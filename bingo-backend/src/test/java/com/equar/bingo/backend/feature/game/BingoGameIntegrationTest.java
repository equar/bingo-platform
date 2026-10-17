package com.equar.bingo.backend.feature.game;

import com.equar.bingo.backend.common.idempotency.IdempotentResult;
import com.equar.bingo.backend.feature.auth.dto.LoginRequest;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.equar.bingo.backend.feature.game.dto.BingoPrizeConfigRequest;
import com.equar.bingo.backend.feature.game.dto.BingoRoomRequest;
import com.equar.bingo.backend.feature.game.dto.DrawEventRequest;
import com.equar.bingo.backend.feature.game.dto.GameSessionRequest;
import com.equar.bingo.backend.feature.game.dto.GameSessionStateUpdateRequest;
import com.equar.bingo.backend.feature.game.dto.PrizeEntitlementSettlementRequest;
import com.equar.bingo.backend.feature.game.dto.TicketPurchaseResponse;
import com.equar.bingo.backend.feature.game.dto.WinClaimRequest;
import com.equar.bingo.backend.feature.game.dto.WinClaimReviewRequest;
import com.equar.bingo.backend.feature.game.entity.BingoPatternType;
import com.equar.bingo.backend.feature.game.entity.GameSessionState;
import com.equar.bingo.backend.feature.game.entity.PrizeEntitlementStatus;
import com.equar.bingo.backend.feature.game.entity.PrizeSettlementPath;
import com.equar.bingo.backend.feature.game.entity.Ticket;
import com.equar.bingo.backend.feature.game.entity.WinClaimStatus;
import com.equar.bingo.backend.feature.game.repository.DrawEventRepository;
import com.equar.bingo.backend.feature.game.repository.BingoRoomRepository;
import com.equar.bingo.backend.feature.game.repository.GameSessionRepository;
import com.equar.bingo.backend.feature.game.repository.PrizeEntitlementRepository;
import com.equar.bingo.backend.feature.game.repository.TicketRepository;
import com.equar.bingo.backend.feature.game.repository.WinClaimRepository;
import com.equar.bingo.backend.feature.game.service.BingoGameService;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.wallet.entity.Wallet;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransaction;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransactionType;
import com.equar.bingo.backend.feature.agent.repository.PayoutVerificationRecordRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentFloatTransactionRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentFloatRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentRepository;
import com.equar.bingo.backend.feature.support.repository.InternalNoteRepository;
import com.equar.bingo.backend.feature.support.repository.SupportMessageRepository;
import com.equar.bingo.backend.feature.support.repository.SupportTicketRepository;
import com.equar.bingo.backend.feature.wallet.repository.IdempotencyRecordRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import com.equar.bingo.backend.feature.wallet.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BingoGameIntegrationTest {

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
        WalletRepository walletRepository;

        @Autowired
        WalletTransactionRepository walletTransactionRepository;

        @Autowired
        TicketRepository ticketRepository;

        @Autowired
        DrawEventRepository drawEventRepository;

        @Autowired
        WinClaimRepository winClaimRepository;

        @Autowired
        PrizeEntitlementRepository prizeEntitlementRepository;
        @Autowired
        PayoutVerificationRecordRepository payoutVerificationRecordRepository;

        @Autowired
        AgentFloatTransactionRepository agentFloatTransactionRepository;

        @Autowired
        AgentFloatRepository agentFloatRepository;

        @Autowired
        AgentRepository agentRepository;

        @Autowired
        IdempotencyRecordRepository idempotencyRecordRepository;

        @Autowired
        BingoGameService bingoGameService;

        @Autowired
        GameSessionRepository gameSessionRepository;

        @Autowired
        BingoRoomRepository bingoRoomRepository;

        @Autowired
        WalletService walletService;

        @Autowired
        SupportTicketRepository supportTicketRepository;

        @Autowired
        SupportMessageRepository supportMessageRepository;

        @Autowired
        InternalNoteRepository internalNoteRepository;

        @Autowired
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

        private User adminUser;
        private User playerOne;
        private User playerTwo;

        @BeforeEach
        void setUp() {
                internalNoteRepository.deleteAllInBatch();
                supportMessageRepository.deleteAllInBatch();
                supportTicketRepository.deleteAllInBatch();
                payoutVerificationRecordRepository.deleteAll();
                agentFloatTransactionRepository.deleteAll();
                agentFloatRepository.deleteAll();
                agentRepository.deleteAll();
                walletTransactionRepository.deleteAll();
                jdbcTemplate.update("delete from signup_reward_grants");
                jdbcTemplate.update("delete from wallet_transfers");
                jdbcTemplate.update("delete from signup_reward_policies");
                jdbcTemplate.update("delete from wallet_transfer_policies");
                prizeEntitlementRepository.deleteAll();
                winClaimRepository.deleteAll();
                drawEventRepository.deleteAll();
                ticketRepository.deleteAll();
                gameSessionRepository.deleteAll();
                bingoRoomRepository.deleteAll();
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
                playerOne = userRepository.save(User.create(
                                phoneNormalizer.normalize("0911000202"),
                                "Player One",
                                passwordEncoder.encode("123456"),
                                AccountStatus.ACTIVE,
                                Set.of(UserRole.PLAYER),
                                now));
                playerTwo = userRepository.save(User.create(
                                phoneNormalizer.normalize("0911000203"),
                                "Player Two",
                                passwordEncoder.encode("123456"),
                                AccountStatus.ACTIVE,
                                Set.of(UserRole.PLAYER),
                                now));

                walletService.createWalletIfMissing(playerOne);
                walletService.createWalletIfMissing(playerTwo);
                walletService.adjustPlayerWallet(playerOne.getId(), new BingoUserPrincipal(adminUser),
                                new com.equar.bingo.backend.feature.wallet.dto.WalletAdjustmentRequest(
                                                new BigDecimal("1000.00"), "ETB",
                                                "seed"),
                                "seed-player-one");
                walletService.adjustPlayerWallet(playerTwo.getId(), new BingoUserPrincipal(adminUser),
                                new com.equar.bingo.backend.feature.wallet.dto.WalletAdjustmentRequest(
                                                new BigDecimal("1000.00"), "ETB",
                                                "seed"),
                                "seed-player-two");
        }

        @Test
        void roomAndSessionEndpointsExposeSnapshots() throws Exception {
                BingoRoomRequest roomRequest = roomRequest("Main Hall A", 1, 10, true);
                var room = bingoGameService.createRoom(roomRequest);
                var session = bingoGameService.createSession(room.id(), sessionRequest(GameSessionState.OPEN));
                String adminToken = loginToken("0911000201", "123456");

                mockMvc.perform(get("/api/v1/rooms")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].name").value("Main Hall A"));

                mockMvc.perform(get("/api/v1/rooms/" + room.id())
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.sessionCapacity").value(1));

                mockMvc.perform(get("/api/v1/sessions/" + session.id())
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.state").value("OPEN"));
        }

        @Test
        void purchaseClaimReviewAndSingleSettlementWork() throws Exception {
                var room = bingoGameService.createRoom(roomRequest("Main Hall B", 4, 10, false));
                var session = bingoGameService.createSession(room.id(), sessionRequest(GameSessionState.OPEN));
                BingoUserPrincipal playerPrincipal = new BingoUserPrincipal(
                                userRepository.findById(playerOne.getId()).orElseThrow());
                BingoUserPrincipal adminPrincipal = new BingoUserPrincipal(
                                userRepository.findById(adminUser.getId()).orElseThrow());
                String playerToken = loginToken("0911000202", "123456");
                String adminToken = loginToken("0911000201", "123456");

                IdempotentResult<TicketPurchaseResponse> purchase = bingoGameService.purchaseTicket(session.id(),
                                playerPrincipal, "ticket-1");
                Ticket ticket = ticketRepository.findById(purchase.body().ticket().id()).orElseThrow();

                String purchaseAgainJson = mockMvc.perform(post("/api/v1/sessions/" + session.id() + "/tickets")
                                .header("Authorization", "Bearer " + playerToken)
                                .header("Idempotency-Key", "ticket-1")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();
                assertTrue(purchaseAgainJson.contains(ticket.getPublicTicketCode()));

                List<Integer> winningNumbers = winningLineNumbers(ticket.getGridJson());
                bingoGameService.updateSessionState(session.id(),
                                new GameSessionStateUpdateRequest(GameSessionState.STARTED));
                for (Integer number : winningNumbers) {
                        bingoGameService.recordDraw(session.id(), new DrawEventRequest(number), adminPrincipal);
                }

                mockMvc.perform(post("/api/v1/tickets/" + ticket.getId() + "/win-claims")
                                .header("Authorization", "Bearer " + playerToken)
                                .header("Idempotency-Key", "claim-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new WinClaimRequest(BingoPatternType.LINE))))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.serverValidationPassed").value(true))
                                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

                mockMvc.perform(post("/api/v1/tickets/" + ticket.getId() + "/win-claims")
                                .header("Authorization", "Bearer " + playerToken)
                                .header("Idempotency-Key", "claim-2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new WinClaimRequest(BingoPatternType.LINE))))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("DUPLICATE_WIN_CLAIM"));

                WinClaimRequest fullHouseBeforeDraws = new WinClaimRequest(BingoPatternType.FULL_HOUSE);
                mockMvc.perform(post("/api/v1/tickets/" + ticket.getId() + "/win-claims")
                                .header("Authorization", "Bearer " + loginToken("0911000203", "123456"))
                                .header("Idempotency-Key", "claim-invalid")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(fullHouseBeforeDraws)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));

                var claim = winClaimRepository.findAll().get(0);
                bingoGameService.reviewWinClaim(claim.getId(), adminPrincipal, new WinClaimReviewRequest("approved"),
                                true);
                var entitlement = prizeEntitlementRepository.findAll().get(0);
                assertEquals(PrizeEntitlementStatus.PENDING, entitlement.getStatus());

                bingoGameService.settleEntitlement(entitlement.getId(), adminPrincipal,
                                new PrizeEntitlementSettlementRequest("SETTLEMENT-1", "wallet payout"));
                Wallet wallet = walletRepository.findByOwnerId(playerOne.getId()).orElseThrow();
                assertEquals(new BigDecimal("1006.00"), wallet.getAvailableBalance());

                assertThrows(Exception.class,
                                () -> bingoGameService.settleEntitlement(entitlement.getId(), adminPrincipal,
                                                new PrizeEntitlementSettlementRequest("SETTLEMENT-2", "duplicate")));
        }

        @Test
        void sessionStateTransitionsAndCapacityRulesAreEnforced() throws Exception {
                var transitionRoom = bingoGameService.createRoom(roomRequest("Main Hall C", 1, 1, true));
                var transitionSession = bingoGameService.createSession(transitionRoom.id(),
                                sessionRequest(GameSessionState.DRAFT));
                BingoUserPrincipal playerPrincipal = new BingoUserPrincipal(
                                userRepository.findById(playerOne.getId()).orElseThrow());
                BingoUserPrincipal adminPrincipal = new BingoUserPrincipal(
                                userRepository.findById(adminUser.getId()).orElseThrow());

                bingoGameService.updateSessionState(transitionSession.id(),
                                new GameSessionStateUpdateRequest(GameSessionState.OPEN));
                bingoGameService.updateSessionState(transitionSession.id(),
                                new GameSessionStateUpdateRequest(GameSessionState.STARTED));

                assertThrows(Exception.class, () -> bingoGameService.updateSessionState(transitionSession.id(),
                                new GameSessionStateUpdateRequest(GameSessionState.OPEN)));

                var capacityRoom = bingoGameService.createRoom(roomRequest("Main Hall D", 1, 1, false));
                var capacitySession = bingoGameService.createSession(capacityRoom.id(),
                                sessionRequest(GameSessionState.OPEN));

                ExecutorService executorService = Executors.newFixedThreadPool(2);
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                Future<String> first = executorService
                                .submit(() -> purchaseAttempt(capacitySession.id(), playerPrincipal, "cap-a", ready,
                                                start));
                Future<String> second = executorService
                                .submit(() -> purchaseAttempt(capacitySession.id(), new BingoUserPrincipal(playerTwo),
                                                "cap-b", ready,
                                                start));
                ready.await(10, TimeUnit.SECONDS);
                start.countDown();
                List<String> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
                executorService.shutdownNow();

                assertTrue(results.contains("OK"));
                assertTrue(results.contains("CONFLICT"));
        }

        private String purchaseAttempt(Long sessionId, BingoUserPrincipal principal, String key, CountDownLatch ready,
                        CountDownLatch start) throws Exception {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                try {
                        bingoGameService.purchaseTicket(sessionId, principal, key);
                        return "OK";
                } catch (Exception exception) {
                        return "CONFLICT";
                }
        }

        private String loginToken(String phoneNumber, String password) throws Exception {
                String response = mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new LoginRequest(phoneNumber, password))))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                return objectMapper.readTree(response).get("accessToken").asText();
        }

        private BingoRoomRequest roomRequest(String name, int sessionCapacity, int maxTicketsPerPlayer,
                        boolean active) {
                return new BingoRoomRequest(
                                name,
                                "Primary room",
                                new BigDecimal("4.00"),
                                "ETB",
                                "Africa/Addis_Ababa",
                                "0 0/30 * * * *",
                                sessionCapacity,
                                maxTicketsPerPlayer,
                                true,
                                true,
                                active,
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

        private GameSessionRequest sessionRequest(GameSessionState state) {
                return new GameSessionRequest(
                                "Session One",
                                Instant.now().plusSeconds(3600),
                                state,
                                new BigDecimal("4.00"),
                                "ETB",
                                1,
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

        private List<Integer> winningLineNumbers(String gridJson) throws Exception {
                List<List<Integer>> grid = objectMapper.readValue(gridJson,
                                new com.fasterxml.jackson.core.type.TypeReference<>() {
                                });
                return new ArrayList<>(
                                List.of(grid.get(0).get(0), grid.get(0).get(1), grid.get(0).get(2), grid.get(0).get(3),
                                                grid.get(0).get(4)));
        }
}
