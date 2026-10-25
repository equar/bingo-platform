package com.equar.bingo.backend.feature.game;

import com.equar.bingo.backend.MySqlIntegrationTest;
import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.feature.auth.dto.LoginRequest;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.equar.bingo.backend.feature.auth.service.JwtService;
import com.equar.bingo.backend.feature.game.dto.BingoPrizeConfigRequest;
import com.equar.bingo.backend.feature.game.dto.BingoRoomRequest;
import com.equar.bingo.backend.feature.game.dto.DrawEventResponse;
import com.equar.bingo.backend.feature.game.dto.GameSessionRequest;
import com.equar.bingo.backend.feature.game.dto.GameSessionStateUpdateRequest;
import com.equar.bingo.backend.feature.game.entity.BingoPatternType;
import com.equar.bingo.backend.feature.game.entity.DrawEvent;
import com.equar.bingo.backend.feature.game.entity.GameSession;
import com.equar.bingo.backend.feature.game.entity.GameSessionState;
import com.equar.bingo.backend.feature.game.entity.PrizeSettlementPath;
import com.equar.bingo.backend.feature.game.repository.BingoRoomRepository;
import com.equar.bingo.backend.feature.game.repository.DrawEventRepository;
import com.equar.bingo.backend.feature.game.repository.GameSessionRepository;
import com.equar.bingo.backend.feature.game.repository.PrizeEntitlementRepository;
import com.equar.bingo.backend.feature.game.repository.TicketRepository;
import com.equar.bingo.backend.feature.game.repository.WinClaimRepository;
import com.equar.bingo.backend.feature.game.service.BingoGameService;
import com.equar.bingo.backend.feature.game.websocket.GameLiveDrawService;
import com.equar.bingo.backend.feature.game.websocket.GameLiveDrawScheduler;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.wallet.repository.DepositClaimRepository;
import com.equar.bingo.backend.feature.wallet.repository.IdempotencyRecordRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import com.equar.bingo.backend.feature.wallet.service.WalletAdjustmentService;
import com.equar.bingo.backend.feature.wallet.service.WalletService;
import com.equar.bingo.backend.feature.wallet.dto.WalletAdjustmentReviewRequest;
import com.equar.bingo.backend.feature.wallet.dto.WalletAdjustmentSubmissionRequest;
import com.equar.bingo.backend.feature.agent.repository.PayoutVerificationRecordRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentFloatTransactionRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentFloatRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentRepository;
import com.equar.bingo.backend.feature.support.repository.InternalNoteRepository;
import com.equar.bingo.backend.feature.support.repository.SupportMessageRepository;
import com.equar.bingo.backend.feature.support.repository.SupportTicketRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(BingoLiveDrawIntegrationTest.TestMessagingConfig.class)
class BingoLiveDrawIntegrationTest extends MySqlIntegrationTest {

        @LocalServerPort
        int port;

        @Autowired
        MockMvc mockMvc;

        @Autowired
        PasswordEncoder passwordEncoder;

        @Autowired
        ObjectMapper objectMapper;

        @Autowired
        EthiopianPhoneNormalizer phoneNormalizer;

        @Autowired
        UserRepository userRepository;

        @Autowired
        JwtService jwtService;

        @Autowired
        WalletRepository walletRepository;

        @Autowired
        WalletTransactionRepository walletTransactionRepository;

        @Autowired
        DepositClaimRepository depositClaimRepository;

        @Autowired
        IdempotencyRecordRepository idempotencyRecordRepository;

        @Autowired
        BingoRoomRepository bingoRoomRepository;

        @Autowired
        GameSessionRepository gameSessionRepository;

        @Autowired
        TicketRepository ticketRepository;

        @Autowired
        DrawEventRepository drawEventRepository;

        @Autowired
        WinClaimRepository winClaimRepository;

        @Autowired
        PrizeEntitlementRepository prizeEntitlementRepository;

        @Autowired
        WalletService walletService;

        @Autowired
        WalletAdjustmentService walletAdjustmentService;

        @Autowired
        SupportTicketRepository supportTicketRepository;

        @Autowired
        SupportMessageRepository supportMessageRepository;

        @Autowired
        InternalNoteRepository internalNoteRepository;

        @Autowired
        BingoGameService bingoGameService;

        @Autowired
        GameLiveDrawService gameLiveDrawService;

        @Autowired
        GameLiveDrawScheduler gameLiveDrawScheduler;

        @Autowired
        PlatformTransactionManager transactionManager;

        @Autowired
        RecordingSimpMessagingTemplate recordingSimpMessagingTemplate;

        @Autowired
        com.equar.bingo.backend.feature.game.config.GameProperties gameProperties;
        @Autowired
        PayoutVerificationRecordRepository payoutVerificationRecordRepository;

        @Autowired
        AgentFloatTransactionRepository agentFloatTransactionRepository;

        @Autowired
        AgentFloatRepository agentFloatRepository;

        @Autowired
        AgentRepository agentRepository;

        @Autowired
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

        private User adminUser;
        private User playerUser;
        private User agentUser;
        private User supportUser;
        private User financeMaker;
        private User financeChecker;

        @BeforeEach
        void setUp() {
                jdbcTemplate.update("delete from audit_log_entries");
                recordingSimpMessagingTemplate.getRecordedMessages().clear();
                jdbcTemplate.update("delete from float_accounting_journal_lines");
                jdbcTemplate.update("delete from float_accounting_journals");
                jdbcTemplate.update("delete from float_top_up_verification_attempts");
                jdbcTemplate.update("delete from float_bank_statement_entries");
                jdbcTemplate.update("delete from agent_float_top_up_requests");
                jdbcTemplate.update("delete from float_receiving_bank_accounts");
                internalNoteRepository.deleteAllInBatch();
                supportMessageRepository.deleteAllInBatch();
                supportTicketRepository.deleteAllInBatch();
                payoutVerificationRecordRepository.deleteAll();
                agentFloatTransactionRepository.deleteAll();
                agentFloatRepository.deleteAll();
                agentRepository.deleteAll();
                jdbcTemplate.update("delete from wallet_adjustment_accounting_journal_lines");
                jdbcTemplate.update("delete from wallet_adjustment_accounting_journals");
                jdbcTemplate.update("delete from wallet_adjustment_requests");
                prizeEntitlementRepository.deleteAll();
                winClaimRepository.deleteAll();
                drawEventRepository.deleteAll();
                ticketRepository.deleteAll();
                gameSessionRepository.deleteAll();
                bingoRoomRepository.deleteAll();
                walletTransactionRepository.deleteAll();
                jdbcTemplate.update("delete from signup_reward_grants");
                jdbcTemplate.update("delete from wallet_transfers");
                jdbcTemplate.update("delete from signup_reward_policies");
                jdbcTemplate.update("delete from wallet_transfer_policies");
                depositClaimRepository.deleteAll();
                walletRepository.deleteAll();
                idempotencyRecordRepository.deleteAll();
                userRepository.deleteAll();

                Instant now = Instant.now();
                adminUser = userRepository.save(User.create(
                                phoneNormalizer.normalize("0911000201"),
                                "Admin User",
                                passwordEncoder.encode("123456"),
                                AccountStatus.ACTIVE,
                                Set.of(UserRole.ADMIN),
                                now));
                playerUser = userRepository.save(User.create(
                                phoneNormalizer.normalize("0911000202"),
                                "Player User",
                                passwordEncoder.encode("123456"),
                                AccountStatus.ACTIVE,
                                Set.of(UserRole.PLAYER),
                                now));
                agentUser = userRepository.save(User.create(
                                phoneNormalizer.normalize("0911000205"),
                                "Agent User",
                                passwordEncoder.encode("123456"),
                                AccountStatus.ACTIVE,
                                Set.of(UserRole.AGENT),
                                now));
                supportUser = userRepository.save(User.create(
                                phoneNormalizer.normalize("0911000206"),
                                "Support User",
                                passwordEncoder.encode("123456"),
                                AccountStatus.ACTIVE,
                                Set.of(UserRole.SUPPORT),
                                now));
                financeMaker = userRepository.save(User.create(
                                phoneNormalizer.normalize("0911000203"),
                                "Finance Verifier",
                                passwordEncoder.encode("123456"),
                                AccountStatus.ACTIVE,
                                Set.of(UserRole.ADMIN, UserRole.FINANCE_VERIFIER),
                                now));
                financeChecker = userRepository.save(User.create(
                                phoneNormalizer.normalize("0911000204"),
                                "Finance Approver",
                                passwordEncoder.encode("123456"),
                                AccountStatus.ACTIVE,
                                Set.of(UserRole.ADMIN, UserRole.FINANCE_APPROVER),
                                now));
                walletService.createWalletIfMissing(playerUser);
                var adjustment = walletAdjustmentService.submit(playerUser.getId(),
                                new BingoUserPrincipal(financeMaker),
                                new WalletAdjustmentSubmissionRequest(new BigDecimal("1000.00"), "ETB",
                                                "Approved integration fixture funding", "LIVE-DRAW-PLAYER"));
                walletAdjustmentService.approve(adjustment.id(), new BingoUserPrincipal(financeChecker),
                                new WalletAdjustmentReviewRequest("Independent integration fixture approval"));
        }

        @Test
        void competingWorkersPersistOnlyOneDraw() {
                GameSession session = startedSession("Concurrency Room");
                Instant dueTime = session.getStartedAt().plus(gameProperties.drawInterval()).plusSeconds(1);

                var first = CompletableFuture
                                .supplyAsync(() -> gameLiveDrawService.advanceSessionDrawIfDue(session.getId(),
                                                dueTime));
                var second = CompletableFuture
                                .supplyAsync(() -> gameLiveDrawService.advanceSessionDrawIfDue(session.getId(),
                                                dueTime));

                List<DrawEventResponse> draws = List.of(first.join(), second.join()).stream()
                                .flatMap(java.util.Optional::stream)
                                .toList();

                assertEquals(1, draws.size());
                assertEquals(1L, drawEventRepository.countBySessionId(session.getId()));
                assertEquals(1L, jdbcTemplate.queryForObject(
                                "select count(*) from audit_log_entries where operation_id = ? and target_type = 'GAME_SESSION' and target_id = ? and action = 'BINGO_NUMBER_DRAWN'",
                                Long.class, draws.get(0).operationId(), session.getId()));
        }

        @Test
        void competingAdminAutomaticTriggersPersistOnlyOneDueDraw() {
                GameSession session = startedSession("Concurrent Admin Draw Room");
                Instant dueStart = Instant.now().minus(gameProperties.drawInterval()).minusSeconds(1);
                jdbcTemplate.update("update bingo_sessions set started_at = ? where id = ?",
                                Timestamp.from(dueStart), session.getId());
                Instant requestTime = Instant.now();
                var ready = new CountDownLatch(2);
                var start = new CountDownLatch(1);
                var first = CompletableFuture.supplyAsync(() -> triggerAttempt(session.getId(), requestTime, ready, start));
                var second = CompletableFuture.supplyAsync(() -> triggerAttempt(session.getId(), requestTime, ready, start));
                try {
                        assertTrue(ready.await(10, TimeUnit.SECONDS));
                        start.countDown();
                        List<DrawEventResponse> draws = java.util.stream.Stream.of(first.get(20, TimeUnit.SECONDS),
                                        second.get(20, TimeUnit.SECONDS))
                                        .filter(java.util.Objects::nonNull)
                                        .toList();
                        assertEquals(1, draws.size());
                        assertEquals(1L, drawEventRepository.countBySessionId(session.getId()));
                        assertEquals(1L, jdbcTemplate.queryForObject(
                                        "select count(*) from audit_log_entries where action = 'BINGO_NUMBER_DRAWN' and target_id = ?",
                                        Long.class, session.getId()));
                } catch (Exception exception) {
                        throw new AssertionError("Concurrent automatic draw trigger test failed", exception);
                }
        }

        private DrawEventResponse triggerAttempt(Long sessionId, Instant now, CountDownLatch ready,
                        CountDownLatch start) {
                ready.countDown();
                try {
                        if (!start.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Timed out waiting to start concurrent draw");
                        }
                        return gameLiveDrawService.triggerAutomaticDraw(sessionId, now, adminUser);
                } catch (ConflictException expected) {
                        return null;
                } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted waiting to start concurrent draw", exception);
                }
        }

        @Test
        void onlyAdminCanStartOrRecordDrawAndLifecycleTransitionsAreAudited() throws Exception {
                var room = bingoGameService.createRoom(roomRequest("Lifecycle Authorization Room"));
                var session = bingoGameService.createSession(room.id(), sessionRequest(GameSessionState.DRAFT));
                String adminToken = loginToken("0911000201", "123456");
                String playerToken = loginToken("0911000202", "123456");
                String agentToken = jwtService.generateToken(agentUser);
                String supportToken = jwtService.generateToken(supportUser);
                String financeVerifierToken = jwtService.generateToken(financeMaker);
                String financeApproverToken = jwtService.generateToken(financeChecker);
                String updatePath = "/api/v1/admin/sessions/" + session.id() + "/state";
                String drawPath = "/api/v1/admin/sessions/" + session.id() + "/draws";

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(updatePath)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new GameSessionStateUpdateRequest(GameSessionState.OPEN))))
                                .andExpect(status().isUnauthorized());
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(updatePath)
                                .header("Authorization", "Bearer " + playerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new GameSessionStateUpdateRequest(GameSessionState.OPEN))))
                                .andExpect(status().isForbidden());
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(updatePath)
                                .header("Authorization", "Bearer " + agentToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new GameSessionStateUpdateRequest(GameSessionState.OPEN))))
                                .andExpect(status().isForbidden());
                mockMvc.perform(post(drawPath).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"numberDrawn\":42}"))
                                .andExpect(status().isUnauthorized());
                mockMvc.perform(post(drawPath).header("Authorization", "Bearer " + playerToken)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"numberDrawn\":42}"))
                                .andExpect(status().isForbidden());
                mockMvc.perform(post(drawPath).header("Authorization", "Bearer " + agentToken)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"numberDrawn\":42}"))
                                .andExpect(status().isForbidden());
                mockMvc.perform(post(drawPath).header("Authorization", "Bearer " + supportToken))
                                .andExpect(status().isForbidden());
                mockMvc.perform(post(drawPath).header("Authorization", "Bearer " + financeVerifierToken))
                                .andExpect(status().isForbidden());
                mockMvc.perform(post(drawPath).header("Authorization", "Bearer " + financeApproverToken))
                                .andExpect(status().isForbidden());
                mockMvc.perform(post(drawPath).header("Authorization", "Bearer invalid-token"))
                                .andExpect(status().isUnauthorized());
                mockMvc.perform(post(drawPath).header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"numberDrawn\":42}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                                .jsonPath("$.code").value("CALLER_SELECTED_DRAW_NOT_ALLOWED"));
                mockMvc.perform(post(drawPath).header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isConflict())
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                                .jsonPath("$.code").value("SESSION_NOT_DRAWING"));
                assertEquals(0L, drawEventRepository.countBySessionId(session.id()));
                assertEquals(0L, jdbcTemplate.queryForObject(
                                "select count(*) from audit_log_entries where target_id = ? and action = 'BINGO_NUMBER_DRAWN'",
                                Long.class, session.id()));
                assertEquals(GameSessionState.DRAFT, gameSessionRepository.findById(session.id()).orElseThrow().getState());

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(updatePath)
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new GameSessionStateUpdateRequest(GameSessionState.OPEN))))
                                .andExpect(status().isOk());
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(updatePath)
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new GameSessionStateUpdateRequest(GameSessionState.STARTED))))
                                .andExpect(status().isOk());
                Instant startedAt = gameSessionRepository.findById(session.id()).orElseThrow().getStartedAt();
                assertTrue(startedAt != null);

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(updatePath)
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new GameSessionStateUpdateRequest(GameSessionState.STARTED))))
                                .andExpect(status().isConflict());
                assertEquals(startedAt, gameSessionRepository.findById(session.id()).orElseThrow().getStartedAt());
                mockMvc.perform(post(drawPath).header("Authorization", "Bearer " + playerToken)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"numberDrawn\":42}"))
                                .andExpect(status().isForbidden());
                mockMvc.perform(post(drawPath).header("Authorization", "Bearer " + agentToken)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"numberDrawn\":42}"))
                                .andExpect(status().isForbidden());
                assertEquals(2L, jdbcTemplate.queryForObject(
                                "select count(*) from audit_log_entries where actor_user_id = ? and target_id = ? and action = 'UPDATE_SESSION_STATE' and json_unquote(json_extract(metadata_json, '$.outcome')) = 'SUCCESS'",
                                Long.class, adminUser.getId(), session.id()));

                Instant dueStart = Instant.now().minus(gameProperties.drawInterval()).minusSeconds(1);
                jdbcTemplate.update("update bingo_sessions set started_at = ? where id = ?",
                                Timestamp.from(dueStart), session.id());
                String drawResponseJson = mockMvc.perform(post(drawPath)
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();
                DrawEventResponse automaticDraw = objectMapper.readValue(drawResponseJson, DrawEventResponse.class);
                assertEquals(session.id(), automaticDraw.sessionId());
                assertEquals(1, automaticDraw.sequenceNo());
                assertTrue(automaticDraw.numberDrawn() >= 1 && automaticDraw.numberDrawn() <= 75);
                DrawEvent persistedDraw = drawEventRepository.findById(automaticDraw.id()).orElseThrow();
                assertEquals(adminUser.getId(), persistedDraw.getDrawnBy().getId());
                assertEquals(1L, jdbcTemplate.queryForObject(
                                "select count(*) from audit_log_entries where operation_id = ? and actor_user_id = ? and target_type = 'GAME_SESSION' and target_id = ? and action = 'BINGO_NUMBER_DRAWN' and json_unquote(json_extract(metadata_json, '$.numberDrawn')) = ?",
                                Long.class, automaticDraw.operationId(), adminUser.getId(), session.id(),
                                Integer.toString(automaticDraw.numberDrawn())));
        }

        @Test
        void drawAndSessionTransitionRollbackTogether() {
                GameSession session = startedSession("Draw Rollback Room");
                Instant dueTime = session.getStartedAt().plus(gameProperties.drawInterval()).plusSeconds(1);
                TransactionTemplate transaction = new TransactionTemplate(transactionManager);

                assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
                        assertTrue(gameLiveDrawService.advanceSessionDrawIfDue(session.getId(), dueTime).isPresent());
                        throw new IllegalStateException("forced test rollback");
                }));

                assertEquals(0L, drawEventRepository.countBySessionId(session.getId()));
                assertEquals(GameSessionState.STARTED,
                                gameSessionRepository.findById(session.getId()).orElseThrow().getState());
                assertEquals(0L, jdbcTemplate.queryForObject(
                                "select count(*) from audit_log_entries where target_id = ? and action = 'BINGO_NUMBER_DRAWN'",
                                Long.class, session.getId()));
        }

        @Test
        void cancellationAndInvalidSessionCannotAdvanceTheDrawEngine() {
                GameSession session = startedSession("Cancelled Draw Room");
                bingoGameService.updateSessionState(session.getId(),
                                new GameSessionStateUpdateRequest(GameSessionState.CANCELLED));
                Instant dueTime = session.getStartedAt().plus(gameProperties.drawInterval()).plusSeconds(1);

                assertTrue(gameLiveDrawService.advanceSessionDrawIfDue(session.getId(), dueTime).isEmpty());
                assertTrue(gameLiveDrawService.advanceSessionDrawIfDue(Long.MAX_VALUE, dueTime).isEmpty());
                assertEquals(0L, drawEventRepository.countBySessionId(session.getId()));
                assertEquals(GameSessionState.CANCELLED,
                                gameSessionRepository.findById(session.getId()).orElseThrow().getState());
        }

        @Test
        void restartRecoveryReplayAndExhaustionAreHandled() throws Exception {
                GameSession session = startedSession("Replay Room");
                Instant recoveredStart = Instant.now().minus(gameProperties.drawInterval()).minusSeconds(1);
                jdbcTemplate.update("update bingo_sessions set started_at = ? where id = ?",
                                Timestamp.from(recoveredStart), session.getId());
                gameLiveDrawScheduler.recoverOnStartup();
                var recoveredDraw = drawEventRepository.findTopBySessionIdOrderBySequenceNoDesc(session.getId())
                                .orElseThrow();
                assertEquals(1, recoveredDraw.getSequenceNo());
                Instant dueTime = recoveredDraw.getDrawnAt().plus(gameProperties.drawInterval()).plusSeconds(1);

                List<Integer> numbers = new ArrayList<>();
                Set<Integer> uniqueNumbers = new HashSet<>();
                numbers.add(recoveredDraw.getNumberDrawn());
                uniqueNumbers.add(recoveredDraw.getNumberDrawn());
                for (int index = 1; index < 75; index++) {
                        DrawEventResponse draw = gameLiveDrawService.advanceSessionDrawIfDue(session.getId(),
                                        dueTime.plus(gameProperties.drawInterval().multipliedBy(index - 1)))
                                        .orElseThrow();
                        numbers.add(draw.numberDrawn());
                        uniqueNumbers.add(draw.numberDrawn());
                }

                assertEquals(75, numbers.size());
                assertEquals(75, uniqueNumbers.size());
                assertTrue(numbers.stream().allMatch(number -> number >= 1 && number <= 75));
                assertTrue(drawEventRepository.findBySessionIdOrderBySequenceNoAsc(session.getId()).stream()
                                .allMatch(draw -> draw.getSession().getId().equals(session.getId())));
                assertTrue(drawEventRepository.findBySessionIdOrderBySequenceNoAsc(session.getId()).stream()
                                .allMatch(draw -> draw.getDrawnAt() != null));
                assertEquals(GameSessionState.CLOSED,
                                gameSessionRepository.findById(session.getId()).orElseThrow().getState());
                assertFalse(gameLiveDrawService
                                .advanceSessionDrawIfDue(session.getId(), dueTime.plus(Duration.ofDays(1)))
                                .isPresent());

                String replay = mockMvc.perform(get("/api/v1/sessions/" + session.getId() + "/draws")
                                .header("Authorization", "Bearer " + loginToken("0911000201", "123456"))
                                .param("afterSequence", "1"))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                List<DrawEventResponse> replayed = objectMapper.readValue(replay, new TypeReference<>() {
                });
                assertEquals(74, replayed.size());
                for (int index = 1; index < replayed.size(); index++) {
                        assertTrue(replayed.get(index - 1).sequenceNo() < replayed.get(index).sequenceNo());
                }
        }

        @Test
        void websocketSubscriptionRequiresJwtAndReceivesBroadcast() throws Exception {
                GameSession session = startedSession("Socket Room");
                String token = loginToken("0911000202", "123456");
                assertTrue(token.startsWith("eyJ"));
                Instant dueTime = session.getStartedAt().plus(gameProperties.drawInterval()).plusSeconds(1);
                gameLiveDrawService.advanceSessionDrawIfDue(session.getId(), dueTime);

                assertFalse(recordingSimpMessagingTemplate.getRecordedMessages().isEmpty());
                RecordedMessage message = recordingSimpMessagingTemplate.getRecordedMessages().get(0);
                assertEquals("/topic/session/" + session.getId() + "/draws", message.destination());
                assertTrue(message.payload() instanceof DrawEventResponse);
        }

        @Test
        void websocketConnectionWithoutJwtIsRejected() {
                WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
                stompClient.setMessageConverter(new MappingJackson2MessageConverter());

                assertThrows(Exception.class, () -> stompClient
                                .connectAsync(URI.create("ws://localhost:" + port + "/ws"), new WebSocketHttpHeaders(),
                                                new StompHeaders(), new StompSessionHandlerAdapter() {
                                                })
                                .get(5, TimeUnit.SECONDS));
        }

        private GameSession startedSession(String roomName) {
                var room = bingoGameService.createRoom(roomRequest(roomName));
                var session = bingoGameService.createSession(room.id(), sessionRequest(GameSessionState.DRAFT));
                bingoGameService.updateSessionState(session.id(),
                                new GameSessionStateUpdateRequest(GameSessionState.OPEN));
                bingoGameService.updateSessionState(session.id(),
                                new GameSessionStateUpdateRequest(GameSessionState.STARTED));
                return gameSessionRepository.findById(session.id()).orElseThrow();
        }

        private String loginToken(String phoneNumber, String password) throws Exception {
                User user = userRepository.findByNormalizedPhone(phoneNormalizer.normalize(phoneNumber)).orElseThrow();
                if (!user.hasRole(UserRole.PLAYER)) {
                        return jwtService.generateToken(user);
                }
                String response = mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new LoginRequest(phoneNumber, password))))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                return objectMapper.readTree(response).get("accessToken").asText();
        }

        private StompHeaders connectHeaders(String token) {
                StompHeaders headers = new StompHeaders();
                headers.add("Authorization", "Bearer " + token);
                return headers;
        }

        private BingoRoomRequest roomRequest(String name) {
                return new BingoRoomRequest(
                                name,
                                "Live draw room",
                                new BigDecimal("4.00"),
                                "ETB",
                                "Africa/Addis_Ababa",
                                "0 0/30 * * * *",
                                10,
                                10,
                                true,
                                true,
                                true,
                                List.of(BingoPatternType.LINE, BingoPatternType.TWO_LINES, BingoPatternType.FULL_HOUSE),
                                List.of(
                                                new BingoPrizeConfigRequest(BingoPatternType.LINE,
                                                                new BigDecimal("10.00"),
                                                                PrizeSettlementPath.PLAYER_WALLET, 1),
                                                new BingoPrizeConfigRequest(BingoPatternType.TWO_LINES,
                                                                new BigDecimal("20.00"),
                                                                PrizeSettlementPath.PLAYER_WALLET, 1),
                                                new BingoPrizeConfigRequest(BingoPatternType.FULL_HOUSE,
                                                                new BigDecimal("50.00"),
                                                                PrizeSettlementPath.PLAYER_WALLET, 1)),
                                null);
        }

        private GameSessionRequest sessionRequest(GameSessionState state) {
                return new GameSessionRequest(
                                "Live session",
                                Instant.now().plusSeconds(60),
                                state,
                                new BigDecimal("4.00"),
                                "ETB",
                                10,
                                10,
                                true,
                                true,
                                List.of(BingoPatternType.LINE, BingoPatternType.TWO_LINES, BingoPatternType.FULL_HOUSE),
                                List.of(
                                                new BingoPrizeConfigRequest(BingoPatternType.LINE,
                                                                new BigDecimal("10.00"),
                                                                PrizeSettlementPath.PLAYER_WALLET, 1),
                                                new BingoPrizeConfigRequest(BingoPatternType.TWO_LINES,
                                                                new BigDecimal("20.00"),
                                                                PrizeSettlementPath.PLAYER_WALLET, 1),
                                                new BingoPrizeConfigRequest(BingoPatternType.FULL_HOUSE,
                                                                new BigDecimal("50.00"),
                                                                PrizeSettlementPath.PLAYER_WALLET, 1)));
        }

        @org.springframework.boot.test.context.TestConfiguration
        static class TestMessagingConfig {

                @Bean
                @Primary
                RecordingSimpMessagingTemplate recordingSimpMessagingTemplate() {
                        return new RecordingSimpMessagingTemplate();
                }
        }

        static final class RecordingSimpMessagingTemplate extends SimpMessagingTemplate {

                private final List<RecordedMessage> recordedMessages = new ArrayList<>();

                RecordingSimpMessagingTemplate() {
                        super(new ExecutorSubscribableChannel());
                }

                @Override
                public void convertAndSend(String destination, Object payload) {
                        recordedMessages.add(new RecordedMessage(destination, payload));
                }

                List<RecordedMessage> getRecordedMessages() {
                        return recordedMessages;
                }
        }

        record RecordedMessage(String destination, Object payload) {
        }
}
