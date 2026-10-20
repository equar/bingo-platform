package com.equar.bingo.backend.feature.wallet;

import com.equar.bingo.backend.common.idempotency.IdempotentResult;
import com.equar.bingo.backend.feature.auth.dto.LoginRequest;
import com.equar.bingo.backend.feature.auth.dto.AdminLoginRequest;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.game.repository.BingoRoomRepository;
import com.equar.bingo.backend.feature.game.repository.GameSessionRepository;
import com.equar.bingo.backend.feature.game.repository.TicketRepository;
import com.equar.bingo.backend.feature.game.repository.DrawEventRepository;
import com.equar.bingo.backend.feature.game.repository.WinClaimRepository;
import com.equar.bingo.backend.feature.game.repository.PrizeEntitlementRepository;
import com.equar.bingo.backend.feature.auth.dto.RegisterRequest;
import com.equar.bingo.backend.feature.wallet.dto.DepositClaimRequest;
import com.equar.bingo.backend.feature.wallet.dto.DepositClaimResponse;
import com.equar.bingo.backend.feature.wallet.dto.DepositReviewRequest;
import com.equar.bingo.backend.feature.wallet.dto.WalletAdjustmentRequest;
import com.equar.bingo.backend.feature.wallet.entity.DepositClaim;
import com.equar.bingo.backend.feature.wallet.entity.Wallet;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransaction;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransactionType;
import com.equar.bingo.backend.feature.wallet.entity.SignupRewardPolicy;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransferPolicy;
import com.equar.bingo.backend.feature.agent.repository.PayoutVerificationRecordRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentFloatTransactionRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentFloatRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentRepository;
import com.equar.bingo.backend.feature.wallet.repository.DepositClaimRepository;
import com.equar.bingo.backend.feature.wallet.repository.IdempotencyRecordRepository;
import com.equar.bingo.backend.feature.wallet.repository.SignupRewardGrantRepository;
import com.equar.bingo.backend.feature.wallet.repository.SignupRewardPolicyRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletTransferPolicyRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletTransferRepository;
import com.equar.bingo.backend.feature.wallet.service.WalletService;
import com.equar.bingo.backend.feature.support.repository.InternalNoteRepository;
import com.equar.bingo.backend.feature.support.repository.SupportMessageRepository;
import com.equar.bingo.backend.feature.support.repository.SupportTicketRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WalletIntegrationTest {

        @Autowired
        MockMvc mockMvc;

        @Autowired
        ObjectMapper objectMapper;

        @Autowired
        UserRepository userRepository;

        @Autowired
        WalletRepository walletRepository;

        @Autowired
        WalletTransactionRepository walletTransactionRepository;

        @Autowired
        SignupRewardPolicyRepository signupRewardPolicyRepository;

        @Autowired
        SignupRewardGrantRepository signupRewardGrantRepository;

        @Autowired
        WalletTransferPolicyRepository walletTransferPolicyRepository;

        @Autowired
        WalletTransferRepository walletTransferRepository;

        @Autowired
        DepositClaimRepository depositClaimRepository;

        @Autowired
        IdempotencyRecordRepository idempotencyRecordRepository;

        @Autowired
        PasswordEncoder passwordEncoder;

        @Autowired
        EthiopianPhoneNormalizer phoneNormalizer;

        @Autowired
        WalletService walletService;

        @Autowired
        SupportTicketRepository supportTicketRepository;

        @Autowired
        SupportMessageRepository supportMessageRepository;

        @Autowired
        InternalNoteRepository internalNoteRepository;

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
        PayoutVerificationRecordRepository payoutVerificationRecordRepository;

        @Autowired
        AgentFloatTransactionRepository agentFloatTransactionRepository;

        @Autowired
        AgentFloatRepository agentFloatRepository;

        @Autowired
        AgentRepository agentRepository;

        private User adminUser;
        private User playerUser;

        @BeforeEach
        void setUp() {
                jdbcTemplate.update("delete from audit_log_entries");
                internalNoteRepository.deleteAllInBatch();
                supportMessageRepository.deleteAllInBatch();
                supportTicketRepository.deleteAllInBatch();
                payoutVerificationRecordRepository.deleteAll();
                agentFloatTransactionRepository.deleteAll();
                agentFloatRepository.deleteAll();
                agentRepository.deleteAll();
                prizeEntitlementRepository.deleteAll();
                winClaimRepository.deleteAll();
                drawEventRepository.deleteAll();
                ticketRepository.deleteAll();
                gameSessionRepository.deleteAll();
                bingoRoomRepository.deleteAll();
                walletTransactionRepository.deleteAll();
                signupRewardGrantRepository.deleteAll();
                signupRewardPolicyRepository.deleteAll();
                walletTransferRepository.deleteAll();
                walletTransferPolicyRepository.deleteAll();
                depositClaimRepository.deleteAll();
                walletRepository.deleteAll();
                idempotencyRecordRepository.deleteAll();
                userRepository.deleteAll();

                Instant now = Instant.now();
                adminUser = userRepository.save(User.create(
                                phoneNormalizer.normalize("0911000101"),
                                "Admin User",
                                passwordEncoder.encode("AdminPass123!"),
                                AccountStatus.ACTIVE,
                                Set.of(UserRole.ADMIN),
                                now));
                adminUser.setEmail("admin@example.com");
                adminUser = userRepository.save(adminUser);

                playerUser = userRepository.save(User.create(
                                phoneNormalizer.normalize("0911000102"),
                                "Player User",
                                passwordEncoder.encode("123456"),
                                AccountStatus.ACTIVE,
                                Set.of(UserRole.PLAYER),
                                now));

                walletService.createWalletIfMissing(playerUser);
                signupRewardPolicyRepository.save(SignupRewardPolicy.create(true, new BigDecimal("25.00"), "ETB",
                                now.minusSeconds(60), now.plusSeconds(86400), adminUser, "Welcome reward", now));
                walletTransferPolicyRepository.save(WalletTransferPolicy.create(true, new BigDecimal("1000.00"),
                                new BigDecimal("5000.00"), "ETB", now.minusSeconds(60), now.plusSeconds(86400),
                                adminUser, "Enable transfers", now));
        }

        @Test
        void playerDepositIdempotencyAndAdminApprovalFlowWork() throws Exception {
                String playerToken = loginToken("0911000102", "123456");
                String adminToken = adminLoginToken("admin@example.com", "AdminPass123!");

                DepositClaimRequest request = new DepositClaimRequest(new BigDecimal("100.00"), "ETB", "TBR-123 456");
                String requestJson = objectMapper.writeValueAsString(request);

                String createResponse = mockMvc.perform(post("/api/v1/wallet/deposits")
                                .header("Authorization", "Bearer " + playerToken)
                                .header("Idempotency-Key", "deposit-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status").value("PENDING"))
                                .andExpect(jsonPath("$.paymentInstructions.providerName").value("Telebirr"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                JsonNode created = objectMapper.readTree(createResponse);
                Long depositId = created.get("id").asLong();

                String repeatResponse = mockMvc.perform(post("/api/v1/wallet/deposits")
                                .header("Authorization", "Bearer " + playerToken)
                                .header("Idempotency-Key", "deposit-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                JsonNode repeated = objectMapper.readTree(repeatResponse);
                assertEquals(depositId, repeated.get("id").asLong());
                assertEquals(1L, depositClaimRepository.count());
                assertEquals(1L, idempotencyRecordRepository.count());

                mockMvc.perform(get("/api/v1/wallet/balance")
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.currency").value("ETB"))
                                .andExpect(jsonPath("$.availableBalance").value(0));

                String approveResponse = mockMvc.perform(post("/api/v1/admin/deposits/" + depositId + "/approve")
                                .header("Authorization", "Bearer " + adminToken)
                                .header("Idempotency-Key", "approve-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new DepositReviewRequest("Verified"))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("APPROVED"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                JsonNode approved = objectMapper.readTree(approveResponse);
                assertEquals("Verified", approved.get("reviewNote").asText());

                mockMvc.perform(get("/api/v1/wallet/balance")
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.availableBalance").value(100));

                mockMvc.perform(get("/api/v1/wallet/transactions")
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].transactionType")
                                                .value(WalletTransactionType.DEPOSIT_CREDIT.name()))
                                .andExpect(jsonPath("$.totalElements").value(1));

                Wallet wallet = walletRepository.findByOwnerId(playerUser.getId()).orElseThrow();
                assertEquals(new BigDecimal("100.00"), wallet.getAvailableBalance());
        }

        @Test
        void duplicateTelebirrReferenceIsRejected() throws Exception {
                String playerToken = loginToken("0911000102", "123456");
                DepositClaimRequest first = new DepositClaimRequest(new BigDecimal("50.00"), "ETB", "TBR-DUP-001");
                DepositClaimRequest second = new DepositClaimRequest(new BigDecimal("50.00"), "ETB", "TBR-DUP-001");

                mockMvc.perform(post("/api/v1/wallet/deposits")
                                .header("Authorization", "Bearer " + playerToken)
                                .header("Idempotency-Key", "deposit-a")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(first)))
                                .andExpect(status().isCreated());

                mockMvc.perform(post("/api/v1/wallet/deposits")
                                .header("Authorization", "Bearer " + playerToken)
                                .header("Idempotency-Key", "deposit-b")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(second)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("DUPLICATE_TELEBIRR_REFERENCE"));
        }

        @Test
        void insufficientAdjustmentRollsBackWithoutChangingBalance() throws Exception {
                String adminToken = adminLoginToken("admin@example.com", "AdminPass123!");

                mockMvc.perform(post("/api/v1/admin/players/" + playerUser.getId() + "/wallet-adjustments")
                                .header("Authorization", "Bearer " + adminToken)
                                .header("Idempotency-Key", "adjust-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new WalletAdjustmentRequest(
                                                new BigDecimal("-25.00"),
                                                "ETB",
                                                "test rollback"))))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));

                Wallet wallet = walletRepository.findByOwnerId(playerUser.getId()).orElseThrow();
                assertEquals(new BigDecimal("0.00"), wallet.getAvailableBalance());
                assertEquals(0L, walletTransactionRepository.count());
        }

        @Test
        void concurrentApprovalCreditsOnlyOnce() throws Exception {
                User admin = userRepository.findById(adminUser.getId()).orElseThrow();
                User player = userRepository.findById(playerUser.getId()).orElseThrow();
                BingoUserPrincipal adminPrincipal = new BingoUserPrincipal(admin);
                BingoUserPrincipal playerPrincipal = new BingoUserPrincipal(player);

                IdempotentResult<DepositClaimResponse> created = walletService.submitDeposit(
                                playerPrincipal,
                                new DepositClaimRequest(new BigDecimal("80.00"), "ETB", "TBR-CONCURRENT-001"),
                                "deposit-concurrent");
                Long depositId = created.body().id();

                ExecutorService executorService = Executors.newFixedThreadPool(2);
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);

                Callable<String> firstAttempt = () -> approveInParallel(adminPrincipal, depositId, ready, start,
                                "approve-a");
                Callable<String> secondAttempt = () -> approveInParallel(adminPrincipal, depositId, ready, start,
                                "approve-b");

                Future<String> first = executorService.submit(firstAttempt);
                Future<String> second = executorService.submit(secondAttempt);

                ready.await(10, TimeUnit.SECONDS);
                start.countDown();

                String firstResult = first.get(10, TimeUnit.SECONDS);
                String secondResult = second.get(10, TimeUnit.SECONDS);
                executorService.shutdownNow();

                assertTrue(List.of(firstResult, secondResult).contains("OK"));
                assertTrue(List.of(firstResult, secondResult).contains("CONFLICT"));

                Wallet wallet = walletRepository.findByOwnerId(playerUser.getId()).orElseThrow();
                assertEquals(new BigDecimal("80.00"), wallet.getAvailableBalance());
                assertEquals(1L, walletTransactionRepository.count());
        }

        @Test
        void signupRewardIsGrantedOnRegistrationAndRecordedOnce() throws Exception {
                String phone = "0911000103";

                mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new RegisterRequest("New Player", phone, "123456"))))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.tokenType").value("Bearer"));

                User newPlayer = userRepository.findByNormalizedPhone(phoneNormalizer.normalize(phone)).orElseThrow();
                Wallet wallet = walletRepository.findByOwnerId(newPlayer.getId()).orElseThrow();
                assertEquals(new BigDecimal("25.00"), wallet.getPromotionalBalance());
                assertEquals(new BigDecimal("25.00"), wallet.getTotalBalance());
                assertEquals(1L, signupRewardGrantRepository.count());
                assertEquals(1L, walletTransactionRepository.count());
                assertEquals(WalletTransactionType.SIGNUP_REWARD_CREDIT,
                                walletTransactionRepository.findAll().get(0).getTransactionType());
        }

        @Test
        void playerTransferUsesIdempotencyAndMovesTransferableBalance() throws Exception {
                String senderToken = loginToken("0911000102", "123456");
                User recipient = userRepository.save(User.create(
                                phoneNormalizer.normalize("0911000104"),
                                "Recipient User",
                                passwordEncoder.encode("123456"),
                                AccountStatus.ACTIVE,
                                Set.of(UserRole.PLAYER),
                                Instant.now()));
                walletService.createWalletIfMissing(recipient);

                Wallet senderWallet = walletRepository.findByOwnerId(playerUser.getId()).orElseThrow();
                senderWallet.setAvailableBalance(new BigDecimal("100.00"));
                walletRepository.save(senderWallet);

                String requestJson = objectMapper.writeValueAsString(
                                new com.equar.bingo.backend.feature.wallet.dto.WalletTransferRequest(
                                                "0911000104", new BigDecimal("30.00"), "ETB", "Dinner split"));

                String firstResponse = mockMvc.perform(post("/api/v1/wallet/transfers")
                                .header("Authorization", "Bearer " + senderToken)
                                .header("Idempotency-Key", "transfer-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("COMPLETED"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                String secondResponse = mockMvc.perform(post("/api/v1/wallet/transfers")
                                .header("Authorization", "Bearer " + senderToken)
                                .header("Idempotency-Key", "transfer-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                JsonNode first = objectMapper.readTree(firstResponse);
                JsonNode second = objectMapper.readTree(secondResponse);
                assertEquals(first.get("operationId").asText(), second.get("operationId").asText());
                assertEquals(1L, walletTransferRepository.count());
                assertEquals(2L, walletTransactionRepository.count());

                Wallet senderAfter = walletRepository.findByOwnerId(playerUser.getId()).orElseThrow();
                Wallet recipientAfter = walletRepository.findByOwnerId(recipient.getId()).orElseThrow();
                assertEquals(new BigDecimal("70.00"), senderAfter.getAvailableBalance());
                assertEquals(new BigDecimal("30.00"), recipientAfter.getAvailableBalance());
        }

        private String approveInParallel(BingoUserPrincipal adminPrincipal,
                        Long depositId,
                        CountDownLatch ready,
                        CountDownLatch start,
                        String idempotencyKey) throws Exception {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                try {
                        walletService.approveDeposit(depositId, adminPrincipal,
                                        new DepositReviewRequest("Concurrent review"),
                                        idempotencyKey);
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

        private String adminLoginToken(String email, String password) throws Exception {
                String response = mockMvc.perform(post("/api/v1/admin/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new AdminLoginRequest(email, password))))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                return objectMapper.readTree(response).get("accessToken").asText();
        }
}