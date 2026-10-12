package com.equar.bingo.backend.feature.wallet;

import com.equar.bingo.backend.common.idempotency.IdempotentResult;
import com.equar.bingo.backend.feature.auth.dto.LoginRequest;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.wallet.dto.DepositClaimRequest;
import com.equar.bingo.backend.feature.wallet.dto.DepositClaimResponse;
import com.equar.bingo.backend.feature.wallet.dto.DepositReviewRequest;
import com.equar.bingo.backend.feature.wallet.dto.WalletAdjustmentRequest;
import com.equar.bingo.backend.feature.wallet.entity.DepositClaim;
import com.equar.bingo.backend.feature.wallet.entity.Wallet;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransaction;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransactionType;
import com.equar.bingo.backend.feature.wallet.repository.DepositClaimRepository;
import com.equar.bingo.backend.feature.wallet.repository.IdempotencyRecordRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import com.equar.bingo.backend.feature.wallet.service.WalletService;
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
    DepositClaimRepository depositClaimRepository;

    @Autowired
    IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    EthiopianPhoneNormalizer phoneNormalizer;

    @Autowired
    WalletService walletService;

    private User adminUser;
    private User playerUser;

    @BeforeEach
    void setUp() {
        walletTransactionRepository.deleteAll();
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

        playerUser = userRepository.save(User.create(
                phoneNormalizer.normalize("0911000102"),
                "Player User",
                passwordEncoder.encode("PlayerPass123!"),
                AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER),
                now));

        walletService.createWalletIfMissing(playerUser);
    }

    @Test
    void playerDepositIdempotencyAndAdminApprovalFlowWork() throws Exception {
        String playerToken = loginToken("0911000102", "PlayerPass123!");
        String adminToken = loginToken("0911000101", "AdminPass123!");

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
                .andExpect(jsonPath("$.content[0].transactionType").value(WalletTransactionType.DEPOSIT_CREDIT.name()))
                .andExpect(jsonPath("$.totalElements").value(1));

        Wallet wallet = walletRepository.findByOwnerId(playerUser.getId()).orElseThrow();
        assertEquals(new BigDecimal("100.00"), wallet.getAvailableBalance());
    }

    @Test
    void duplicateTelebirrReferenceIsRejected() throws Exception {
        String playerToken = loginToken("0911000102", "PlayerPass123!");
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
        String adminToken = loginToken("0911000101", "AdminPass123!");

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

        Callable<String> firstAttempt = () -> approveInParallel(adminPrincipal, depositId, ready, start, "approve-a");
        Callable<String> secondAttempt = () -> approveInParallel(adminPrincipal, depositId, ready, start, "approve-b");

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

    private String approveInParallel(BingoUserPrincipal adminPrincipal,
            Long depositId,
            CountDownLatch ready,
            CountDownLatch start,
            String idempotencyKey) throws Exception {
        ready.countDown();
        start.await(10, TimeUnit.SECONDS);
        try {
            walletService.approveDeposit(depositId, adminPrincipal, new DepositReviewRequest("Concurrent review"),
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
}