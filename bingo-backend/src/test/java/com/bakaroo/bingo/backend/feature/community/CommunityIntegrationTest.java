package com.bakaroo.bingo.backend.feature.community;

import static com.bakaroo.bingo.backend.feature.community.dto.CommunityDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.bakaroo.bingo.backend.MySqlIntegrationTest;
import com.bakaroo.bingo.backend.feature.auth.service.*;
import com.bakaroo.bingo.backend.feature.community.service.*;
import com.bakaroo.bingo.backend.feature.game.dto.*;
import com.bakaroo.bingo.backend.feature.game.entity.*;
import com.bakaroo.bingo.backend.feature.game.repository.*;
import com.bakaroo.bingo.backend.feature.game.service.*;
import com.bakaroo.bingo.backend.feature.user.entity.*;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommunityIntegrationTest extends MySqlIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserRepository users;
  @Autowired BingoGameService games;
  @Autowired PrivateGameService privateGames;
  @Autowired PlayerProgressService progress;
  @Autowired GameAccessService access;
  @Autowired CommunityClock time;
  @Autowired JwtService jwt;
  @Autowired JdbcTemplate jdbc;
  private final List<Long> actors = new ArrayList<>();
  private User host, member, outsider, admin;
  private Long template;

  @BeforeEach
  void setup() {
    host = user(UserRole.PLAYER);
    member = user(UserRole.PLAYER);
    outsider = user(UserRole.PLAYER);
    admin = user(UserRole.ADMIN);
    template =
        games
            .createRoom(
                new BingoRoomRequest(
                    "Community template " + UUID.randomUUID(),
                    "Approved rules",
                    new BigDecimal("10.00"),
                    "ETB",
                    "Africa/Addis_Ababa",
                    "MANUAL",
                    2,
                    1,
                    true,
                    false,
                    true,
                    List.of(BingoPatternType.LINE),
                    List.of(
                        new BingoPrizeConfigRequest(
                            BingoPatternType.LINE,
                            new BigDecimal("20.00"),
                            PrizeSettlementPath.PLAYER_WALLET,
                            1)),
                    null))
            .id();
  }

  @AfterEach
  void clearCommunityReferences() {
    // Only remove community rows belonging to this test's actors. Other test fixtures remain
    // untouched.
    for (Long actor : actors) {
      jdbc.update(
          "delete m from private_game_members m join private_games p on p.room_id=m.room_id where"
              + " p.host_id=?",
          actor);
      jdbc.update("delete from private_game_members where player_id=?", actor);
      jdbc.update("delete from private_games where host_id=?", actor);
      jdbc.update("delete from player_challenge_claims where player_id=?", actor);
      jdbc.update("delete from leaderboard_profiles where player_id=?", actor);
    }
  }

  private User user(UserRole role) {
    var user =
        users.save(
            User.create(
                "2519"
                    + String.format(
                        "%08d", ThreadLocalRandom.current().nextInt(10000000, 99999999)),
                "Test private name",
                "hash",
                AccountStatus.ACTIVE,
                Set.of(role),
                Instant.now()));
    actors.add(user.getId());
    return user;
  }

  private String bearer(User user) {
    return "Bearer " + jwt.generateToken(user);
  }

  private JsonNode postJson(String url, User user, Object body, int expected) throws Exception {
    return json.readTree(
        mvc.perform(
                post(url)
                    .header("Authorization", bearer(user))
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(body)))
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private Long createPrivate() {
    return privateGames
        .create(
            new BingoUserPrincipal(host),
            new CreatePrivateGame(template, "Friends", Instant.now().plusSeconds(600)),
            UUID.randomUUID().toString())
        .body()
        .game()
        .roomId();
  }

  private Long privateSession(Long room) {
    return jdbc.queryForObject(
        "select session_id from private_games where room_id=?", Long.class, room);
  }

  private void ticket(Long session, User player) {
    jdbc.update(
        "insert into"
            + " bingo_tickets(session_id,owner_id,public_ticket_code,operation_id,grid_json,purchased_at)"
            + " values(?,?,?,?,?,?)",
        session,
        player.getId(),
        UUID.randomUUID().toString().replace("-", "").substring(0, 20),
        UUID.randomUUID().toString(),
        "[]",
        Timestamp.from(time.now().minusSeconds(300)));
  }

  private Long completedPublic(User player, Long room) {
    var session =
        games.createSession(
            room,
            new GameSessionRequest(
                UUID.randomUUID().toString(),
                time.now().minusSeconds(ThreadLocalRandom.current().nextInt(1000, 10000)),
                GameSessionState.OPEN,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    ticket(session.id(), player);
    jdbc.update(
        "update bingo_sessions set state='CLOSED',started_at=?,ended_at=? where id=?",
        Timestamp.from(time.now().minusSeconds(200)),
        Timestamp.from(time.now().minusSeconds(30)),
        session.id());
    return session.id();
  }

  @Test
  void privateInvitesAreHashedRotatableBoundedAndPrivateAcrossAllReadPaths() throws Exception {
    Long room = createPrivate();
    Long session = privateSession(room);
    var first = privateGames.rotate(room, new BingoUserPrincipal(host));
    assertThat(
            jdbc.queryForObject(
                "select invite_hash from private_games where room_id=?", String.class, room))
        .hasSize(64)
        .isNotEqualTo(first.code());
    assertThat(
            jdbc.queryForObject(
                "select response_body from idempotency_records where actor_id=? limit 1",
                String.class,
                host.getId()))
        .doesNotContain(first.code());
    var second = privateGames.rotate(room, new BingoUserPrincipal(host));
    postJson("/api/v1/private-games/join", member, new JoinPrivateGame(first.code()), 404);
    postJson("/api/v1/private-games/join", member, new JoinPrivateGame(second.code()), 200);
    postJson("/api/v1/private-games/join", outsider, new JoinPrivateGame(second.code()), 409);
    for (String path :
        List.of(
            "/api/v1/rooms/" + room,
            "/api/v1/rooms/" + room + "/sessions",
            "/api/v1/sessions/" + session,
            "/api/v1/sessions/" + session + "/draws")) {
      mvc.perform(get(path).header("Authorization", bearer(outsider)))
          .andExpect(status().isNotFound());
      mvc.perform(get(path).header("Authorization", bearer(member))).andExpect(status().isOk());
    }
    assertThatThrownBy(() -> access.requireSessionAccess(session, new BingoUserPrincipal(outsider)))
        .hasMessageContaining("Room not found");
    assertThatCode(() -> access.requireSessionAccess(session, new BingoUserPrincipal(member)))
        .doesNotThrowAnyException();
    mvc.perform(
            post("/api/v1/private-games/" + room + "/invite")
                .header("Authorization", bearer(member)))
        .andExpect(status().isForbidden());
    String rooms =
        mvc.perform(get("/api/v1/rooms").header("Authorization", bearer(host)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(json.readTree(rooms).findValues("id").stream().map(JsonNode::asLong))
        .doesNotContain(room);
  }

  @Test
  void hostCannotCancelFundedGamesOrStartWithoutTwoTicketedMembers() throws Exception {
    Long room = createPrivate();
    Long session = privateSession(room);
    postJson("/api/v1/private-games/" + room + "/start", host, Map.of(), 409);
    ticket(session, host);
    postJson("/api/v1/private-games/" + room + "/cancel", host, Map.of(), 409);
    assertThatThrownBy(
            () -> games.issueTicketForAgent(session, outsider, UUID.randomUUID().toString()))
        .hasMessageContaining("Room not found");
    var code = privateGames.rotate(room, new BingoUserPrincipal(host));
    privateGames.join(
        new BingoUserPrincipal(member),
        new JoinPrivateGame(code.code()),
        UUID.randomUUID().toString());
    ticket(session, member);
    postJson("/api/v1/private-games/" + room + "/start", host, Map.of(), 200);
    assertThat(
            jdbc.queryForObject(
                "select state from bingo_sessions where id=?", String.class, session))
        .isEqualTo("STARTED");
    assertThat(
            jdbc.queryForObject(
                "select ticket_price_snapshot from bingo_sessions where id=?",
                BigDecimal.class,
                session))
        .isEqualByComparingTo("10.00");
    mvc.perform(
            put("/api/v1/admin/sessions/" + session + "/state")
                .header("Authorization", bearer(host))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"state\":\"CLOSED\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void expiredInvitesAndRolesAreRejected() throws Exception {
    Long room = createPrivate();
    var invite = privateGames.rotate(room, new BingoUserPrincipal(host));
    jdbc.update(
        "update private_games set invite_expires_at=? where room_id=?",
        Timestamp.from(time.now().minusSeconds(1)),
        room);
    postJson("/api/v1/private-games/join", member, new JoinPrivateGame(invite.code()), 404);
    mvc.perform(get("/api/v1/challenges/daily")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/challenges/daily").header("Authorization", bearer(admin)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/community/summary").header("Authorization", bearer(host)))
        .andExpect(status().isForbidden());
  }

  @Test
  void challengeProgressExcludesPrivateAndCancelledGamesAndNeverMultipliesTickets() {
    Long room = createPrivate();
    Long privateSession = privateSession(room);
    ticket(privateSession, host);
    jdbc.update(
        "update bingo_sessions set state='CLOSED',started_at=?,ended_at=? where id=?",
        Timestamp.from(time.now().minusSeconds(100)),
        Timestamp.from(time.now().minusSeconds(10)),
        privateSession);
    assertThat(progress.daily(new BingoUserPrincipal(host)).challenges().get(0).progress())
        .isZero();
    Long publicSession = completedPublic(host, template);
    ticket(publicSession, host);
    var daily = progress.daily(new BingoUserPrincipal(host));
    assertThat(daily.challenges().get(0).progress()).isEqualTo(1);
    assertThat(daily.challenges().get(1).progress()).isEqualTo(1);
    jdbc.update("update bingo_sessions set state='CANCELLED' where id=?", publicSession);
    assertThat(progress.daily(new BingoUserPrincipal(host)).challenges().get(0).progress())
        .isZero();
  }

  @Test
  void concurrentChallengeClaimsAwardExactlyOnceWithoutAnyWalletWrites() throws Exception {
    completedPublic(host, template);
    long transactions = jdbc.queryForObject("select count(*) from wallet_transactions", Long.class);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (int n = 0; n < 2; n++)
        results.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  return progress
                      .claim(
                          "FINISH_GAME",
                          new ChallengeClaimRequest(time.today()),
                          new BingoUserPrincipal(host),
                          UUID.randomUUID().toString())
                      .body()
                      .points();
                }));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      for (var result : results) assertThat(result.get(20, TimeUnit.SECONDS)).isEqualTo(20);
    } finally {
      pool.shutdownNow();
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from player_challenge_claims where player_id=?",
                Long.class,
                host.getId()))
        .isEqualTo(1);
    assertThat(progress.daily(new BingoUserPrincipal(host)).totalPoints()).isEqualTo(20);
    assertThat(jdbc.queryForObject("select count(*) from wallet_transactions", Long.class))
        .isEqualTo(transactions);
  }

  @Test
  void incompleteAndExpiredClaimsCannotAwardPoints() throws Exception {
    postJson(
        "/api/v1/challenges/FINISH_GAME/claim", host, new ChallengeClaimRequest(time.today()), 409);
    postJson(
        "/api/v1/challenges/FINISH_GAME/claim",
        host,
        new ChallengeClaimRequest(time.today().minusDays(1)),
        409);
    assertThat(progress.daily(new BingoUserPrincipal(host)).totalPoints()).isZero();
  }

  @Test
  void communityEndpointsArePresentInTheGeneratedContract() throws Exception {
    String spec =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode parsed = json.readTree(spec);
    assertThat(parsed.path("paths").has("/api/v1/private-games")).isTrue();
    assertThat(parsed.path("paths").has("/api/v1/challenges/daily")).isTrue();
    assertThat(parsed.path("paths").has("/api/v1/leaderboard")).isTrue();
    if (Boolean.getBoolean("bingo.exportOpenApi")) {
      java.nio.file.Files.writeString(
          java.nio.file.Path.of("openapi.json"),
          json.writerWithDefaultPrettyPrinter().writeValueAsString(parsed) + "\n");
    }
  }

  @Test
  void rankingsAreOptInTieAwareAndDoNotRevealPlayerIdentifiers() {
    for (User actor : List.of(host, member, outsider)) {
      jdbc.update(
          "insert into"
              + " player_challenge_claims(player_id,business_date,challenge_code,rule_version,points,claimed_at)"
              + " values(?,?,?,1,20,?)",
          actor.getId(),
          time.today(),
          "FINISH_GAME",
          Timestamp.from(time.now()));
    }
    String suffix = UUID.randomUUID().toString().substring(0, 6);
    progress.profile(new BingoUserPrincipal(host), new LeaderboardProfile("Alpha-" + suffix, true));
    progress.profile(
        new BingoUserPrincipal(member), new LeaderboardProfile("Beta-" + suffix, true));
    var board = progress.leaderboard(new BingoUserPrincipal(host), LeaderboardPeriod.WEEKLY, 0, 25);
    assertThat(board.entries().stream().filter(e -> e.alias().endsWith(suffix)).toList())
        .hasSize(2);
    assertThat(
            board.entries().stream()
                .filter(e -> e.alias().endsWith(suffix))
                .map(LeaderboardEntry::rank)
                .distinct()
                .count())
        .isEqualTo(1);
    assertThatThrownBy(
            () ->
                progress.profile(
                    new BingoUserPrincipal(outsider),
                    new LeaderboardProfile("Alpha-" + suffix, true)))
        .hasMessageContaining("another public alias");
    progress.profile(
        new BingoUserPrincipal(host), new LeaderboardProfile("Alpha-" + suffix, false));
    assertThat(
            progress
                .leaderboard(new BingoUserPrincipal(host), LeaderboardPeriod.WEEKLY, 0, 25)
                .myRank())
        .isNull();
    assertThat(progress.daily(new BingoUserPrincipal(host)).totalPoints()).isEqualTo(20);
  }
}
