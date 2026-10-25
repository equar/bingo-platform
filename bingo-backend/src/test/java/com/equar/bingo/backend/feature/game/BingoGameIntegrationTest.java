package com.equar.bingo.backend.feature.game;

import com.equar.bingo.backend.MySqlIntegrationTest;
import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.common.idempotency.IdempotentResult;
import com.equar.bingo.backend.feature.auth.dto.LoginRequest;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.equar.bingo.backend.feature.auth.service.JwtService;
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
import com.equar.bingo.backend.feature.wallet.service.WalletAdjustmentService;
import com.equar.bingo.backend.feature.wallet.service.WalletService;
import com.equar.bingo.backend.feature.wallet.dto.WalletAdjustmentReviewRequest;
import com.equar.bingo.backend.feature.wallet.dto.WalletAdjustmentSubmissionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.dao.OptimisticLockingFailureException;

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
class BingoGameIntegrationTest extends MySqlIntegrationTest {

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
        WalletAdjustmentService walletAdjustmentService;

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
        private User financeMaker;
        private User financeChecker;

        @BeforeEach
        void setUp() {
                jdbcTemplate.update("delete from audit_log_entries");
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
                financeMaker = financeUser("0911000204", UserRole.FINANCE_VERIFIER, now);
                financeChecker = financeUser("0911000205", UserRole.FINANCE_APPROVER, now);

                walletService.createWalletIfMissing(playerOne);
                walletService.createWalletIfMissing(playerTwo);
                fundWallet(playerOne, "GAME-PLAYER-ONE");
                fundWallet(playerTwo, "GAME-PLAYER-TWO");
        }

        private User financeUser(String phone, UserRole role, Instant now) {
                return userRepository.save(User.create(
                                phoneNormalizer.normalize(phone),
                                "Finance " + role.name(),
                                passwordEncoder.encode("123456"),
                                AccountStatus.ACTIVE,
                                Set.of(UserRole.ADMIN, role),
                                now));
        }

        private void fundWallet(User player, String reference) {
                var request = walletAdjustmentService.submit(player.getId(), new BingoUserPrincipal(financeMaker),
                                new WalletAdjustmentSubmissionRequest(new BigDecimal("1000.00"), "ETB",
                                                "Approved integration fixture funding", reference));
                walletAdjustmentService.approve(request.id(), new BingoUserPrincipal(financeChecker),
                                new WalletAdjustmentReviewRequest("Independent integration fixture approval"));
        }

        @Test
        void roomAndSessionEndpointsExposeSnapshots() throws Exception {
                BingoRoomRequest roomRequest = roomRequest("Main Hall A", 1, 10, true);
                var room = bingoGameService.createRoom(roomRequest);
                var session = bingoGameService.createSession(room.id(), sessionRequest(GameSessionState.OPEN));
                String playerToken = loginToken("0911000202", "123456");

                mockMvc.perform(get("/api/v1/rooms")
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].name").value("Main Hall A"));

                mockMvc.perform(get("/api/v1/rooms/" + room.id())
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.sessionCapacity").value(1))
                                .andExpect(jsonPath("$.configuredPrizes").doesNotExist())
                                .andExpect(jsonPath("$.maxTicketsPerPlayer").doesNotExist());

                mockMvc.perform(get("/api/v1/rooms/" + room.id() + "/sessions")
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(session.id()))
                                .andExpect(jsonPath("$[0].scheduledStartAt").isNotEmpty())
                                .andExpect(jsonPath("$[0].configuredPrizes").doesNotExist())
                                .andExpect(jsonPath("$[0].ticketSalesCloseOnSessionStart").doesNotExist());

                mockMvc.perform(get("/api/v1/sessions/" + session.id())
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.state").value("OPEN"))
                                .andExpect(jsonPath("$.configuredPrizes").doesNotExist())
                                .andExpect(jsonPath("$.maxTicketsPerPlayer").doesNotExist());
        }

        @Test
        void playerDiscoveryRequiresActivePlayerAndDoesNotMutateGameOrWalletData() throws Exception {
                var room = bingoGameService.createRoom(roomRequest("Discovery Room", 8, 10, true));
                var session = bingoGameService.createSession(room.id(), sessionRequest(GameSessionState.OPEN));
                String playerToken = loginToken("0911000202", "123456");
                String adminToken = loginToken("0911000201", "123456");
                User agent = userRepository.save(User.create(phoneNormalizer.normalize("0911000998"), "Agent Test",
                                passwordEncoder.encode("123456"), AccountStatus.ACTIVE, Set.of(UserRole.AGENT),
                                Instant.now()));
                String agentToken = jwtService.generateToken(agent);
                User suspendedPlayer = userRepository.save(User.create(
                                phoneNormalizer.normalize("0911000997"), "Suspended Player",
                                passwordEncoder.encode("123456"), AccountStatus.SUSPENDED,
                                Set.of(UserRole.PLAYER), Instant.now()));
                String suspendedToken = jwtService.generateToken(suspendedPlayer);

                long ticketsBefore = ticketRepository.count();
                long transactionsBefore = walletTransactionRepository.count();

                mockMvc.perform(get("/api/v1/rooms"))
                                .andExpect(status().isUnauthorized());
                mockMvc.perform(get("/api/v1/rooms")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isForbidden());
                mockMvc.perform(get("/api/v1/rooms")
                                .header("Authorization", "Bearer " + agentToken))
                                .andExpect(status().isForbidden());
                mockMvc.perform(get("/api/v1/rooms")
                                .header("Authorization", "Bearer " + suspendedToken))
                                .andExpect(status().isUnauthorized());
                mockMvc.perform(post("/api/v1/sessions/" + session.id() + "/tickets")
                                .header("Authorization", "Bearer " + suspendedToken)
                                .header("Idempotency-Key", "suspended-player-ticket")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isUnauthorized());

                mockMvc.perform(get("/api/v1/rooms")
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(room.id()));
                mockMvc.perform(get("/api/v1/rooms/999999")
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"))
                                .andExpect(jsonPath("$.trace").doesNotExist());
                mockMvc.perform(get("/api/v1/rooms/999999/sessions")
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isNotFound());
                mockMvc.perform(get("/api/v1/sessions/999999")
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isNotFound());
                mockMvc.perform(get("/api/v1/rooms/" + room.id() + "/sessions")
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(session.id()))
                                .andExpect(jsonPath("$[0].state").value("OPEN"));

                var inactiveRoom = bingoGameService.createRoom(roomRequest("Hidden Inactive Room", 2, 1, false));
                mockMvc.perform(get("/api/v1/rooms/" + inactiveRoom.id())
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isNotFound());
                mockMvc.perform(get("/api/v1/rooms/" + inactiveRoom.id() + "/sessions")
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isNotFound());

                var roomEntity = bingoRoomRepository.findById(room.id()).orElseThrow();
                roomEntity.setActive(false);
                bingoRoomRepository.saveAndFlush(roomEntity);
                mockMvc.perform(get("/api/v1/sessions/" + session.id())
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));

                assertEquals(ticketsBefore, ticketRepository.count());
                assertEquals(transactionsBefore, walletTransactionRepository.count());
        }

        @Test
        void purchaseClaimReviewAndSingleSettlementWork() throws Exception {
                var room = bingoGameService.createRoom(roomRequest("Main Hall B", 4, 10, true));
                var session = bingoGameService.createSession(room.id(), sessionRequest(GameSessionState.OPEN));
                BingoUserPrincipal playerPrincipal = new BingoUserPrincipal(
                                userRepository.findById(playerOne.getId()).orElseThrow());
                BingoUserPrincipal adminPrincipal = new BingoUserPrincipal(
                                userRepository.findById(adminUser.getId()).orElseThrow());
                String playerToken = loginToken("0911000202", "123456");
                String adminToken = loginToken("0911000201", "123456");

                Wallet ticketWalletBefore = walletRepository.findByOwnerId(playerOne.getId()).orElseThrow();
                ticketWalletBefore.setPromotionalBalance(new BigDecimal("5.00"));
                walletRepository.save(ticketWalletBefore);
                BigDecimal transferableBeforeTicket = ticketWalletBefore.getAvailableBalance();
                BigDecimal promotionalBeforeTicket = ticketWalletBefore.getPromotionalBalance();
                BigDecimal totalBeforeTicket = transferableBeforeTicket.add(promotionalBeforeTicket);

                IdempotentResult<TicketPurchaseResponse> purchase = bingoGameService.purchaseTicket(session.id(),
                                playerPrincipal, "ticket-1");
                Ticket ticket = ticketRepository.findById(purchase.body().ticket().id()).orElseThrow();
                Wallet ticketWalletAfter = walletRepository.findByOwnerId(playerOne.getId()).orElseThrow();
                WalletTransaction ticketTransaction = walletTransactionRepository.findAll().stream()
                                .filter(transaction -> transaction.getTransactionType()
                                                == WalletTransactionType.TICKET_PURCHASE_DEBIT)
                                .findFirst().orElseThrow();
                assertEquals(transferableBeforeTicket, ticketTransaction.getTransferableBalanceBefore());
                assertEquals(ticketWalletAfter.getAvailableBalance(),
                                ticketTransaction.getTransferableBalanceAfter());
                assertEquals(promotionalBeforeTicket, ticketTransaction.getPromotionalBalanceBefore());
                assertEquals(ticketWalletAfter.getPromotionalBalance(),
                                ticketTransaction.getPromotionalBalanceAfter());
                assertEquals(totalBeforeTicket, ticketTransaction.getBalanceBefore());
                assertEquals(ticketWalletAfter.getTotalBalance(), ticketTransaction.getBalanceAfter());

                String purchaseAgainJson = mockMvc.perform(post("/api/v1/sessions/" + session.id() + "/tickets")
                                .header("Authorization", "Bearer " + playerToken)
                                .header("Idempotency-Key", "ticket-1")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();
                assertTrue(purchaseAgainJson.contains(ticket.getPublicTicketCode()));
                assertTrue(jdbcTemplate.queryForObject(
                                "select count(*) from audit_log_entries where action = 'PURCHASE_TICKET'",
                                Integer.class) > 0);
                mockMvc.perform(get("/api/v1/sessions/" + session.id() + "/my-tickets")
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalElements").value(1))
                                .andExpect(jsonPath("$.content[0].id").value(ticket.getId()));
                String otherPlayerToken = loginToken("0911000203", "123456");
                mockMvc.perform(get("/api/v1/sessions/" + session.id() + "/my-tickets")
                                .header("Authorization", "Bearer " + otherPlayerToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalElements").value(0))
                                .andExpect(jsonPath("$.content").isEmpty());

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
                var duplicateReview = assertThrows(ConflictException.class,
                                () -> bingoGameService.reviewWinClaim(claim.getId(), adminPrincipal,
                                                new WinClaimReviewRequest("duplicate review"), true));
                assertEquals("WIN_CLAIM_ALREADY_REVIEWED", duplicateReview.getCode());
                assertEquals(1, prizeEntitlementRepository.findAll().size());

                Wallet walletBeforePrize = walletRepository.findByOwnerId(playerOne.getId()).orElseThrow();
                BigDecimal transferableBeforePrize = walletBeforePrize.getAvailableBalance();
                BigDecimal promotionalBeforePrize = walletBeforePrize.getPromotionalBalance();
                mockMvc.perform(post("/api/v1/admin/prize-entitlements/" + entitlement.getId() + "/settle")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new PrizeEntitlementSettlementRequest("SETTLEMENT-1", "wallet payout"))))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("PRIZE_SETTLEMENT_BLOCKED"));
                Wallet walletAfterBlockedPrize = walletRepository.findByOwnerId(playerOne.getId()).orElseThrow();
                assertEquals(transferableBeforePrize, walletAfterBlockedPrize.getAvailableBalance());
                assertEquals(promotionalBeforePrize, walletAfterBlockedPrize.getPromotionalBalance());
                assertEquals(PrizeEntitlementStatus.PENDING,
                                prizeEntitlementRepository.findById(entitlement.getId()).orElseThrow().getStatus());
                assertTrue(walletTransactionRepository.findAll().stream().noneMatch(transaction ->
                                transaction.getTransactionType() == WalletTransactionType.PRIZE_CREDIT));
        }

        @Test
        void ticketPurchaseRejectsDraftSessionsAndRoomsDeactivatedAfterScheduling() {
                var room = bingoGameService.createRoom(roomRequest("Lifecycle purchase guard", 2, 2, true));
                var session = bingoGameService.createSession(room.id(), sessionRequest(GameSessionState.DRAFT));
                var player = new BingoUserPrincipal(userRepository.findById(playerOne.getId()).orElseThrow());
                Wallet walletBefore = walletRepository.findByOwnerId(playerOne.getId()).orElseThrow();
                BigDecimal transferableBefore = walletBefore.getAvailableBalance();
                BigDecimal promotionalBefore = walletBefore.getPromotionalBalance();
                long ticketsBefore = ticketRepository.count();
                long transactionsBefore = walletTransactionRepository.count();

                var draftFailure = assertThrows(ConflictException.class,
                                () -> bingoGameService.purchaseTicket(session.id(), player, "draft-ticket"));
                assertEquals("SESSION_NOT_OPEN", draftFailure.getCode());

                bingoGameService.updateSessionState(session.id(),
                                new GameSessionStateUpdateRequest(GameSessionState.OPEN));
                var persistedRoom = bingoRoomRepository.findById(room.id()).orElseThrow();
                persistedRoom.setActive(false);
                bingoRoomRepository.saveAndFlush(persistedRoom);

                var inactiveFailure = assertThrows(ConflictException.class,
                                () -> bingoGameService.purchaseTicket(session.id(), player, "inactive-room-ticket"));
                assertEquals("ROOM_INACTIVE", inactiveFailure.getCode());
                assertEquals(ticketsBefore, ticketRepository.count());
                assertEquals(transactionsBefore, walletTransactionRepository.count());
                Wallet walletAfter = walletRepository.findByOwnerId(playerOne.getId()).orElseThrow();
                assertEquals(transferableBefore, walletAfter.getAvailableBalance());
                assertEquals(promotionalBefore, walletAfter.getPromotionalBalance());
        }

        @Test
        void cancelledAndCompletedSessionsRejectTicketPurchase() {
                var room = bingoGameService.createRoom(roomRequest("Terminal purchase guard", 3, 3, true));
                var cancelled = bingoGameService.createSession(room.id(), sessionRequest(GameSessionState.DRAFT));
                var completed = bingoGameService.createSession(
                                bingoGameService.createRoom(roomRequest("Completed purchase guard", 2, 2, true)).id(),
                                sessionRequest(GameSessionState.DRAFT));
                bingoGameService.updateSessionState(cancelled.id(),
                                new GameSessionStateUpdateRequest(GameSessionState.CANCELLED));
                bingoGameService.updateSessionState(completed.id(),
                                new GameSessionStateUpdateRequest(GameSessionState.OPEN));
                bingoGameService.updateSessionState(completed.id(),
                                new GameSessionStateUpdateRequest(GameSessionState.STARTED));
                bingoGameService.updateSessionState(completed.id(),
                                new GameSessionStateUpdateRequest(GameSessionState.CLOSED));
                var player = new BingoUserPrincipal(userRepository.findById(playerOne.getId()).orElseThrow());
                long ticketsBefore = ticketRepository.count();
                long transactionsBefore = walletTransactionRepository.count();

                var cancelledFailure = assertThrows(ConflictException.class,
                                () -> bingoGameService.purchaseTicket(cancelled.id(), player, "cancelled-ticket"));
                var completedFailure = assertThrows(ConflictException.class,
                                () -> bingoGameService.purchaseTicket(completed.id(), player, "completed-ticket"));

                assertEquals("SESSION_NOT_OPEN", cancelledFailure.getCode());
                assertEquals("SESSION_NOT_OPEN", completedFailure.getCode());
                assertEquals(ticketsBefore, ticketRepository.count());
                assertEquals(transactionsBefore, walletTransactionRepository.count());
        }

        @Test
        void insufficientWalletBalanceRejectsPurchaseWithoutIssuanceOrDebit() {
                var room = bingoGameService.createRoom(roomRequest("Insufficient ticket purchase", 2, 2, true));
                var session = bingoGameService.createSession(room.id(), sessionRequest(GameSessionState.OPEN));
                var player = new BingoUserPrincipal(userRepository.findById(playerOne.getId()).orElseThrow());
                Wallet walletBefore = walletRepository.findByOwnerId(playerOne.getId()).orElseThrow();
                walletBefore.setAvailableBalance(BigDecimal.ZERO.setScale(2));
                walletBefore.setPromotionalBalance(BigDecimal.ZERO.setScale(2));
                walletRepository.saveAndFlush(walletBefore);
                long ticketsBefore = ticketRepository.count();
                long transactionsBefore = walletTransactionRepository.count();

                var failure = assertThrows(ConflictException.class,
                                () -> bingoGameService.purchaseTicket(session.id(), player, "insufficient-ticket"));

                assertEquals("INSUFFICIENT_BALANCE", failure.getCode());
                assertEquals(ticketsBefore, ticketRepository.count());
                assertEquals(transactionsBefore, walletTransactionRepository.count());
                Wallet walletAfter = walletRepository.findByOwnerId(playerOne.getId()).orElseThrow();
                assertEquals(BigDecimal.ZERO.setScale(2), walletAfter.getAvailableBalance());
                assertEquals(BigDecimal.ZERO.setScale(2), walletAfter.getPromotionalBalance());
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

                var transitionFailure = assertThrows(ConflictException.class,
                                () -> bingoGameService.updateSessionState(transitionSession.id(),
                                                new GameSessionStateUpdateRequest(GameSessionState.OPEN)));
                assertEquals("INVALID_SESSION_STATE_TRANSITION", transitionFailure.getCode());
                assertEquals(GameSessionState.STARTED,
                                gameSessionRepository.findById(transitionSession.id()).orElseThrow().getState());

                var capacityRoom = bingoGameService.createRoom(roomRequest("Main Hall D", 1, 1, true));
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

        @Test
        void concurrentPurchasesAcrossSessionsSerializeThePlayerWallet() throws Exception {
                var firstRoom = bingoGameService.createRoom(roomRequest("Wallet lock room one", 2, 2, true));
                var secondRoom = bingoGameService.createRoom(roomRequest("Wallet lock room two", 2, 2, true));
                var firstSession = bingoGameService.createSession(firstRoom.id(), sessionRequest(GameSessionState.OPEN));
                var secondSession = bingoGameService.createSession(secondRoom.id(), sessionRequest(GameSessionState.OPEN));
                Wallet wallet = walletRepository.findByOwnerId(playerOne.getId()).orElseThrow();
                wallet.setAvailableBalance(new BigDecimal("4.00"));
                wallet.setPromotionalBalance(BigDecimal.ZERO.setScale(2));
                walletRepository.saveAndFlush(wallet);
                var player = new BingoUserPrincipal(userRepository.findById(playerOne.getId()).orElseThrow());

                ExecutorService executor = Executors.newFixedThreadPool(2);
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                Future<String> first = executor.submit(() -> purchaseAttempt(
                                firstSession.id(), player, "wallet-lock-a", ready, start));
                Future<String> second = executor.submit(() -> purchaseAttempt(
                                secondSession.id(), player, "wallet-lock-b", ready, start));
                ready.await(10, TimeUnit.SECONDS);
                start.countDown();
                List<String> outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
                executor.shutdownNow();

                assertEquals(1, outcomes.stream().filter("OK"::equals).count());
                assertEquals(1, outcomes.stream().filter("CONFLICT"::equals).count());
                assertEquals(new BigDecimal("0.00"),
                                walletRepository.findByOwnerId(playerOne.getId()).orElseThrow().getTotalBalance());
                assertEquals(1, ticketRepository.countByOwnerId(playerOne.getId()));
                assertEquals(1, walletTransactionRepository.findAll().stream().filter(transaction ->
                                transaction.getTransactionType() == WalletTransactionType.TICKET_PURCHASE_DEBIT).count());
        }

        @Test
        void inactiveRoomCannotBeScheduledForANewSession() {
                var inactiveRoom = bingoGameService.createRoom(roomRequest("Inactive room scheduling guard", 1, 1, false));

                var failure = assertThrows(ConflictException.class, () -> bingoGameService.createSession(inactiveRoom.id(),
                                sessionRequest(GameSessionState.DRAFT)));

                assertEquals("ROOM_INACTIVE", failure.getCode());
                assertTrue(gameSessionRepository.findByRoomIdOrderByScheduledStartAtDesc(inactiveRoom.id()).isEmpty());
        }

        @Test
        void roomConfigurationRejectsStaleVersionsWithoutOverwritingTheLatestValues() {
                var room = bingoGameService.createRoom(roomRequest("Versioned room configuration", 2, 2, true));
                var firstUpdate = new BingoRoomRequest(room.name(), room.description(), new BigDecimal("5.00"),
                                room.currency(), room.timezone(), room.scheduleRule(), room.sessionCapacity(),
                                room.maxTicketsPerPlayer(), room.ticketSalesCloseOnSessionStart(),
                                room.allowMultipleWinnersPerPrize(), room.active(), room.configuredPatterns(),
                                room.configuredPrizes().stream().map(prize -> new BingoPrizeConfigRequest(
                                                prize.prizeType(), prize.amount(), prize.settlementPath(), prize.maxWinners()))
                                                .toList(),
                                room.version());
                var updated = bingoGameService.updateRoom(room.id(), firstUpdate);
                assertEquals(room.version() + 1, updated.version());
                assertEquals(new BigDecimal("5.00"), updated.ticketPrice());

                var staleUpdate = new BingoRoomRequest(room.name(), room.description(), new BigDecimal("8.00"),
                                room.currency(), room.timezone(), room.scheduleRule(), room.sessionCapacity(),
                                room.maxTicketsPerPlayer(), room.ticketSalesCloseOnSessionStart(),
                                room.allowMultipleWinnersPerPrize(), room.active(), room.configuredPatterns(),
                                firstUpdate.configuredPrizes(), room.version());
                var failure = assertThrows(ConflictException.class,
                                () -> bingoGameService.updateRoom(room.id(), staleUpdate));

                assertEquals("ROOM_VERSION_CONFLICT", failure.getCode());
                var persisted = bingoGameService.getRoom(room.id());
                assertEquals(updated.version(), persisted.version());
                assertEquals(new BigDecimal("5.00"), persisted.ticketPrice());
        }

        @Test
        void roomMoneyMustFitTheTwoDecimalEtbMinorUnit() throws Exception {
                String adminToken = loginToken("0911000201", "123456");
                ObjectNode invalidRoom = (ObjectNode) objectMapper.valueToTree(roomRequest(
                                "Invalid precision room", 2, 2, true));
                invalidRoom.put("ticketPrice", new BigDecimal("1.001"));
                ((ObjectNode) invalidRoom.withArray("configuredPrizes").get(0)).put("amount", new BigDecimal("0.001"));

                mockMvc.perform(post("/api/v1/admin/rooms")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRoom)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
                assertTrue(bingoRoomRepository.findAll().stream()
                                .noneMatch(room -> room.getName().equals("Invalid precision room")));
        }

        @Test
        void concurrentRoomConfigurationUpdatesCannotSilentlyOverwriteEachOther() throws Exception {
                var room = bingoGameService.createRoom(roomRequest("Concurrent room update", 2, 2, true));
                var updateA = roomUpdateRequest(room, new BigDecimal("5.00"));
                var updateB = roomUpdateRequest(room, new BigDecimal("7.00"));
                ExecutorService executor = Executors.newFixedThreadPool(2);
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                Future<String> first = executor.submit(() -> updateRoomAttempt(room.id(), updateA, ready, start));
                Future<String> second = executor.submit(() -> updateRoomAttempt(room.id(), updateB, ready, start));
                ready.await(10, TimeUnit.SECONDS);
                start.countDown();
                List<String> outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
                executor.shutdownNow();

                assertEquals(1, outcomes.stream().filter("UPDATED"::equals).count());
                assertEquals(1, outcomes.stream().filter("CONFLICT"::equals).count());
                var persisted = bingoGameService.getRoom(room.id());
                assertEquals(1, persisted.version());
                assertTrue(persisted.ticketPrice().compareTo(new BigDecimal("5.00")) == 0
                                || persisted.ticketPrice().compareTo(new BigDecimal("7.00")) == 0);
        }

        private String updateRoomAttempt(Long roomId, BingoRoomRequest request, CountDownLatch ready,
                        CountDownLatch start) throws Exception {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                try {
                        bingoGameService.updateRoom(roomId, request);
                        return "UPDATED";
                } catch (ConflictException | OptimisticLockingFailureException exception) {
                        return "CONFLICT";
                }
        }

        private BingoRoomRequest roomUpdateRequest(com.equar.bingo.backend.feature.game.dto.BingoRoomResponse room,
                        BigDecimal ticketPrice) {
                return new BingoRoomRequest(room.name(), room.description(), ticketPrice, room.currency(),
                                room.timezone(), room.scheduleRule(), room.sessionCapacity(), room.maxTicketsPerPlayer(),
                                room.ticketSalesCloseOnSessionStart(), room.allowMultipleWinnersPerPrize(), room.active(),
                                room.configuredPatterns(), room.configuredPrizes().stream().map(prize ->
                                                new BingoPrizeConfigRequest(prize.prizeType(), prize.amount(),
                                                                prize.settlementPath(), prize.maxWinners())).toList(),
                                room.version());
        }

        @Test
        void sessionCreationCannotSkipIntoAnOperationalState() throws Exception {
                var room = bingoGameService.createRoom(roomRequest("Initial state guard room", 2, 2, true));
                String adminToken = loginToken("0911000201", "123456");
                var request = sessionRequest(GameSessionState.DRAWING);

                mockMvc.perform(post("/api/v1/admin/rooms/" + room.id() + "/sessions")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("INVALID_INITIAL_SESSION_STATE"));
                assertTrue(gameSessionRepository.findByRoomIdOrderByScheduledStartAtDesc(room.id()).isEmpty());
        }

        @Test
        void sessionCreationRejectsMissingRequiredFields() throws Exception {
                var room = bingoGameService.createRoom(roomRequest("Required session fields room", 2, 2, true));
                String adminToken = loginToken("0911000201", "123456");

                mockMvc.perform(post("/api/v1/admin/rooms/" + room.id() + "/sessions")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"sessionName\":\"\",\"state\":\"DRAFT\"}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
                assertTrue(gameSessionRepository.findByRoomIdOrderByScheduledStartAtDesc(room.id()).isEmpty());
        }

        @Test
        void nonAdminCannotChangeSessionState() throws Exception {
                var room = bingoGameService.createRoom(roomRequest("Session authorization room", 2, 2, true));
                var session = bingoGameService.createSession(room.id(), sessionRequest(GameSessionState.DRAFT));
                String playerToken = loginToken("0911000202", "123456");

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .put("/api/v1/admin/sessions/" + session.id() + "/state")
                                .header("Authorization", "Bearer " + playerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new GameSessionStateUpdateRequest(GameSessionState.OPEN))))
                                .andExpect(status().isForbidden());
                assertEquals(GameSessionState.DRAFT,
                                gameSessionRepository.findById(session.id()).orElseThrow().getState());
        }

        @Test
        void roomChangesDoNotRewriteHistoricalSessionRulesOrTicketPrice() {
                var room = bingoGameService.createRoom(roomRequest("Historical snapshot room", 5, 2, true));
                var session = bingoGameService.createSession(room.id(), sessionRequest(GameSessionState.DRAFT));
                var updatedRoom = bingoGameService.updateRoom(room.id(), roomUpdateRequest(room, new BigDecimal("9.00")));

                assertEquals(new BigDecimal("9.00"), updatedRoom.ticketPrice());
                var historical = bingoGameService.getSession(session.id());
                assertEquals(new BigDecimal("4.00"), historical.ticketPrice());
                assertEquals(List.of(BingoPatternType.LINE, BingoPatternType.TWO_LINES, BingoPatternType.FULL_HOUSE),
                                historical.configuredPatterns());
                assertEquals(0, historical.ticketCount());
        }

        @Test
        void aServerRejectedClaimCannotBeApprovedOrPaid() {
                var room = bingoGameService.createRoom(roomRequest("Unverified claim room", 5, 2, true));
                var session = bingoGameService.createSession(room.id(), sessionRequest(GameSessionState.OPEN));
                var player = new BingoUserPrincipal(userRepository.findById(playerOne.getId()).orElseThrow());
                var admin = new BingoUserPrincipal(userRepository.findById(adminUser.getId()).orElseThrow());
                var purchased = bingoGameService.purchaseTicket(session.id(), player, "unverified-claim-ticket");
                var claimResult = bingoGameService.submitWinClaim(purchased.body().ticket().id(), player,
                                new WinClaimRequest(BingoPatternType.LINE), "unverified-claim-key");
                var claim = winClaimRepository.findById(claimResult.body().id()).orElseThrow();
                assertEquals(WinClaimStatus.FAILED_SERVER_VALIDATION, claim.getStatus());
                assertTrue(!claim.isServerValidationPassed());

                var failure = assertThrows(ConflictException.class,
                                () -> bingoGameService.reviewWinClaim(claim.getId(), admin,
                                                new WinClaimReviewRequest("do not override"), true));

                assertEquals("SERVER_VALIDATION_FAILED", failure.getCode());
                assertTrue(prizeEntitlementRepository.findAll().isEmpty());
                assertTrue(walletTransactionRepository.findAll().stream().noneMatch(transaction ->
                                transaction.getTransactionType() == WalletTransactionType.PRIZE_CREDIT));
        }

        @Test
        void concurrentCreationForTheSameRoomAndStartTimeProducesOnlyOneSession() throws Exception {
                var room = bingoGameService.createRoom(roomRequest("Concurrent schedule room", 2, 2, true));
                Instant scheduledStart = Instant.now().plusSeconds(7200);
                var request = new GameSessionRequest("Concurrent schedule session", scheduledStart,
                                GameSessionState.DRAFT, null, null, null, null, null, null, null, null);
                ExecutorService executor = Executors.newFixedThreadPool(2);
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                Future<String> first = executor.submit(() -> createSessionAttempt(room.id(), request, ready, start));
                Future<String> second = executor.submit(() -> createSessionAttempt(room.id(), request, ready, start));
                ready.await(10, TimeUnit.SECONDS);
                start.countDown();
                List<String> outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
                executor.shutdownNow();

                assertEquals(1, outcomes.stream().filter("CREATED"::equals).count());
                assertEquals(1, outcomes.stream().filter("CONFLICT"::equals).count());
                assertEquals(1, gameSessionRepository.findByRoomIdOrderByScheduledStartAtDesc(room.id()).size());
        }

        private String createSessionAttempt(Long roomId, GameSessionRequest request, CountDownLatch ready,
                        CountDownLatch start) throws Exception {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                try {
                        bingoGameService.createSession(roomId, request);
                        return "CREATED";
                } catch (ConflictException exception) {
                        return "CONFLICT";
                }
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
                                                                PrizeSettlementPath.PLAYER_WALLET, 1)),
                                null);
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
