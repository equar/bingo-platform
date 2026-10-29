package com.bakaroo.bingo.backend.feature.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bakaroo.bingo.backend.MySqlIntegrationTest;
import com.bakaroo.bingo.backend.common.exception.ConflictException;
import com.bakaroo.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.bakaroo.bingo.backend.feature.auth.service.JwtService;
import com.bakaroo.bingo.backend.feature.game.dto.BingoPrizeConfigRequest;
import com.bakaroo.bingo.backend.feature.game.dto.BingoRoomRequest;
import com.bakaroo.bingo.backend.feature.game.dto.DrawEventResponse;
import com.bakaroo.bingo.backend.feature.game.dto.GameSessionRequest;
import com.bakaroo.bingo.backend.feature.game.dto.GameSessionStateUpdateRequest;
import com.bakaroo.bingo.backend.feature.game.entity.BingoPatternType;
import com.bakaroo.bingo.backend.feature.game.entity.DrawEvent;
import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;
import com.bakaroo.bingo.backend.feature.game.entity.PrizeSettlementPath;
import com.bakaroo.bingo.backend.feature.game.repository.DrawEventRepository;
import com.bakaroo.bingo.backend.feature.game.service.BingoGameService;
import com.bakaroo.bingo.backend.feature.game.websocket.GameLiveDrawService;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Append-only integrity tests. This class deliberately has no cleanup hooks and is not safe to run
 * alongside the broad destructive integration suites. Select this class alone with Surefire.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.game.draw-interval=PT10S",
        "app.game.draw-scheduler-enabled=false",
        "app.wallet.signup-reward-test-posting-enabled=false",
        "app.wallet.deposit-simulation-enabled=false",
        "app.wallet.adjustment-simulation-enabled=false",
        "app.agent.float-simulation-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DrawRetrySafetyIntegrationTest extends MySqlIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    EthiopianPhoneNormalizer phoneNormalizer;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtService jwtService;

    @Autowired
    BingoGameService bingoGameService;

    @Autowired
    GameLiveDrawService gameLiveDrawService;

    @Autowired
    DrawEventRepository drawEventRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void identicalBodylessRequestRetriedAfterLostResponseCreatesAnotherDraw() throws Exception {
        Fixture fixture = createStartedFixture("retry");
        waitUntilDue(fixture.startedAt());

        String path = "/api/v1/admin/sessions/" + fixture.sessionId() + "/draws";
        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + fixture.adminToken()))
                .andExpect(status().isOk())
                .andReturn(); // Deliberately discard the response to model the caller losing it.

        // A caller that lost the response has only database/server evidence here; it cannot replay a key.
        assertEquals(1L, drawEventRepository.countBySessionId(fixture.sessionId()));
        assertEquals(1L, drawAuditCount(fixture.sessionId()));

        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + fixture.adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRAW_NOT_AVAILABLE"));
        assertEquals(1L, drawEventRepository.countBySessionId(fixture.sessionId()));

        Thread.sleep(Duration.ofSeconds(10).toMillis() + 150);
        MvcResult retryHttpExchange = mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + fixture.adminToken()))
                .andExpect(status().isOk())
                .andReturn();
        DrawEventResponse retryResponse = objectMapper.readValue(
                retryHttpExchange.getResponse().getContentAsString(), DrawEventResponse.class);

        assertEquals(2, retryResponse.sequenceNo());
        List<DrawEvent> persisted = drawEventRepository.findBySessionIdOrderBySequenceNoAsc(fixture.sessionId());
        assertEquals(2, persisted.size());
        assertEquals(1, persisted.get(0).getSequenceNo());
        assertEquals(2, persisted.get(1).getSequenceNo());
        assertNotEquals(persisted.get(0).getOperationId(), persisted.get(1).getOperationId());
        assertNotEquals(persisted.get(0).getNumberDrawn(), persisted.get(1).getNumberDrawn());
        assertEquals(persisted.get(1).getOperationId(), retryResponse.operationId());
        assertEquals(persisted.get(1).getNumberDrawn(), retryResponse.numberDrawn());
        assertEquals(2L, drawAuditCount(fixture.sessionId()));
        assertAuditMatches(fixture.sessionId(), responseFor(fixture.sessionId(), persisted.get(0)));
        assertAuditMatches(fixture.sessionId(), responseFor(fixture.sessionId(), persisted.get(1)));
        assertTimestampMatches(fixture.sessionId(), retryResponse);

        System.out.printf("M04C4_RETRY run=%s session=%d createdAt=%s draws=2 audits=2%n",
                fixture.runId(), fixture.sessionId(), fixture.createdAt());
    }

    @Test
    void competingAdminOperationsPersistOnlyOneDrawAndAudit() throws Exception {
        Fixture fixture = createStartedFixture("concurrent");
        waitUntilDue(fixture.startedAt());
        Instant sameRequestTime = Instant.now();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<DrawEventResponse> first = CompletableFuture.supplyAsync(
                    () -> triggerAt(fixture, sameRequestTime, ready, start), executor);
            CompletableFuture<DrawEventResponse> second = CompletableFuture.supplyAsync(
                    () -> triggerAt(fixture, sameRequestTime, ready, start), executor);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<DrawEventResponse> successful = java.util.stream.Stream.of(
                            first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            assertEquals(1, successful.size());
            assertEquals(1L, drawEventRepository.countBySessionId(fixture.sessionId()));
            assertEquals(1L, drawAuditCount(fixture.sessionId()));
            assertAuditMatches(fixture.sessionId(), successful.get(0));
            assertTimestampMatches(fixture.sessionId(), successful.get(0));

            System.out.printf("M04C4_CONCURRENT run=%s session=%d createdAt=%s winners=1 draws=1 audits=1%n",
                    fixture.runId(), fixture.sessionId(), fixture.createdAt());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private DrawEventResponse triggerAt(Fixture fixture, Instant now, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start the concurrent draw requests");
            }
            return gameLiveDrawService.triggerAutomaticDraw(fixture.sessionId(), now, fixture.admin());
        } catch (ConflictException expectedCompetingRequest) {
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating concurrent draw requests", exception);
        }
    }

    private Fixture createStartedFixture(String caseName) {
        String runId = "m04c4-" + caseName + "-" + Instant.now().toEpochMilli() + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        String phone = uniqueSyntheticPhone();
        Instant createdAt = Instant.now();
        User admin = userRepository.save(User.create(
                phoneNormalizer.normalize(phone),
                "QA " + runId,
                passwordEncoder.encode("246810"),
                AccountStatus.ACTIVE,
                Set.of(UserRole.ADMIN),
                createdAt));

        var room = bingoGameService.createRoom(new BingoRoomRequest(
                "QA " + runId,
                "Append-only Module 04C-4 draw integrity fixture",
                new BigDecimal("1.00"),
                "ETB",
                "Africa/Addis_Ababa",
                "0 0/30 * * * *",
                10,
                2,
                true,
                true,
                true,
                List.of(BingoPatternType.LINE),
                List.of(new BingoPrizeConfigRequest(
                        BingoPatternType.LINE,
                        new BigDecimal("1.00"),
                        PrizeSettlementPath.PLAYER_WALLET,
                        1)),
                null));
        var session = bingoGameService.createSession(room.id(), new GameSessionRequest(
                "QA " + runId,
                Instant.now().plusSeconds(60),
                GameSessionState.DRAFT,
                new BigDecimal("1.00"),
                "ETB",
                10,
                2,
                true,
                true,
                List.of(BingoPatternType.LINE),
                List.of(new BingoPrizeConfigRequest(
                        BingoPatternType.LINE,
                        new BigDecimal("1.00"),
                        PrizeSettlementPath.PLAYER_WALLET,
                        1))));
        bingoGameService.updateSessionState(session.id(), new GameSessionStateUpdateRequest(GameSessionState.OPEN));
        var started = bingoGameService.updateSessionState(
                session.id(), new GameSessionStateUpdateRequest(GameSessionState.STARTED));
        assertNotNull(started.startedAt());
        return new Fixture(runId, session.id(), session.createdAt(), started.startedAt(), admin,
                jwtService.generateToken(admin));
    }

    private String uniqueSyntheticPhone() {
        String phone;
        do {
            long suffix = Math.floorMod(System.nanoTime(), 100_000_000L);
            phone = "09" + String.format("%08d", suffix);
        } while (userRepository.findByNormalizedPhone(phoneNormalizer.normalize(phone)).isPresent());
        return phone;
    }

    private void waitUntilDue(Instant startedAt) throws InterruptedException {
        long waitMillis = Duration.between(Instant.now(), startedAt.plusSeconds(10).plusMillis(100)).toMillis();
        if (waitMillis > 0) {
            Thread.sleep(waitMillis);
        }
    }

    private long drawAuditCount(Long sessionId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from audit_log_entries where action = 'BINGO_NUMBER_DRAWN' "
                        + "and target_type = 'GAME_SESSION' and target_id = ?",
                Long.class,
                sessionId);
    }

    private void assertAuditMatches(Long sessionId, DrawEventResponse response) throws Exception {
        List<String> matches = jdbcTemplate.query(
                "select operation_id from audit_log_entries where action = 'BINGO_NUMBER_DRAWN' "
                        + "and target_type = 'GAME_SESSION' and target_id = ?",
                (result, rowNumber) -> result.getString(1),
                sessionId);
        assertTrue(matches.contains(response.operationId()));
        String metadata = jdbcTemplate.queryForObject(
                "select metadata_json from audit_log_entries where operation_id = ?",
                String.class,
                response.operationId());
        JsonNode json = objectMapper.readTree(metadata);
        assertEquals(response.sequenceNo(), json.path("sequenceNo").asInt());
        assertEquals(response.numberDrawn(), json.path("numberDrawn").asInt());
    }

    private void assertTimestampMatches(Long sessionId, DrawEventResponse response) {
        DrawEvent persisted = drawEventRepository.findBySessionIdOrderBySequenceNoAsc(sessionId).stream()
                .filter(draw -> draw.getSequenceNo() == response.sequenceNo())
                .findFirst()
                .orElseThrow();
        assertEquals(response.drawnAt(), persisted.getDrawnAt());
        assertTrue(persisted.getNumberDrawn() >= 1 && persisted.getNumberDrawn() <= 75);
        assertFalse(persisted.getOperationId().isBlank());
    }

    private DrawEventResponse responseFor(Long sessionId, DrawEvent draw) {
        return new DrawEventResponse(draw.getId(), sessionId, draw.getSequenceNo(), draw.getNumberDrawn(),
                draw.getOperationId(), draw.getDrawnAt());
    }

    private record Fixture(String runId, Long sessionId, Instant createdAt, Instant startedAt, User admin,
            String adminToken) {
    }
}
