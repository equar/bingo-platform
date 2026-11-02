package com.bakaroo.bingo.backend.feature.community.service;

import static com.bakaroo.bingo.backend.feature.community.dto.CommunityDtos.*;

import com.bakaroo.bingo.backend.common.exception.*;
import com.bakaroo.bingo.backend.common.idempotency.*;
import com.bakaroo.bingo.backend.common.request.RequestHashService;
import com.bakaroo.bingo.backend.feature.audit.service.AuditLogService;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.community.repository.CommunityRepository;
import java.text.Normalizer;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerProgressService {
  private final CommunityRepository repo;
  private final CommunityClock time;
  private final IdempotencyService idempotency;
  private final RequestHashService hashes;
  private final AuditLogService audit;

  public PlayerProgressService(
      CommunityRepository repo,
      CommunityClock time,
      IdempotencyService idempotency,
      RequestHashService hashes,
      AuditLogService audit) {
    this.repo = repo;
    this.time = time;
    this.idempotency = idempotency;
    this.hashes = hashes;
    this.audit = audit;
  }

  public record Rule(String code, String metric, int target, int points) {}

  public static final List<Rule> RULES =
      List.of(
          new Rule("FINISH_GAME", "COMPLETED_PUBLIC_SESSIONS", 1, 20),
          new Rule("EXPLORE_ROOMS", "DISTINCT_PUBLIC_ROOMS", 2, 30),
          new Rule("VERIFIED_BINGO", "APPROVED_PUBLIC_WINS", 1, 50));

  private List<Challenge> challenges(Long player, LocalDate day) {
    var progress = repo.progress(player, time.dayStart(day), time.dayStart(day.plusDays(1)));
    var claimed = repo.claimed(player, day);
    return RULES.stream()
        .map(
            rule -> {
              int value =
                  switch (rule.metric()) {
                    case "COMPLETED_PUBLIC_SESSIONS" -> progress.sessions();
                    case "DISTINCT_PUBLIC_ROOMS" -> progress.rooms();
                    case "APPROVED_PUBLIC_WINS" -> progress.wins();
                    default -> throw new IllegalStateException("Unknown challenge metric");
                  };
              return new Challenge(
                  rule.code(),
                  rule.metric(),
                  rule.target(),
                  Math.min(value, rule.target()),
                  rule.points(),
                  value >= rule.target(),
                  claimed.containsKey(rule.code()),
                  1);
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public DailyChallenges daily(BingoUserPrincipal principal) {
    Long player = PrivateGameService.player(principal).getId();
    LocalDate today = time.today();
    return new DailyChallenges(
        today,
        time.timezone(),
        time.dayStart(today.plusDays(1)),
        Math.toIntExact(repo.totalPoints(player)),
        challenges(player, today));
  }

  @Transactional
  public IdempotentResult<ChallengeClaim> claim(
      String code, ChallengeClaimRequest request, BingoUserPrincipal principal, String key) {
    var actor = PrivateGameService.player(principal);
    if (RULES.stream().noneMatch(r -> r.code().equals(code)))
      throw new NotFoundException("CHALLENGE_NOT_FOUND", "Challenge not found");
    return idempotency.execute(
        actor.getId(),
        key,
        hashes.hash("POST", "/api/v1/challenges/" + code + "/claim", request),
        UUID.randomUUID().toString(),
        200,
        ChallengeClaim.class,
        () -> {
          repo.lockPlayer(actor.getId());
          var claimed = repo.claimed(actor.getId(), request.businessDate());
          if (claimed.containsKey(code))
            return new ChallengeClaim(code, request.businessDate(), claimed.get(code), true);
          if (!request.businessDate().equals(time.today()))
            throw new ConflictException(
                "CHALLENGE_EXPIRED", "This challenge day has ended; refresh today's challenges");
          var challenge =
              challenges(actor.getId(), request.businessDate()).stream()
                  .filter(c -> c.code().equals(code))
                  .findFirst()
                  .orElseThrow();
          if (!challenge.completed())
            throw new ConflictException(
                "CHALLENGE_INCOMPLETE", "Complete the challenge before claiming its points");
          repo.claim(actor.getId(), request.businessDate(), code, challenge.points(), time.now());
          audit.record(
              actor,
              "DAILY_CHALLENGE_CLAIMED",
              "PLAYER",
              actor.getId(),
              null,
              null,
              Map.of(
                  "challenge",
                  code,
                  "businessDate",
                  request.businessDate().toString(),
                  "points",
                  challenge.points(),
                  "ruleVersion",
                  1));
          return new ChallengeClaim(code, request.businessDate(), challenge.points(), true);
        });
  }

  @Transactional
  public LeaderboardProfile profile(BingoUserPrincipal principal, LeaderboardProfile request) {
    var actor = PrivateGameService.player(principal);
    repo.lockPlayer(actor.getId());
    String alias =
        request.alias() == null
            ? repo.profile(actor.getId()).alias()
            : Normalizer.normalize(request.alias().trim(), Normalizer.Form.NFC);
    if (alias == null) alias = "Player-" + UUID.randomUUID().toString().substring(0, 8);
    if (alias.length() < 3
        || alias.length() > 24
        || !alias.matches("[\\p{L}\\p{N} _-]+")
        || (request.alias() != null && alias.matches(".*[0-9]{6,}.*")))
      throw new BadRequestException(
          "INVALID_PUBLIC_ALIAS",
          "Use 3–24 letters, spaces, short numbers, dashes or underscores; do not use a phone"
              + " number");
    var profile = new LeaderboardProfile(alias, request.visible());
    try {
      repo.saveProfile(actor.getId(), profile, time.now());
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException("ALIAS_TAKEN", "Choose another public alias");
    }
    audit.record(
        actor,
        "LEADERBOARD_VISIBILITY_CHANGED",
        "PLAYER",
        actor.getId(),
        null,
        null,
        Map.of("visible", profile.visible()));
    return profile;
  }

  @Transactional(readOnly = true)
  public Leaderboard leaderboard(
      BingoUserPrincipal principal, LeaderboardPeriod period, int page, int size) {
    Long player = PrivateGameService.player(principal).getId();
    LocalDate until = time.today().plusDays(1);
    LocalDate from =
        switch (period) {
          case DAILY -> time.today();
          case WEEKLY -> time.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
          case ALL_TIME -> LocalDate.of(1970, 1, 1);
        };
    LocalDate end = period == LeaderboardPeriod.WEEKLY ? from.plusWeeks(1) : until;
    return new Leaderboard(
        period,
        time.timezone(),
        period == LeaderboardPeriod.ALL_TIME ? null : from,
        period == LeaderboardPeriod.ALL_TIME ? null : end,
        repo.rankedCount(from, end),
        page,
        size,
        repo.ranks(from, end, player, page, size),
        repo.rank(from, end, player),
        repo.periodPoints(player, from, end),
        repo.profile(player));
  }

  @Transactional(readOnly = true)
  public CommunitySummary summary() {
    return repo.summary(time.today(), time.timezone());
  }
}
