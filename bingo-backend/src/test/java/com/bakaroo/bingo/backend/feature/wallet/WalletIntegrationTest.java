package com.bakaroo.bingo.backend.feature.wallet;

import com.bakaroo.bingo.backend.MySqlIntegrationTest;
import com.bakaroo.bingo.backend.common.idempotency.IdempotentResult;
import com.bakaroo.bingo.backend.feature.auth.dto.LoginRequest;
import com.bakaroo.bingo.backend.feature.auth.dto.AdminLoginRequest;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.bakaroo.bingo.backend.feature.game.repository.BingoRoomRepository;
import com.bakaroo.bingo.backend.feature.game.repository.GameSessionRepository;
import com.bakaroo.bingo.backend.feature.game.repository.TicketRepository;
import com.bakaroo.bingo.backend.feature.game.repository.DrawEventRepository;
import com.bakaroo.bingo.backend.feature.game.repository.WinClaimRepository;
import com.bakaroo.bingo.backend.feature.game.repository.PrizeEntitlementRepository;
import com.bakaroo.bingo.backend.feature.auth.dto.RegisterRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.DepositClaimRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.DepositClaimResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.DepositReviewRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.DepositVerificationRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletAdjustmentRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletAdjustmentReviewRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletAdjustmentSubmissionRequest;
import com.bakaroo.bingo.backend.feature.wallet.entity.DepositClaim;
import com.bakaroo.bingo.backend.feature.wallet.entity.DepositClaimStatus;
import com.bakaroo.bingo.backend.feature.wallet.entity.Wallet;
import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransaction;
import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransactionType;
import com.bakaroo.bingo.backend.feature.wallet.entity.SignupRewardPolicy;
import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransferPolicy;
import com.bakaroo.bingo.backend.feature.agent.repository.PayoutVerificationRecordRepository;
import com.bakaroo.bingo.backend.feature.agent.repository.AgentFloatTransactionRepository;
import com.bakaroo.bingo.backend.feature.agent.repository.AgentFloatRepository;
import com.bakaroo.bingo.backend.feature.agent.repository.AgentRepository;
import com.bakaroo.bingo.backend.feature.agent.entity.Agent;
import com.bakaroo.bingo.backend.feature.agent.entity.AgentStatus;
import com.bakaroo.bingo.backend.feature.agent.dto.AgentLoginRequest;
import com.bakaroo.bingo.backend.feature.wallet.repository.DepositClaimRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.IdempotencyRecordRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.SignupRewardGrantRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.SignupRewardPolicyRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletTransferPolicyRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletTransferRepository;
import com.bakaroo.bingo.backend.feature.wallet.service.WalletService;
import com.bakaroo.bingo.backend.feature.wallet.service.DepositFinancialService;
import com.bakaroo.bingo.backend.feature.wallet.service.WalletAdjustmentService;
import com.bakaroo.bingo.backend.feature.wallet.service.SignupRewardService;
import com.bakaroo.bingo.backend.feature.support.repository.InternalNoteRepository;
import com.bakaroo.bingo.backend.feature.support.repository.SupportMessageRepository;
import com.bakaroo.bingo.backend.feature.support.repository.SupportTicketRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
class WalletIntegrationTest extends MySqlIntegrationTest {

        @Autowired
        MockMvc mockMvc;

        @Autowired
        ObjectMapper objectMapper;

        @Autowired
        JdbcTemplate jdbcTemplate;

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
        DepositFinancialService depositFinancialService;

        @Autowired
        WalletAdjustmentService walletAdjustmentService;

        @Autowired
        SignupRewardService signupRewardService;

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

        @Autowired
        @org.springframework.beans.factory.annotation.Qualifier("agentPinEncoder")
        PasswordEncoder agentPinEncoder;

        private User adminUser;
        private User playerUser;

        @BeforeEach
        void setUp() {
                jdbcTemplate.update("delete from audit_log_entries");
                jdbcTemplate.update("delete from float_accounting_journal_lines");
                jdbcTemplate.update("delete from float_accounting_journals");
                jdbcTemplate.update("delete from float_top_up_verification_attempts");
                jdbcTemplate.update("delete from deposit_accounting_journal_lines");
                jdbcTemplate.update("delete from deposit_accounting_journals");
                jdbcTemplate.update("delete from wallet_adjustment_accounting_journal_lines");
                jdbcTemplate.update("delete from wallet_adjustment_accounting_journals");
                jdbcTemplate.update("delete from wallet_adjustment_requests");
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
        void playerDepositSubmissionNeverCreditsWalletWithoutIndependentVerification() throws Exception {
                adminUser.setRoles(Set.of(UserRole.ADMIN, UserRole.FINANCE_APPROVER));
                userRepository.save(adminUser);
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

                Wallet walletBeforeDeposit = walletRepository.findByOwnerId(playerUser.getId()).orElseThrow();
                walletBeforeDeposit.setPromotionalBalance(new BigDecimal("12.00"));
                walletRepository.save(walletBeforeDeposit);

                mockMvc.perform(post("/api/v1/admin/deposits/" + depositId + "/approve")
                                .header("Authorization", "Bearer " + adminToken)
                                .header("Idempotency-Key", "approve-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new DepositReviewRequest("Verified"))))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("DEPOSIT_NOT_VERIFIED"));

                mockMvc.perform(get("/api/v1/wallet/balance")
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.availableBalance").value(0));

                mockMvc.perform(get("/api/v1/wallet/transactions")
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalElements").value(0));

                Wallet wallet = walletRepository.findByOwnerId(playerUser.getId()).orElseThrow();
                assertEquals(new BigDecimal("0.00"), wallet.getAvailableBalance());
                assertEquals(DepositClaimStatus.PENDING,
                                depositClaimRepository.findById(depositId).orElseThrow().getStatus());
                assertEquals(0L, walletTransactionRepository.count());
        }

        @Test
        void malformedDepositRequestsReturnStructuredBadRequests() throws Exception {
                String playerToken = loginToken("0911000102", "123456");
                String validRequest = objectMapper.writeValueAsString(
                                new DepositClaimRequest(new BigDecimal("20.00"), "ETB", "TBR-400-TEST"));

                mockMvc.perform(post("/api/v1/wallet/deposits")
                                .header("Authorization", "Bearer " + playerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_HEADER"))
                                .andExpect(jsonPath("$.status").value(400))
                                .andExpect(jsonPath("$.path").value("/api/v1/wallet/deposits"))
                                .andExpect(jsonPath("$.details[0].field").value("Idempotency-Key"));

                mockMvc.perform(post("/api/v1/wallet/deposits")
                                .header("Authorization", "Bearer " + playerToken)
                                .header("Idempotency-Key", "malformed-body")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));

                mockMvc.perform(post("/api/v1/wallet/deposits")
                                .header("Authorization", "Bearer " + playerToken)
                                .header("Idempotency-Key", "missing-body")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

                mockMvc.perform(post("/api/v1/wallet/deposits")
                                .header("Authorization", "Bearer " + playerToken)
                                .header("Idempotency-Key", "invalid-body")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"submittedCashAmount\":0,\"currency\":\"ETB\",\"telebirrReference\":\"TBR-400-INVALID\"}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
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
                                .andExpect(jsonPath("$.code").value("WALLET_ADJUSTMENT_MAKER_CHECKER_REQUIRED"));

                Wallet wallet = walletRepository.findByOwnerId(playerUser.getId()).orElseThrow();
                assertEquals(new BigDecimal("0.00"), wallet.getAvailableBalance());
                assertEquals(0L, walletTransactionRepository.count());
        }

        @Test
        void verifiedDepositPostsBalancedJournalOnceWithSeparatedFinanceUsers() {
                User verifier = financeUser("0911000103", Set.of(UserRole.ADMIN, UserRole.FINANCE_VERIFIER));
                User approver = financeUser("0911000104", Set.of(UserRole.ADMIN, UserRole.FINANCE_APPROVER));
                BingoUserPrincipal playerPrincipal = new BingoUserPrincipal(playerUser);
                IdempotentResult<DepositClaimResponse> submitted = walletService.submitDeposit(playerPrincipal,
                                new DepositClaimRequest(new BigDecimal("60.00"), "ETB", "TBR-VERIFIED-001"),
                                "verified-deposit");
                Long depositId = submitted.body().id();

                jdbcTemplate.update("""
                                insert into float_receiving_bank_accounts
                                (bank_name, account_number_ciphertext, masked_account_number, account_holder_name,
                                 currency, active, player_deposits_enabled, created_by_user_id, updated_by_user_id,
                                 created_at, updated_at)
                                values ('QA Synthetic Bank', 'local-test-ciphertext', '****2002', 'QA Account',
                                 'ETB', true, true, ?, ?, current_timestamp(6), current_timestamp(6))
                                """, adminUser.getId(), adminUser.getId());
                Long bankAccountId = jdbcTemplate.queryForObject(
                                "select id from float_receiving_bank_accounts where masked_account_number='****2002'",
                                Long.class);
                jdbcTemplate.update("""
                                insert into float_bank_statement_entries
                                (receiving_bank_account_id, transaction_reference, amount, currency, transaction_date,
                                 settlement_state, source, synthetic, recorded_by_user_id, recorded_at)
                                values (?, 'TBR-VERIFIED-001', 60.00, 'ETB', ?, 'SETTLED',
                                 'SYNTHETIC_QA_BANK_STATEMENT', true, ?, current_timestamp(6))
                                """, bankAccountId, LocalDate.now(), verifier.getId());
                Long statementEntryId = jdbcTemplate.queryForObject(
                                "select id from float_bank_statement_entries where transaction_reference='TBR-VERIFIED-001'",
                                Long.class);

                var verification = depositFinancialService.verify(depositId, new BingoUserPrincipal(verifier),
                                new DepositVerificationRequest(statementEntryId));
                assertEquals("MATCHED", verification.outcome());
                IdempotentResult<DepositClaimResponse> approved = depositFinancialService.approve(depositId,
                                new BingoUserPrincipal(approver), "QA synthetic approval", "approve-verified-1");
                assertEquals("APPROVED", approved.body().status());
                assertEquals(verifier.getId(), approved.body().verifiedBy());

                Wallet wallet = walletRepository.findByOwnerId(playerUser.getId()).orElseThrow();
                assertEquals(new BigDecimal("60.00"), wallet.getAvailableBalance());
                assertEquals(1L, walletTransactionRepository.count());
                assertEquals(1L, jdbcTemplate.queryForObject(
                                "select count(*) from deposit_accounting_journals where deposit_claim_id=?",
                                Long.class, depositId));
                BigDecimal debitTotal = jdbcTemplate.queryForObject("""
                                select sum(debit_amount) from deposit_accounting_journal_lines l
                                join deposit_accounting_journals j on j.id=l.journal_id
                                where j.deposit_claim_id=?
                                """, BigDecimal.class, depositId);
                assertEquals(0, new BigDecimal("60.00").compareTo(debitTotal));
                IdempotentResult<DepositClaimResponse> retry = depositFinancialService.approve(depositId,
                                new BingoUserPrincipal(approver), "QA synthetic approval", "approve-verified-1");
                assertEquals(approved.body().operationId(), retry.body().operationId());
                assertEquals(new BigDecimal("60.00"),
                                walletRepository.findByOwnerId(playerUser.getId()).orElseThrow().getAvailableBalance());
                assertEquals(1L, walletTransactionRepository.count());
        }

        @Test
        void duplicateEvidenceAndAmountMismatchNeverCreateDepositPostings() {
                User verifier = financeUser("0911000103", Set.of(UserRole.ADMIN, UserRole.FINANCE_VERIFIER));
                User approver = financeUser("0911000104", Set.of(UserRole.ADMIN, UserRole.FINANCE_APPROVER));
                Long bankAccountId;
                jdbcTemplate.update("""
                                insert into float_receiving_bank_accounts
                                (bank_name, account_number_ciphertext, masked_account_number, account_holder_name,
                                 currency, active, player_deposits_enabled, created_by_user_id, updated_by_user_id,
                                 created_at, updated_at)
                                values ('QA Synthetic Bank', 'local-test-ciphertext', '****2002', 'QA Account',
                                 'ETB', true, true, ?, ?, current_timestamp(6), current_timestamp(6))
                                """, adminUser.getId(), adminUser.getId());
                bankAccountId = jdbcTemplate.queryForObject(
                                "select id from float_receiving_bank_accounts where masked_account_number='****2002'",
                                Long.class);

                Long firstId = walletService.submitDeposit(new BingoUserPrincipal(playerUser),
                                new DepositClaimRequest(new BigDecimal("2.50"), "ETB", "TBR-DUPLICATE-EVIDENCE"),
                                "duplicate-evidence-first").body().id();
                Long secondId = walletService.submitDeposit(new BingoUserPrincipal(playerUser),
                                new DepositClaimRequest(new BigDecimal("2.50"), "ETB", "TBR-DUPLICATE-EVIDENCE-2"),
                                "duplicate-evidence-second").body().id();
                jdbcTemplate.update("""
                                insert into float_bank_statement_entries
                                (receiving_bank_account_id, transaction_reference, amount, currency, transaction_date,
                                 settlement_state, source, synthetic, recorded_by_user_id, recorded_at)
                                values (?, 'TBR-DUPLICATE-EVIDENCE', 2.50, 'ETB', ?, 'SETTLED',
                                 'SYNTHETIC_QA_BANK_STATEMENT', true, ?, current_timestamp(6))
                                """, bankAccountId, LocalDate.now(), verifier.getId());
                Long duplicateStatementId = jdbcTemplate.queryForObject(
                                "select id from float_bank_statement_entries where transaction_reference='TBR-DUPLICATE-EVIDENCE'",
                                Long.class);
                assertEquals("MATCHED", depositFinancialService.verify(firstId, new BingoUserPrincipal(verifier),
                                new DepositVerificationRequest(duplicateStatementId)).outcome());
                var duplicateEvidence = org.junit.jupiter.api.Assertions.assertThrows(
                                com.bakaroo.bingo.backend.common.exception.ConflictException.class,
                                () -> depositFinancialService.verify(secondId, new BingoUserPrincipal(verifier),
                                                new DepositVerificationRequest(duplicateStatementId)));
                assertEquals("BANK_STATEMENT_ALREADY_USED", duplicateEvidence.getCode());
                assertEquals(DepositClaimStatus.PENDING,
                                depositClaimRepository.findById(secondId).orElseThrow().getStatus());

                Long mismatchId = walletService.submitDeposit(new BingoUserPrincipal(playerUser),
                                new DepositClaimRequest(new BigDecimal("3.00"), "ETB", "TBR-AMOUNT-MISMATCH"),
                                "amount-mismatch-deposit").body().id();
                jdbcTemplate.update("""
                                insert into float_bank_statement_entries
                                (receiving_bank_account_id, transaction_reference, amount, currency, transaction_date,
                                 settlement_state, source, synthetic, recorded_by_user_id, recorded_at)
                                values (?, 'TBR-AMOUNT-MISMATCH', 2.99, 'ETB', ?, 'SETTLED',
                                 'SYNTHETIC_QA_BANK_STATEMENT', true, ?, current_timestamp(6))
                                """, bankAccountId, LocalDate.now(), verifier.getId());
                Long mismatchStatementId = jdbcTemplate.queryForObject(
                                "select id from float_bank_statement_entries where transaction_reference='TBR-AMOUNT-MISMATCH'",
                                Long.class);
                assertEquals("AMOUNT_MISMATCH", depositFinancialService.verify(mismatchId,
                                new BingoUserPrincipal(verifier), new DepositVerificationRequest(mismatchStatementId))
                                .outcome());
                assertEquals(DepositClaimStatus.VERIFICATION_EXCEPTION,
                                depositClaimRepository.findById(mismatchId).orElseThrow().getStatus());
                var mismatchApproval = org.junit.jupiter.api.Assertions.assertThrows(
                                com.bakaroo.bingo.backend.common.exception.ConflictException.class,
                                () -> depositFinancialService.approve(mismatchId, new BingoUserPrincipal(approver),
                                                "Receipt does not match cleared funds", "approve-mismatch"));
                assertEquals("DEPOSIT_NOT_VERIFIED", mismatchApproval.getCode());
                assertEquals(new BigDecimal("0.00"),
                                walletRepository.findByOwnerId(playerUser.getId()).orElseThrow()
                                                .getAvailableBalance());
                assertEquals(0L, walletTransactionRepository.count());
                assertEquals(0L, jdbcTemplate.queryForObject("select count(*) from deposit_accounting_journals",
                                Long.class));
        }

        @Test
        void walletAdjustmentRequiresDistinctCheckerAndPostsAtMostOnce() {
                User maker = financeUser("0911000103",
                                Set.of(UserRole.ADMIN, UserRole.FINANCE_VERIFIER, UserRole.FINANCE_APPROVER));
                User checker = financeUser("0911000104", Set.of(UserRole.ADMIN, UserRole.FINANCE_APPROVER));
                var request = walletAdjustmentService.submit(playerUser.getId(), new BingoUserPrincipal(maker),
                                new WalletAdjustmentSubmissionRequest(new BigDecimal("15.00"), "ETB",
                                                "Approved QA adjustment", "CASE-02D-15"));

                var separationFailure = org.junit.jupiter.api.Assertions.assertThrows(
                                com.bakaroo.bingo.backend.common.exception.ConflictException.class,
                                () -> walletAdjustmentService.approve(request.id(), new BingoUserPrincipal(maker),
                                                new WalletAdjustmentReviewRequest("Self approval")));
                assertEquals("WALLET_ADJUSTMENT_SEPARATION_OF_DUTIES_REQUIRED", separationFailure.getCode());
                var posted = walletAdjustmentService.approve(request.id(), new BingoUserPrincipal(checker),
                                new WalletAdjustmentReviewRequest("Independent check"));
                assertEquals("APPROVED", posted.status());
                assertTrue(posted.simulated());
                assertEquals(new BigDecimal("15.00"), posted.balanceAfter());
                var retry = walletAdjustmentService.approve(request.id(), new BingoUserPrincipal(checker),
                                new WalletAdjustmentReviewRequest("Independent check"));
                assertEquals(posted.journalId(), retry.journalId());
                assertEquals(new BigDecimal("15.00"),
                                walletRepository.findByOwnerId(playerUser.getId()).orElseThrow().getAvailableBalance());
                assertEquals(1L, walletTransactionRepository.count());
                assertEquals(1L, jdbcTemplate.queryForObject("""
                                select count(*) from wallet_adjustment_accounting_journals
                                where adjustment_request_id=?
                                """, Long.class, request.id()));

                var excessiveDebit = walletAdjustmentService.submit(playerUser.getId(),
                                new BingoUserPrincipal(maker),
                                new WalletAdjustmentSubmissionRequest(new BigDecimal("-20.00"), "ETB",
                                                "Insufficient funds test", "CASE-02D-NEGATIVE"));
                var insufficientFunds = org.junit.jupiter.api.Assertions.assertThrows(
                                com.bakaroo.bingo.backend.common.exception.ConflictException.class,
                                () -> walletAdjustmentService.approve(excessiveDebit.id(),
                                                new BingoUserPrincipal(checker),
                                                new WalletAdjustmentReviewRequest("Insufficient balance")));
                assertEquals("INSUFFICIENT_BALANCE", insufficientFunds.getCode());
                assertEquals("PENDING", walletAdjustmentService.history(playerUser.getId()).stream()
                                .filter(item -> item.id().equals(excessiveDebit.id())).findFirst().orElseThrow()
                                .status());
                assertEquals(new BigDecimal("15.00"),
                                walletRepository.findByOwnerId(playerUser.getId()).orElseThrow().getAvailableBalance());
                assertEquals(1L, walletTransactionRepository.count());
        }

        @Test
        void legacyConcurrentApprovalAttemptsNeverCreditUnverifiedDeposits() throws Exception {
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

                assertEquals(2L, List.of(firstResult, secondResult).stream()
                                .filter("CONFLICT"::equals).count());

                Wallet wallet = walletRepository.findByOwnerId(playerUser.getId()).orElseThrow();
                assertEquals(new BigDecimal("0.00"), wallet.getAvailableBalance());
                assertEquals(0L, walletTransactionRepository.count());
                assertEquals(DepositClaimStatus.PENDING,
                                depositClaimRepository.findById(depositId).orElseThrow().getStatus());
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
                assertWalletSnapshot(walletTransactionRepository.findAll().get(0),
                                "0.00", "0.00", "0.00", "25.00");
        }

        @Test
        void disabledAndZeroSignupPoliciesLeaveNewWalletsAtZero() throws Exception {
                Instant now = Instant.now();
                signupRewardPolicyRepository.save(SignupRewardPolicy.create(false, new BigDecimal("25.00"), "ETB",
                                now.minusSeconds(60), now.plusSeconds(3600), adminUser, "disabled policy", now));

                registerPlayer("0911000103");
                assertWalletHasNoSignupReward("0911000103");

                signupRewardPolicyRepository.save(SignupRewardPolicy.create(true, BigDecimal.ZERO.setScale(2), "ETB",
                                now.minusSeconds(60), now.plusSeconds(3600), adminUser, "zero policy", now));
                registerPlayer("0911000104");
                assertWalletHasNoSignupReward("0911000104");
        }

        @Test
        void expiredAndFutureSignupPoliciesDoNotGrant() throws Exception {
                Instant now = Instant.now();
                signupRewardPolicyRepository.save(SignupRewardPolicy.create(true, new BigDecimal("25.00"), "ETB",
                                now.minusSeconds(7200), now.minusSeconds(3600), adminUser, "expired policy", now));
                registerPlayer("0911000103");
                assertWalletHasNoSignupReward("0911000103");

                signupRewardPolicyRepository.save(SignupRewardPolicy.create(true, new BigDecimal("25.00"), "ETB",
                                now.plusSeconds(3600), now.plusSeconds(7200), adminUser, "future policy", now));
                registerPlayer("0911000104");
                assertWalletHasNoSignupReward("0911000104");
        }

        @Test
        void duplicateAndConcurrentRegistrationRetriesCannotDuplicateWalletOrGrant() throws Exception {
                String phone = "0911000103";
                String body = objectMapper.writeValueAsString(new RegisterRequest("Retry Player", phone, "123456"));
                mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                                .andExpect(status().isCreated());
                mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                                .andExpect(status().isConflict());

                String concurrentPhone = "0911000104";
                String concurrentBody = objectMapper.writeValueAsString(
                                new RegisterRequest("Concurrent Player", concurrentPhone, "123456"));
                int attempts = 4;
                ExecutorService executor = Executors.newFixedThreadPool(attempts);
                CountDownLatch ready = new CountDownLatch(attempts);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Integer>> responses = new java.util.ArrayList<>();
                for (int i = 0; i < attempts; i++) {
                        responses.add(executor.submit(() -> {
                                ready.countDown();
                                start.await();
                                return mockMvc.perform(post("/api/v1/auth/register")
                                                .contentType(MediaType.APPLICATION_JSON).content(concurrentBody))
                                                .andReturn().getResponse().getStatus();
                        }));
                }
                ready.await(10, TimeUnit.SECONDS);
                start.countDown();
                List<Integer> statuses = new java.util.ArrayList<>();
                for (Future<Integer> response : responses) {
                        statuses.add(response.get(30, TimeUnit.SECONDS));
                }
                executor.shutdownNow();

                assertEquals(1L, statuses.stream().filter(status -> status == 201).count());
                assertEquals(attempts - 1L, statuses.stream().filter(status -> status == 409).count());
                assertTrue(userRepository.findByNormalizedPhone(phoneNormalizer.normalize(concurrentPhone)).isPresent());
                User concurrentPlayer = userRepository
                                .findByNormalizedPhone(phoneNormalizer.normalize(concurrentPhone)).orElseThrow();
                assertEquals(1L, walletRepository.countByOwnerId(concurrentPlayer.getId()));
                assertTrue(signupRewardGrantRepository.existsByPlayerId(concurrentPlayer.getId()));
                assertEquals(new BigDecimal("25.00"), walletRepository.findByOwnerId(concurrentPlayer.getId())
                                .orElseThrow().getPromotionalBalance());
        }

        @Test
        void signupConfigurationRejectsNegativeUnsupportedCurrencyAndInvalidDates() throws Exception {
                String adminToken = adminLoginToken("admin@example.com", "AdminPass123!");
                Instant now = Instant.now();
                String negativeAmount = objectMapper.writeValueAsString(Map.of(
                                "enabled", false, "rewardAmount", -1, "currency", "ETB",
                                "effectiveFrom", now.toString()));
                String unsupportedCurrency = objectMapper.writeValueAsString(Map.of(
                                "enabled", false, "rewardAmount", 0, "currency", "USD",
                                "effectiveFrom", now.toString()));
                String invalidDates = objectMapper.writeValueAsString(Map.of(
                                "enabled", false, "rewardAmount", 0, "currency", "ETB",
                                "effectiveFrom", now.toString(), "effectiveUntil", now.minusSeconds(1).toString()));

                for (String body : List.of(negativeAmount, unsupportedCurrency, invalidDates)) {
                        mockMvc.perform(post("/api/v1/admin/wallet/signup-rewards/settings")
                                        .header("Authorization", "Bearer " + adminToken)
                                        .contentType(MediaType.APPLICATION_JSON).content(body))
                                        .andExpect(status().isBadRequest());
                }
                assertEquals(1L, signupRewardPolicyRepository.count());
        }

        @Test
        void restrictedPlayerCannotReceiveSignupReward() {
                playerUser.setStatus(AccountStatus.PENDING);
                userRepository.save(playerUser);

                signupRewardService.grantForNewPlayer(playerUser);

                assertEquals(new BigDecimal("0.00"), walletRepository.findByOwnerId(playerUser.getId())
                                .orElseThrow().getPromotionalBalance());
                assertEquals(0L, signupRewardGrantRepository.count());
                assertEquals(0L, walletTransactionRepository.count());
        }

        private void registerPlayer(String phone) throws Exception {
                mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new RegisterRequest("Policy Player", phone,
                                                "123456"))))
                                .andExpect(status().isCreated());
        }

        private void assertWalletHasNoSignupReward(String phone) {
                User player = userRepository.findByNormalizedPhone(phoneNormalizer.normalize(phone)).orElseThrow();
                Wallet wallet = walletRepository.findByOwnerId(player.getId()).orElseThrow();
                assertEquals("ETB", wallet.getCurrency());
                assertEquals(new BigDecimal("0.00"), wallet.getAvailableBalance());
                assertEquals(new BigDecimal("0.00"), wallet.getPromotionalBalance());
                assertTrue(!signupRewardGrantRepository.existsByPlayerId(player.getId()));
                assertEquals(0L, walletTransactionRepository.countByOwnerId(player.getId()));
        }

        @Test
        void transferRejectsDisabledExpiredAndZeroLimitPolicies() throws Exception {
                String senderToken = loginToken("0911000102", "123456");
                User recipient = userRepository.save(User.create(
                                phoneNormalizer.normalize("0911000104"), "Policy Recipient",
                                passwordEncoder.encode("123456"), AccountStatus.ACTIVE,
                                Set.of(UserRole.PLAYER), Instant.now()));
                walletService.createWalletIfMissing(recipient);
                var request = objectMapper.writeValueAsString(
                                new com.bakaroo.bingo.backend.feature.wallet.dto.WalletTransferRequest(
                                                "0911000104", new BigDecimal("1.00"), "ETB", "Policy regression"));
                Instant now = Instant.now();

                mockMvc.perform(post("/api/v1/wallet/transfers")
                                .header("Authorization", "Bearer " + senderToken)
                                .header("Idempotency-Key", "transfer-invalid-recipient")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new com.bakaroo.bingo.backend.feature.wallet.dto.WalletTransferRequest(
                                                                "0911000999", new BigDecimal("1.00"), "ETB",
                                                                "Invalid recipient regression"))))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
                assertEquals(0L, walletTransferRepository.count());
                assertEquals(0L, walletTransactionRepository.count());

                walletTransferPolicyRepository.save(WalletTransferPolicy.create(false,
                                new BigDecimal("100.00"), new BigDecimal("500.00"), "ETB",
                                now.minusSeconds(60), now.plusSeconds(3600), adminUser,
                                "isolated disabled-policy regression", now));
                mockMvc.perform(post("/api/v1/wallet/transfers")
                                .header("Authorization", "Bearer " + senderToken)
                                .header("Idempotency-Key", "policy-disabled")
                                .contentType(MediaType.APPLICATION_JSON).content(request))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("TRANSFERS_DISABLED"));

                walletTransferPolicyRepository.save(WalletTransferPolicy.create(true,
                                new BigDecimal("100.00"), new BigDecimal("500.00"), "ETB",
                                now.minusSeconds(7200), now.minusSeconds(3600), adminUser,
                                "isolated expired-policy regression", now));
                mockMvc.perform(post("/api/v1/wallet/transfers")
                                .header("Authorization", "Bearer " + senderToken)
                                .header("Idempotency-Key", "policy-expired")
                                .contentType(MediaType.APPLICATION_JSON).content(request))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("TRANSFER_POLICY_INACTIVE"));

                walletTransferPolicyRepository.save(WalletTransferPolicy.create(true,
                                BigDecimal.ZERO, new BigDecimal("500.00"), "ETB",
                                now.minusSeconds(60), now.plusSeconds(3600), adminUser,
                                "isolated zero-limit regression", now));
                mockMvc.perform(post("/api/v1/wallet/transfers")
                                .header("Authorization", "Bearer " + senderToken)
                                .header("Idempotency-Key", "policy-zero-limit")
                                .contentType(MediaType.APPLICATION_JSON).content(request))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("TRANSFER_LIMIT_EXCEEDED"));

                assertEquals(0L, walletTransferRepository.count());
                assertEquals(0L, walletTransactionRepository.count());
                assertEquals(new BigDecimal("0.00"), walletRepository.findByOwnerId(playerUser.getId())
                                .orElseThrow().getAvailableBalance());
                assertEquals(new BigDecimal("0.00"), walletRepository.findByOwnerId(recipient.getId())
                                .orElseThrow().getAvailableBalance());
        }

        @Test
        void playerTransferUsesIdempotencyAndMovesTransferableBalance() throws Exception {
                String senderToken = loginToken("0911000102", "123456");
                String adminToken = adminLoginToken("admin@example.com", "AdminPass123!");
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
                senderWallet.setPromotionalBalance(new BigDecimal("10.00"));
                walletRepository.save(senderWallet);
                Wallet recipientWallet = walletRepository.findByOwnerId(recipient.getId()).orElseThrow();
                recipientWallet.setPromotionalBalance(new BigDecimal("4.00"));
                walletRepository.save(recipientWallet);

                String requestJson = objectMapper.writeValueAsString(
                                new com.bakaroo.bingo.backend.feature.wallet.dto.WalletTransferRequest(
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

                long transferId = first.get("id").asLong();
                String operationId = first.get("operationId").asText();
                String recipientToken = loginToken("0911000104", "123456");
                mockMvc.perform(get("/api/v1/wallet/transactions")
                                .header("Authorization", "Bearer " + senderToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalElements").value(1))
                                .andExpect(jsonPath("$.content[0].transactionType").value("WALLET_TRANSFER_DEBIT"))
                                .andExpect(jsonPath("$.content[0].referenceId").value(transferId));
                mockMvc.perform(get("/api/v1/wallet/transactions")
                                .header("Authorization", "Bearer " + recipientToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalElements").value(1))
                                .andExpect(jsonPath("$.content[0].transactionType").value("WALLET_TRANSFER_CREDIT"))
                                .andExpect(jsonPath("$.content[0].referenceId").value(transferId));
                mockMvc.perform(get("/api/v1/admin/wallet/transfers")
                                .header("Authorization", "Bearer " + adminToken)
                                .param("query", operationId)
                                .param("status", "COMPLETED")
                                .param("senderPhone", "251911000102")
                                .param("recipientPhone", "251911000104")
                                .param("page", "0")
                                .param("size", "1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalElements").value(1))
                                .andExpect(jsonPath("$.totalPages").value(1))
                                .andExpect(jsonPath("$.content[0].id").value(transferId))
                                .andExpect(jsonPath("$.content[0].operationId").value(operationId))
                                .andExpect(jsonPath("$.content[0].senderPhoneNumber").value("251911000102"))
                                .andExpect(jsonPath("$.content[0].recipientPhoneNumber").value("251911000104"))
                                .andExpect(jsonPath("$.content[0].amount").value(30.00))
                                .andExpect(jsonPath("$.content[0].currency").value("ETB"))
                                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));

                mockMvc.perform(get("/api/v1/admin/wallet/transfers")
                                .header("Authorization", "Bearer " + adminToken)
                                .param("query", operationId)
                                .param("status", "COMPLETED")
                                .param("page", "1")
                                .param("size", "1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isEmpty())
                                .andExpect(jsonPath("$.totalElements").value(1));

                String detailBody = mockMvc.perform(get("/api/v1/admin/wallet/transfers/{id}", transferId)
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.transfer.id").value(transferId))
                                .andExpect(jsonPath("$.transfer.operationId").value(operationId))
                                .andExpect(jsonPath("$.transfer.status").value("COMPLETED"))
                                .andExpect(jsonPath("$.transfer.amount").value(30.00))
                                .andExpect(jsonPath("$.transfer.currency").value("ETB"))
                                .andExpect(jsonPath("$.transfer.senderId").value(playerUser.getId()))
                                .andExpect(jsonPath("$.transfer.recipientId").value(recipient.getId()))
                                .andExpect(jsonPath("$.ledgerEntries.length()").value(2))
                                .andExpect(jsonPath("$.ledgerEntries[0].referenceType").value("WALLET_TRANSFER"))
                                .andExpect(jsonPath("$.ledgerEntries[0].referenceId").value(transferId))
                                .andReturn().getResponse().getContentAsString();
                JsonNode detail = objectMapper.readTree(detailBody);
                assertEquals(operationId, detail.path("ledgerEntries").get(0).path("operationId").asText());
                assertEquals(operationId, detail.path("ledgerEntries").get(1).path("operationId").asText());
                assertEquals(2, walletTransactionRepository
                                .findByReferenceTypeAndReferenceIdOrderByCreatedAtAscIdAsc("WALLET_TRANSFER", transferId)
                                .size());
                var persistedTransfer = walletTransferRepository.findById(transferId).orElseThrow();
                assertEquals(persistedTransfer.getCreatedAt().toEpochMilli(),
                                java.time.Instant.parse(first.path("createdAt").asText()).toEpochMilli());
                assertEquals(persistedTransfer.getCompletedAt().toEpochMilli(),
                                java.time.Instant.parse(first.path("completedAt").asText()).toEpochMilli());

                mockMvc.perform(post("/api/v1/wallet/transfers")
                                .header("Authorization", "Bearer " + senderToken)
                                .header("Idempotency-Key", "transfer-insufficient")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new com.bakaroo.bingo.backend.feature.wallet.dto.WalletTransferRequest(
                                                                "0911000104", new BigDecimal("71.00"), "ETB",
                                                                "Insufficient balance regression"))))
                                .andExpect(status().isConflict());
                assertEquals(new BigDecimal("70.00"),
                                walletRepository.findByOwnerId(playerUser.getId()).orElseThrow().getAvailableBalance());
                assertEquals(new BigDecimal("30.00"),
                                walletRepository.findByOwnerId(recipient.getId()).orElseThrow().getAvailableBalance());
                assertEquals(1L, walletTransferRepository.count());
                assertEquals(2L, walletTransactionRepository.count());
                assertEquals(1, jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM audit_log_entries WHERE actor_user_id = ? "
                                                + "AND action = 'TRANSFER' "
                                                + "AND JSON_UNQUOTE(JSON_EXTRACT(metadata_json, '$.outcome')) = 'FAILURE' "
                                                + "AND JSON_UNQUOTE(JSON_EXTRACT(metadata_json, '$.method')) = 'transfer'",
                                Integer.class, playerUser.getId()));

                mockMvc.perform(get("/api/v1/admin/wallet/transfers/{id}", Long.MAX_VALUE)
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").value("TRANSFER_NOT_FOUND"));
                mockMvc.perform(get("/api/v1/admin/wallet/transfers")
                                .header("Authorization", "Bearer " + senderToken))
                                .andExpect(status().isForbidden());

                User agent = userRepository.save(User.create(
                                phoneNormalizer.normalize("0911000105"), "Agent User", passwordEncoder.encode("unused"),
                                AccountStatus.ACTIVE, Set.of(UserRole.AGENT), Instant.now()));
                agent.setAgentPinHash(agentPinEncoder.encode("842691"));
                agent = userRepository.save(agent);
                agentRepository.save(Agent.create(agent, AgentStatus.APPROVED, "QA City", "QA Territory",
                                "QA_ID", "M02H-AGENT-01", null, null));
                String agentToken = agentLoginToken("0911000105", "842691");
                mockMvc.perform(get("/api/v1/admin/wallet/transfers")
                                .header("Authorization", "Bearer " + agentToken))
                                .andExpect(status().isForbidden());

                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                ExecutorService executor = Executors.newFixedThreadPool(2);
                try {
                        String concurrentRequest = objectMapper.writeValueAsString(
                                        new com.bakaroo.bingo.backend.feature.wallet.dto.WalletTransferRequest(
                                                        "0911000104", new BigDecimal("40.00"), "ETB",
                                                        "Concurrent overspend regression"));
                        Future<Integer> resultA = executor.submit(() -> concurrentTransferStatus(
                                        ready, start, senderToken, "transfer-concurrent-a", concurrentRequest));
                        Future<Integer> resultB = executor.submit(() -> concurrentTransferStatus(
                                        ready, start, senderToken, "transfer-concurrent-b", concurrentRequest));
                        assertTrue(ready.await(10, TimeUnit.SECONDS));
                        start.countDown();
                        var statuses = List.of(resultA.get(20, TimeUnit.SECONDS), resultB.get(20, TimeUnit.SECONDS));
                        assertEquals(1, statuses.stream().filter(code -> code == 200).count());
                        assertEquals(1, statuses.stream().filter(code -> code == 409).count());
                } finally {
                        start.countDown();
                        executor.shutdownNow();
                }

                Wallet senderAfter = walletRepository.findByOwnerId(playerUser.getId()).orElseThrow();
                Wallet recipientAfter = walletRepository.findByOwnerId(recipient.getId()).orElseThrow();
                assertEquals(new BigDecimal("30.00"), senderAfter.getAvailableBalance());
                assertEquals(new BigDecimal("70.00"), recipientAfter.getAvailableBalance());
                assertEquals(2L, walletTransferRepository.count());
                assertEquals(4L, walletTransactionRepository.count());
                assertWalletSnapshot(walletTransactionRepository.findAll().stream()
                                .filter(transaction -> transaction.getTransactionType()
                                                == WalletTransactionType.WALLET_TRANSFER_DEBIT)
                                .findFirst().orElseThrow(),
                                "100.00", "70.00", "10.00", "10.00");
                assertWalletSnapshot(walletTransactionRepository.findAll().stream()
                                .filter(transaction -> transaction.getTransactionType()
                                                == WalletTransactionType.WALLET_TRANSFER_CREDIT)
                                .findFirst().orElseThrow(),
                                "0.00", "30.00", "4.00", "4.00");
        }

        private int concurrentTransferStatus(CountDownLatch ready, CountDownLatch start, String token,
                        String key, String body) throws Exception {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to start concurrent transfer request");
                }
                return mockMvc.perform(post("/api/v1/wallet/transfers")
                                .header("Authorization", "Bearer " + token)
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                                .andReturn().getResponse().getStatus();
        }

        private void assertWalletSnapshot(WalletTransaction transaction,
                        String transferableBefore,
                        String transferableAfter,
                        String promotionalBefore,
                        String promotionalAfter) {
                BigDecimal transferableBeforeAmount = new BigDecimal(transferableBefore);
                BigDecimal transferableAfterAmount = new BigDecimal(transferableAfter);
                BigDecimal promotionalBeforeAmount = new BigDecimal(promotionalBefore);
                BigDecimal promotionalAfterAmount = new BigDecimal(promotionalAfter);
                assertEquals(transferableBeforeAmount, transaction.getTransferableBalanceBefore());
                assertEquals(transferableAfterAmount, transaction.getTransferableBalanceAfter());
                assertEquals(promotionalBeforeAmount, transaction.getPromotionalBalanceBefore());
                assertEquals(promotionalAfterAmount, transaction.getPromotionalBalanceAfter());
                assertEquals(transferableBeforeAmount.add(promotionalBeforeAmount), transaction.getBalanceBefore());
                assertEquals(transferableAfterAmount.add(promotionalAfterAmount), transaction.getBalanceAfter());
        }

        private User financeUser(String phone, Set<UserRole> roles) {
                return userRepository.save(User.create(phoneNormalizer.normalize(phone), "Finance QA User",
                                passwordEncoder.encode("123456"), AccountStatus.ACTIVE, roles, Instant.now()));
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

        private String agentLoginToken(String phoneNumber, String pin) throws Exception {
                String response = mockMvc.perform(post("/api/v1/agent/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new AgentLoginRequest(phoneNumber, pin))))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                return objectMapper.readTree(response).get("accessToken").asText();
        }
}
