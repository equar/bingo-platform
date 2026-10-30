package com.bakaroo.bingo.backend.feature.community.service;

import static com.bakaroo.bingo.backend.feature.community.dto.CommunityDtos.*;

import com.bakaroo.bingo.backend.common.exception.*;
import com.bakaroo.bingo.backend.common.idempotency.*;
import com.bakaroo.bingo.backend.common.request.RequestHashService;
import com.bakaroo.bingo.backend.feature.audit.service.AuditLogService;
import com.bakaroo.bingo.backend.feature.auth.service.*;
import com.bakaroo.bingo.backend.feature.community.repository.CommunityRepository;
import com.bakaroo.bingo.backend.feature.game.dto.*;
import com.bakaroo.bingo.backend.feature.game.entity.*;
import com.bakaroo.bingo.backend.feature.game.repository.*;
import com.bakaroo.bingo.backend.feature.game.service.BingoGameService;
import com.bakaroo.bingo.backend.feature.user.entity.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrivateGameService {
  private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private final SecureRandom random = new SecureRandom();
  private final CommunityRepository repo;
  private final BingoRoomRepository rooms;
  private final GameSessionRepository sessions;
  private final BingoGameService games;
  private final CommunityClock time;
  private final IdempotencyService idempotency;
  private final RequestHashService hashes;
  private final AuthenticationAttemptThrottleService throttle;
  private final AuditLogService audit;

  public PrivateGameService(
      CommunityRepository repo,
      BingoRoomRepository rooms,
      GameSessionRepository sessions,
      BingoGameService games,
      CommunityClock time,
      IdempotencyService idempotency,
      RequestHashService hashes,
      AuthenticationAttemptThrottleService throttle,
      AuditLogService audit) {
    this.repo = repo;
    this.rooms = rooms;
    this.sessions = sessions;
    this.games = games;
    this.time = time;
    this.idempotency = idempotency;
    this.hashes = hashes;
    this.throttle = throttle;
    this.audit = audit;
  }

  static User player(BingoUserPrincipal p) {
    if (p == null || !p.isEnabled() || !p.getUser().getRoles().contains(UserRole.PLAYER))
      throw new AccessDeniedException("An active player account is required");
    return p.getUser();
  }

  @Transactional
  public IdempotentResult<PrivateGameCreated> create(
      BingoUserPrincipal principal, CreatePrivateGame request, String key) {
    User actor = player(principal);
    return idempotency.execute(
        actor.getId(),
        key,
        hashes.hash("POST", "/api/v1/private-games", request),
        UUID.randomUUID().toString(),
        201,
        PrivateGameCreated.class,
        () -> {
          repo.lockPlayer(actor.getId());
          if (repo.openPrivateCount(actor.getId()) >= 5)
            throw new ConflictException(
                "PRIVATE_GAME_LIMIT", "You can host up to five unfinished private games");
          if (request.scheduledStartAt().isBefore(time.now())
              || request.scheduledStartAt().isAfter(time.now().plus(Duration.ofDays(7))))
            throw new BadRequestException(
                "INVALID_PRIVATE_SCHEDULE", "Choose a start time within the next seven days");
          BingoRoom template =
              rooms
                  .findByIdAndActiveTrue(request.templateRoomId())
                  .filter(r -> !r.isPrivateRoom())
                  .orElseThrow(
                      () ->
                          new NotFoundException(
                              "ROOM_NOT_FOUND", "Public room template not found"));
          BingoRoom room = new BingoRoom();
          room.setPrivateRoom(true);
          room.setName(request.name().trim());
          room.setDescription("Invite-only Bingo game");
          room.setTicketPrice(template.getTicketPrice());
          room.setCurrency(template.getCurrency());
          room.setTimezone(template.getTimezone());
          room.setScheduleRule("PRIVATE_HOST_START");
          room.setSessionCapacity(Math.min(50, template.getSessionCapacity()));
          room.setMaxTicketsPerPlayer(template.getMaxTicketsPerPlayer());
          room.setTicketSalesCloseOnSessionStart(true);
          room.setAllowMultipleWinnersPerPrize(template.isAllowMultipleWinnersPerPrize());
          room.setConfiguredPatternsJson(template.getConfiguredPatternsJson());
          room.setConfiguredPrizesJson(template.getConfiguredPrizesJson());
          room.setActive(true);
          room = rooms.saveAndFlush(room);
          var session =
              games.createSession(
                  room.getId(),
                  new GameSessionRequest(
                      request.name().trim(),
                      request.scheduledStartAt(),
                      GameSessionState.OPEN,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null));
          String code = code();
          Instant expiry = time.now().plus(Duration.ofHours(24));
          repo.createPrivate(
              room.getId(), session.id(), actor.getId(), hash(code), expiry, time.now());
          audit.record(
              actor,
              "PRIVATE_GAME_CREATED",
              "BINGO_ROOM",
              room.getId(),
              null,
              null,
              Map.of("templateRoomId", template.getId()));
          return new PrivateGameCreated(view(room.getId(), actor.getId()));
        });
  }

  @Transactional(readOnly = true)
  public List<PrivateGame> mine(BingoUserPrincipal p) {
    Long id = player(p).getId();
    return repo.myRooms(id).stream().map(r -> view(r, id)).toList();
  }

  @Transactional(readOnly = true)
  public PrivateGameDetails details(Long room, BingoUserPrincipal p) {
    Long id = player(p).getId();
    if (!repo.member(room, id)) throw missing();
    var row = repo.privateGame(room, false).orElseThrow(PrivateGameService::missing);
    return new PrivateGameDetails(
        view(room, id), games.getPlayerRoom(room), games.getPlayerSession(row.sessionId()));
  }

  @Transactional
  public IdempotentResult<PrivateGame> join(
      BingoUserPrincipal p, JoinPrivateGame request, String key) {
    User actor = player(p);
    // Persist throttling before idempotency acquires the actor row lock.
    throttle.requireAllowed(
        "private-invite:" + actor.getId(),
        20,
        Duration.ofMinutes(10),
        "INVITE_RATE_LIMITED",
        "Too many invite attempts; try again later");
    return idempotency.execute(
        actor.getId(),
        key,
        hashes.hash("POST", "/api/v1/private-games/join", request),
        UUID.randomUUID().toString(),
        200,
        PrivateGame.class,
        () -> {
          String digest = hash(normalize(request.code()));
          Long roomId = repo.invitedRoom(digest).orElseThrow(PrivateGameService::missing);
          var row = repo.privateGame(roomId, true).orElseThrow(PrivateGameService::missing);
          // Recheck after locking so rotation and join serialize on the invite row.
          if (!MessageDigest.isEqual(
              digest.getBytes(StandardCharsets.US_ASCII),
              repo.inviteHash(roomId).getBytes(StandardCharsets.US_ASCII))) throw missing();
          var session =
              sessions.findByIdForUpdate(row.sessionId()).orElseThrow(PrivateGameService::missing);
          if (!session.getRoom().isActive()
              || !row.expiresAt().isAfter(time.now())
              || session.getState() != GameSessionState.OPEN) throw missing();
          if (!repo.member(roomId, actor.getId())) {
            if (repo.members(roomId) >= session.getSessionCapacitySnapshot())
              throw new ConflictException("PRIVATE_GAME_FULL", "This private game is full");
            repo.addMember(roomId, actor.getId(), time.now());
            audit.record(actor, "PRIVATE_GAME_JOINED", "BINGO_ROOM", roomId, null, null, Map.of());
          }
          return view(roomId, actor.getId());
        });
  }

  @Transactional
  public Invite rotate(Long room, BingoUserPrincipal p) {
    User actor = player(p);
    var row = host(room, actor);
    var session =
        sessions.findByIdForUpdate(row.sessionId()).orElseThrow(PrivateGameService::missing);
    if (!session.getRoom().isActive() || session.getState() != GameSessionState.OPEN)
      throw new ConflictException(
          "PRIVATE_GAME_NOT_OPEN", "Invites are only available before a game starts");
    String code = code();
    Instant expiry = time.now().plus(Duration.ofHours(24));
    repo.rotate(room, hash(code), expiry);
    audit.record(actor, "PRIVATE_INVITE_ROTATED", "BINGO_ROOM", room, null, null, Map.of());
    return new Invite(code, expiry);
  }

  @Transactional
  public PrivateGame start(Long room, BingoUserPrincipal p) {
    User actor = player(p);
    var row = host(room, actor);
    var session =
        sessions.findByIdForUpdate(row.sessionId()).orElseThrow(PrivateGameService::missing);
    if (session.getState() == GameSessionState.STARTED
        || session.getState() == GameSessionState.DRAWING) return view(room, actor.getId());
    if (!session.getRoom().isActive())
      throw new ConflictException("ROOM_INACTIVE", "This private game is disabled");
    if (repo.ticketedPlayers(row.sessionId()) < 2)
      throw new ConflictException(
          "PRIVATE_GAME_NEEDS_PLAYERS", "At least two members must have tickets before starting");
    games.updateSessionState(
        row.sessionId(), new GameSessionStateUpdateRequest(GameSessionState.STARTED));
    audit.record(
        actor,
        "PRIVATE_GAME_STARTED",
        "BINGO_ROOM",
        room,
        null,
        null,
        Map.of("sessionId", row.sessionId()));
    return view(room, actor.getId());
  }

  @Transactional
  public PrivateGame cancel(Long room, BingoUserPrincipal p) {
    User actor = player(p);
    var row = host(room, actor);
    var session =
        sessions.findByIdForUpdate(row.sessionId()).orElseThrow(PrivateGameService::missing);
    if (session.getState() == GameSessionState.CANCELLED) return view(room, actor.getId());
    if (session.getState() != GameSessionState.OPEN || repo.ticketCount(row.sessionId()) > 0)
      throw new ConflictException(
          "PRIVATE_GAME_CANNOT_CANCEL",
          "Only unstarted games without tickets can be cancelled; contact support for funded"
              + " games");
    games.updateSessionState(
        row.sessionId(), new GameSessionStateUpdateRequest(GameSessionState.CANCELLED));
    audit.record(actor, "PRIVATE_GAME_CANCELLED", "BINGO_ROOM", room, null, null, Map.of());
    return view(room, actor.getId());
  }

  private CommunityRepository.PrivateRow host(Long room, User actor) {
    var row = repo.privateGame(room, true).orElseThrow(PrivateGameService::missing);
    if (!row.hostId().equals(actor.getId()))
      throw new AccessDeniedException("Only the host can manage this private game");
    return row;
  }

  private PrivateGame view(Long roomId, Long viewer) {
    var row = repo.privateGame(roomId, false).orElseThrow(PrivateGameService::missing);
    var s = sessions.findById(row.sessionId()).orElseThrow(PrivateGameService::missing);
    return new PrivateGame(
        roomId,
        s.getId(),
        s.getRoom().getName(),
        s.getState().name(),
        row.hostId().equals(viewer),
        s.getRoom().isActive(),
        repo.members(roomId),
        s.getSessionCapacitySnapshot(),
        row.expiresAt(),
        s.getScheduledStartAt());
  }

  @Transactional(readOnly = true)
  public List<PrivateGame> adminGames() {
    return repo.adminRooms().stream().map(id -> view(id, -1L)).toList();
  }

  @Transactional
  public void disable(Long id, BingoUserPrincipal principal, Moderation request) {
    if (principal == null || !principal.getUser().getRoles().contains(UserRole.ADMIN))
      throw new AccessDeniedException("Administrator required");
    repo.privateGame(id, true).orElseThrow(PrivateGameService::missing);
    var room = rooms.findById(id).orElseThrow(PrivateGameService::missing);
    room.setActive(false);
    audit.record(
        principal.getUser(),
        "PRIVATE_GAME_DISABLED",
        "BINGO_ROOM",
        id,
        request.reason(),
        null,
        Map.of());
  }

  static NotFoundException missing() {
    return new NotFoundException("PRIVATE_GAME_NOT_FOUND", "Private game or invitation not found");
  }

  static String normalize(String code) {
    return code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
  }

  static String hash(String code) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private String code() {
    StringBuilder s = new StringBuilder();
    for (int i = 0; i < 14; i++) s.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    return s.toString();
  }
}
