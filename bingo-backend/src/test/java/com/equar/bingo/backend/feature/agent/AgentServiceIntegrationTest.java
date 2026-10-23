package com.equar.bingo.backend.feature.agent;

import com.equar.bingo.backend.MySqlIntegrationTest;
import com.equar.bingo.backend.common.exception.BadRequestException;
import com.equar.bingo.backend.feature.agent.dto.AgentFloatTransferRequest;
import com.equar.bingo.backend.feature.agent.dto.AgentPayoutVerificationRequest;
import com.equar.bingo.backend.feature.agent.entity.Agent;
import com.equar.bingo.backend.feature.agent.entity.AgentFloat;
import com.equar.bingo.backend.feature.agent.entity.AgentStatus;
import com.equar.bingo.backend.feature.agent.dto.AgentProfileResponse;
import com.equar.bingo.backend.feature.agent.entity.PayoutVerificationStatus;
import com.equar.bingo.backend.feature.agent.entity.CommissionRule;
import com.equar.bingo.backend.feature.agent.entity.FloatTopUpRequestStatus;
import com.equar.bingo.backend.feature.agent.repository.AgentFloatRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentFloatTransactionRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentRepository;
import com.equar.bingo.backend.feature.agent.repository.CommissionAccrualRepository;
import com.equar.bingo.backend.feature.agent.repository.FloatTopUpRequestRepository;
import com.equar.bingo.backend.feature.agent.repository.PayoutVerificationRecordRepository;
import com.equar.bingo.backend.feature.agent.service.AgentService;
import com.equar.bingo.backend.feature.agent.service.FloatFundingService;
import com.equar.bingo.backend.feature.agent.dto.FloatVerificationRequest;
import com.equar.bingo.backend.feature.agent.dto.SyntheticBankStatementRequest;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.equar.bingo.backend.feature.auth.dto.FinanceRoleAssignmentRequest;
import com.equar.bingo.backend.feature.auth.service.FinanceRoleService;
import com.equar.bingo.backend.feature.game.entity.BingoPatternType;
import com.equar.bingo.backend.feature.game.entity.BingoRoom;
import com.equar.bingo.backend.feature.game.entity.GameSession;
import com.equar.bingo.backend.feature.game.entity.GameSessionState;
import com.equar.bingo.backend.feature.game.entity.PrizeEntitlement;
import com.equar.bingo.backend.feature.game.entity.PrizeEntitlementStatus;
import com.equar.bingo.backend.feature.game.entity.PrizeSettlementPath;
import com.equar.bingo.backend.feature.game.entity.Ticket;
import com.equar.bingo.backend.feature.game.entity.WinClaim;
import com.equar.bingo.backend.feature.game.entity.WinClaimStatus;
import com.equar.bingo.backend.feature.game.repository.BingoRoomRepository;
import com.equar.bingo.backend.feature.game.repository.GameSessionRepository;
import com.equar.bingo.backend.feature.game.repository.PrizeEntitlementRepository;
import com.equar.bingo.backend.feature.game.repository.TicketRepository;
import com.equar.bingo.backend.feature.game.repository.WinClaimRepository;
import com.equar.bingo.backend.feature.support.repository.InternalNoteRepository;
import com.equar.bingo.backend.feature.support.repository.SupportMessageRepository;
import com.equar.bingo.backend.feature.support.repository.SupportTicketRepository;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.wallet.entity.IdempotencyRecord;
import com.equar.bingo.backend.feature.wallet.repository.DepositClaimRepository;
import com.equar.bingo.backend.feature.wallet.repository.IdempotencyRecordRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AgentServiceIntegrationTest extends MySqlIntegrationTest {

        @Autowired
        AgentService agentService;

        @Autowired
        FloatFundingService floatFundingService;

        @Autowired
        FinanceRoleService financeRoleService;

        @Autowired
        AgentRepository agentRepository;

        @Autowired
        AgentFloatRepository agentFloatRepository;

        @Autowired
        AgentFloatTransactionRepository agentFloatTransactionRepository;

        @Autowired
        FloatTopUpRequestRepository floatTopUpRequestRepository;

        @Autowired
        CommissionAccrualRepository commissionAccrualRepository;

        @Autowired
        PayoutVerificationRecordRepository payoutVerificationRecordRepository;

        @Autowired
        PrizeEntitlementRepository prizeEntitlementRepository;

        @Autowired
        WinClaimRepository winClaimRepository;

        @Autowired
        TicketRepository ticketRepository;

        @Autowired
        GameSessionRepository gameSessionRepository;

        @Autowired
        BingoRoomRepository bingoRoomRepository;

        @Autowired
        WalletRepository walletRepository;

        @Autowired
        WalletTransactionRepository walletTransactionRepository;

        @Autowired
        DepositClaimRepository depositClaimRepository;

        @Autowired
        IdempotencyRecordRepository idempotencyRecordRepository;

        @Autowired
        UserRepository userRepository;

        @Autowired
        EthiopianPhoneNormalizer phoneNormalizer;

        @Autowired
        PasswordEncoder passwordEncoder;

        @Autowired
        @Qualifier("agentPinEncoder")
        PasswordEncoder agentPinEncoder;

        @Autowired
        SupportTicketRepository supportTicketRepository;

        @Autowired
        SupportMessageRepository supportMessageRepository;

        @Autowired
        InternalNoteRepository internalNoteRepository;

        @Autowired
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

        private User agentUser;
        private User playerUser;
        private BingoUserPrincipal agentPrincipal;

        @BeforeEach
        void setUp() {
                jdbcTemplate.update("delete from audit_log_entries");
                jdbcTemplate.update("delete from float_accounting_journal_lines");
                jdbcTemplate.update("delete from float_accounting_journals");
                jdbcTemplate.update("delete from float_top_up_verification_attempts");
                jdbcTemplate.update("delete from float_bank_statement_entries");
                internalNoteRepository.deleteAllInBatch();
                supportMessageRepository.deleteAllInBatch();
                supportTicketRepository.deleteAllInBatch();
                payoutVerificationRecordRepository.deleteAll();
                commissionAccrualRepository.deleteAll();
                floatTopUpRequestRepository.deleteAll();
                jdbcTemplate.update("delete from float_receiving_bank_accounts");
                agentFloatTransactionRepository.deleteAll();
                prizeEntitlementRepository.deleteAll();
                winClaimRepository.deleteAll();
                ticketRepository.deleteAll();
                gameSessionRepository.deleteAll();
                bingoRoomRepository.deleteAll();
                agentFloatRepository.deleteAll();
                agentRepository.deleteAll();
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
                agentUser = userRepository.save(User.create(phoneNormalizer.normalize("0911000301"), "Agent User",
                                passwordEncoder.encode("AgentPass123!"), AccountStatus.ACTIVE, Set.of(UserRole.AGENT),
                                now));
                agentUser.setAgentPinHash(agentPinEncoder.encode("2468"));
                agentUser = userRepository.save(agentUser);
                Agent agent = agentRepository.save(Agent.create(agentUser, AgentStatus.APPROVED, "Addis Ababa",
                                "Central", "NID", "AGENT-001", null, null));
                agentFloatRepository.save(AgentFloat.create(agent, "ETB", new BigDecimal("100.00"), BigDecimal.ZERO));
                jdbcTemplate.update("""
                        insert into float_receiving_bank_accounts
                        (id, bank_name, account_number_ciphertext, masked_account_number, account_holder_name,
                         currency, active, created_by_user_id, updated_by_user_id, created_at, updated_at)
                        values (1, 'Test Bank', 'test-encrypted', '••••0001', 'Test Holder', 'ETB', true, ?, ?, ?, ?)
                        """, agentUser.getId(), agentUser.getId(), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
                jdbcTemplate.update("""
                        insert into float_receiving_bank_accounts
                        (id, bank_name, account_number_ciphertext, masked_account_number, account_holder_name,
                         currency, active, created_by_user_id, updated_by_user_id, created_at, updated_at)
                        values (2, 'Other Test Bank', 'test-encrypted-2', '••••0002', 'Other Holder', 'ETB', true, ?, ?, ?, ?)
                        """, agentUser.getId(), agentUser.getId(), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
                agentPrincipal = new BingoUserPrincipal(agentUser);

                playerUser = userRepository.save(User.create(phoneNormalizer.normalize("0911000302"), "Player User",
                                passwordEncoder.encode("123456"), AccountStatus.ACTIVE, Set.of(UserRole.PLAYER), now));
        }

        @Test
        void transferRejectsNonPositiveAmountsWithoutChangingBalances() {
                AgentFloat before = agentFloatRepository
                                .findByAgentId(agentRepository.findByUserId(agentUser.getId()).orElseThrow()
                                                .getId())
                                .orElseThrow();

                assertThrows(BadRequestException.class, () -> agentService.transferFloat(agentPrincipal,
                                new AgentFloatTransferRequest(playerUser.getNormalizedPhone(), new BigDecimal("0.00"),
                                                "ETB",
                                                "2468", "invalid"),
                                "transfer-invalid"));

                AgentFloat after = agentFloatRepository
                                .findByAgentId(agentRepository.findByUserId(agentUser.getId()).orElseThrow()
                                                .getId())
                                .orElseThrow();
                assertEquals(before.getAvailableBalance(), after.getAvailableBalance());
                assertEquals(before.getClearingBalance(), after.getClearingBalance());
                assertEquals(0L, agentFloatTransactionRepository.count());
                assertEquals(0L, idempotencyRecordRepository.count());
        }

        @Test
        void payoutVerificationMovesBalanceToClearingExactlyOnce() {
                PrizeEntitlement entitlement = createAgentCashEntitlement();

                var first = agentService.verifyAndDisbursePayout(agentPrincipal,
                                new AgentPayoutVerificationRequest(entitlement.getPayoutCode(), "2468", "CLR-1"),
                                "payout-key");
                assertEquals(PayoutVerificationStatus.DISBURSED, first.body().status());
                assertEquals(new BigDecimal("12.34"), first.body().disbursedAmount());

                AgentFloat floatState = agentFloatRepository.findByAgentId(
                                agentRepository.findByUserId(agentUser.getId()).orElseThrow().getId()).orElseThrow();
                assertEquals(new BigDecimal("87.66"), floatState.getAvailableBalance());
                assertEquals(new BigDecimal("12.34"), floatState.getClearingBalance());
                assertEquals(1L, payoutVerificationRecordRepository.count());
                assertEquals(1L, agentFloatTransactionRepository.count());
                assertEquals(1L, idempotencyRecordRepository.count());

                var replay = agentService.verifyAndDisbursePayout(agentPrincipal,
                                new AgentPayoutVerificationRequest(entitlement.getPayoutCode(), "2468", "CLR-1"),
                                "payout-key");
                assertEquals(first.body().recordId(), replay.body().recordId());
                assertEquals(new BigDecimal("87.66"), agentFloatRepository.findByAgentId(
                                agentRepository.findByUserId(agentUser.getId()).orElseThrow().getId()).orElseThrow()
                                .getAvailableBalance());
                assertEquals(new BigDecimal("12.34"), agentFloatRepository.findByAgentId(
                                agentRepository.findByUserId(agentUser.getId()).orElseThrow().getId()).orElseThrow()
                                .getClearingBalance());
                assertEquals(1L, payoutVerificationRecordRepository.count());
                assertEquals(1L, agentFloatTransactionRepository.count());
                assertEquals(1L, idempotencyRecordRepository.count());
        }

        @Test
        void dashboardLookupRegisterAndFloatReadPathsWork() {
                var dashboard = agentService.dashboard(agentPrincipal);
                assertEquals(agentRepository.findByUserId(agentUser.getId()).orElseThrow().getId(),
                                dashboard.agentId());
                assertEquals("APPROVED", dashboard.status());
                assertEquals(new BigDecimal("100.00"), dashboard.availableFloat());
                assertEquals(new BigDecimal("0.00"), dashboard.clearingFloat());

                assertEquals(playerUser.getId(), agentService.lookupPlayer(playerUser.getNormalizedPhone()).id());

                var registered = agentService.registerPlayer(
                                new com.equar.bingo.backend.feature.agent.dto.AgentPlayerRegistrationRequest(
                                                "0911000399", "Registered Player"));
                assertEquals("Registered Player", registered.displayName());
                assertEquals(Set.of(UserRole.PLAYER), registered.roles());

                assertEquals(new BigDecimal("100.00"), agentService.getFloat(agentPrincipal).availableBalance());
                assertTrue(agentService.getFloatHistory(agentPrincipal).isEmpty());
        }

        @Test
        void financialHistoryIsPagedAndFilteredOnTheServer() {
                agentService.transferFloat(agentPrincipal,
                                new AgentFloatTransferRequest("0911000302", new BigDecimal("10.00"), "ETB", "2468",
                                                "page test"),
                                "history-transfer-key");
                agentService.submitTopUpRequest(agentPrincipal,
                                new com.equar.bingo.backend.feature.agent.dto.SubmitFloatTopUpRequest(
                                                new BigDecimal("15.00"), "ETB", "page test"),
                                "history-topup-key");

                var firstPage = agentService.getFinancialHistory(
                                agentPrincipal, PageRequest.of(0, 1), null, null, null, null);
                var secondPage = agentService.getFinancialHistory(
                                agentPrincipal, PageRequest.of(1, 1), null, null, null, null);

                assertEquals(1, firstPage.content().size());
                assertEquals(1, secondPage.content().size());
                assertEquals(2, firstPage.totalElements());
                assertEquals(2, firstPage.totalPages());
                assertTrue(firstPage.content().get(0).createdAt()
                                .isAfter(secondPage.content().get(0).createdAt())
                                || firstPage.content().get(0).createdAt()
                                                .equals(secondPage.content().get(0).createdAt()));

                var filtered = agentService.getFinancialHistory(agentPrincipal, PageRequest.of(0, 20),
                                "floatTopUpRequest", "PENDING", null, null);
                assertEquals(1, filtered.totalElements());
                assertEquals("floatTopUpRequest", filtered.content().get(0).type());
                assertEquals("PENDING", filtered.content().get(0).status());

                assertThrows(BadRequestException.class, () -> agentService.getFinancialHistory(
                                agentPrincipal, PageRequest.of(0, 101), null, null, null, null));
                assertThrows(BadRequestException.class, () -> agentService.getFinancialHistory(
                                agentPrincipal, PageRequest.of(0, 20), "not-a-type", null, null, null));
        }

        @Test
        void transferFloatCreditsThePlayerWalletAndCreatesHistory() {
                var result = agentService.transferFloat(agentPrincipal,
                                new AgentFloatTransferRequest("0911000302", new BigDecimal("10.00"), "ETB", "2468",
                                                "float transfer"),
                                "transfer-key");

                assertEquals(new BigDecimal("90.00"), result.body().availableBalance());
                assertEquals(1L, agentFloatTransactionRepository.count());
                assertEquals(1L, walletTransactionRepository.count());
                assertEquals(new BigDecimal("10.00"), walletRepository.findByOwnerId(playerUser.getId()).orElseThrow()
                                .getAvailableBalance());
                WalletTransaction transaction = walletTransactionRepository.findAll().get(0);
                assertEquals(new BigDecimal("0.00"), transaction.getBalanceBefore());
                assertEquals(new BigDecimal("10.00"), transaction.getBalanceAfter());
                assertEquals(new BigDecimal("0.00"), transaction.getTransferableBalanceBefore());
                assertEquals(new BigDecimal("10.00"), transaction.getTransferableBalanceAfter());
                assertEquals(new BigDecimal("0.00"), transaction.getPromotionalBalanceBefore());
                assertEquals(new BigDecimal("0.00"), transaction.getPromotionalBalanceAfter());
                assertEquals(1, agentService.getFloatHistory(agentPrincipal).size());
        }

        @Test
        void topUpReviewAndCommissionRulePathsWork() {
                var topUp = agentService.submitTopUpRequest(agentPrincipal,
                                new com.equar.bingo.backend.feature.agent.dto.SubmitFloatTopUpRequest(
                                                new BigDecimal("15.00"),
                                                "etb", "need liquidity"),
                                "topup-key");
                assertEquals(FloatTopUpRequestStatus.PENDING, topUp.body().status());
                var replay = agentService.submitTopUpRequest(agentPrincipal,
                                new com.equar.bingo.backend.feature.agent.dto.SubmitFloatTopUpRequest(
                                                new BigDecimal("15.00"), "etb", "need liquidity"),
                                "topup-key");
                assertEquals(topUp.body().id(), replay.body().id());
                assertEquals(1, floatTopUpRequestRepository.count());

                User admin = userRepository.save(User.create(phoneNormalizer.normalize("0911000401"), "Admin User",
                                passwordEncoder.encode("AdminPass123!"), AccountStatus.ACTIVE, Set.of(UserRole.ADMIN),
                                Instant.now()));
                BingoUserPrincipal adminPrincipal = new BingoUserPrincipal(admin);

                var queue = agentService.adminTopUpRequests(PageRequest.of(0, 20), "PENDING", "Agent");
                assertEquals(1, queue.totalElements());
                assertEquals(topUp.body().id(), queue.content().get(0).id());
                assertEquals("need liquidity", queue.content().get(0).submissionNote());
                assertThrows(com.equar.bingo.backend.common.exception.ConflictException.class,
                                () -> agentService.reviewTopUpRequest(topUp.body().id(), adminPrincipal,
                                                new com.equar.bingo.backend.feature.agent.dto.ReviewFloatTopUpRequest(
                                                                true, "approved")));
                assertEquals(FloatTopUpRequestStatus.PENDING,
                                floatTopUpRequestRepository.findById(topUp.body().id()).orElseThrow().getStatus());
                assertEquals(new BigDecimal("100.00"), agentService.getFloat(agentPrincipal).availableBalance());
                var reviewed = agentService.reviewTopUpRequest(topUp.body().id(), adminPrincipal,
                                new com.equar.bingo.backend.feature.agent.dto.ReviewFloatTopUpRequest(false,
                                                "Funding evidence not approved"));
                assertEquals(FloatTopUpRequestStatus.REJECTED, reviewed.status());
                assertEquals("need liquidity", reviewed.submissionNote());
                assertThrows(com.equar.bingo.backend.common.exception.ConflictException.class,
                                () -> agentService.reviewTopUpRequest(topUp.body().id(), adminPrincipal,
                                                new com.equar.bingo.backend.feature.agent.dto.ReviewFloatTopUpRequest(
                                                                true, "Invalid duplicate approval")));
                assertEquals(new BigDecimal("100.00"), agentService.getFloat(agentPrincipal).availableBalance());
                assertEquals(0, agentFloatTransactionRepository.count());

                agentService.upsertCommissionRule(adminPrincipal,
                                new com.equar.bingo.backend.feature.agent.dto.CommissionRuleRequest(250,
                                                Instant.now().plusSeconds(3600), true));
                assertTrue(agentService.getCommissionHistory(agentPrincipal).isEmpty());
        }

        @Test
        void independentlyVerifiedQaFundingPostsBalancedJournalExactlyOnce() {
                User verifier = userRepository.save(User.create(phoneNormalizer.normalize("0911000391"), "Finance Verifier",
                                passwordEncoder.encode("FinancePass123!"), AccountStatus.ACTIVE,
                                Set.of(UserRole.FINANCE_VERIFIER), Instant.now()));
                User approver = userRepository.save(User.create(phoneNormalizer.normalize("0911000392"), "Finance Approver",
                                passwordEncoder.encode("FinancePass123!"), AccountStatus.ACTIVE,
                                Set.of(UserRole.FINANCE_APPROVER), Instant.now()));

                var submitted = agentService.submitTopUpRequest(agentPrincipal,
                                new com.equar.bingo.backend.feature.agent.dto.SubmitFloatTopUpRequest(
                                                new BigDecimal("25.50"), "ETB", "unit note", 1L,
                                                "M02B-BANK-VERIFY-001", "Uploaded receipt is a claim only"),
                                "m02b-funding-submit-1").body();
                assertEquals(FloatTopUpRequestStatus.PENDING, submitted.status());
                assertEquals("••••0001", submitted.maskedAccountSnapshot());
                assertThrows(com.equar.bingo.backend.common.exception.ConflictException.class,
                                () -> floatFundingService.postVerifiedRequest(submitted.id(),
                                                new BingoUserPrincipal(approver), "premature"));
                assertEquals(new BigDecimal("100.00"), agentService.getFloat(agentPrincipal).availableBalance());

                var statement = floatFundingService.recordSyntheticStatement(new BingoUserPrincipal(verifier),
                                new SyntheticBankStatementRequest(1L, "M02B-BANK-VERIFY-001",
                                                new BigDecimal("25.50"), "ETB", java.time.LocalDate.now(), "SETTLED"));
                assertTrue(statement.synthetic());
                var verified = floatFundingService.verify(submitted.id(), new BingoUserPrincipal(verifier),
                                new FloatVerificationRequest(statement.statementEntryId(), null, null));
                assertEquals("MATCHED", verified.outcome());
                assertEquals(FloatTopUpRequestStatus.VERIFIED,
                                floatTopUpRequestRepository.findById(submitted.id()).orElseThrow().getStatus());
                assertThrows(com.equar.bingo.backend.common.exception.ConflictException.class,
                                () -> floatFundingService.postVerifiedRequest(submitted.id(),
                                                new BingoUserPrincipal(verifier), "same user cannot verify and approve"));

                var posted = floatFundingService.postVerifiedRequest(submitted.id(), new BingoUserPrincipal(approver),
                                "Synthetic QA approval");
                assertTrue(posted.simulated());
                assertEquals("ETB", posted.currency());
                assertEquals(new BigDecimal("125.50"), posted.availableBalance());
                assertEquals(verifier.getId(), posted.verifierUserId());
                assertEquals(approver.getId(), posted.approverUserId());
                assertEquals(posted.journalId(), floatFundingService.postVerifiedRequest(submitted.id(),
                                new BingoUserPrincipal(approver), "retry").journalId());
                assertEquals(new BigDecimal("125.50"), agentService.getFloat(agentPrincipal).availableBalance());
                assertEquals(1, jdbcTemplate.queryForObject("select count(*) from float_accounting_journals where request_id=?",
                                Integer.class, submitted.id()));
                assertEquals(2, jdbcTemplate.queryForObject("select count(*) from float_accounting_journal_lines where journal_id=?",
                                Integer.class, posted.journalId()));
                assertEquals(0, jdbcTemplate.queryForObject("""
                                select count(*) from float_accounting_journal_lines where journal_id=?
                                  and ((debit_amount > 0 and credit_amount > 0) or (debit_amount = 0 and credit_amount = 0))
                                """, Integer.class, posted.journalId()));
                assertEquals(1, jdbcTemplate.queryForObject("select count(*) from agent_float_transactions where related_entity_id=?",
                                Integer.class, submitted.id()));
                assertEquals(1, jdbcTemplate.queryForObject("""
                                select case when sum(debit_amount)=sum(credit_amount) then 1 else 0 end
                                from float_accounting_journal_lines where journal_id=?
                                """, Integer.class, posted.journalId()));
        }

        @Test
        void independentVerificationExceptionsAndUnauthorizedReviewersNeverCreditFloat() {
                User verifier = userRepository.save(User.create(phoneNormalizer.normalize("0911000381"), "Finance Verifier Two",
                                passwordEncoder.encode("FinancePass123!"), AccountStatus.ACTIVE,
                                Set.of(UserRole.FINANCE_VERIFIER), Instant.now()));
                var request = agentService.submitTopUpRequest(agentPrincipal,
                                new com.equar.bingo.backend.feature.agent.dto.SubmitFloatTopUpRequest(
                                                new BigDecimal("10.00"), "ETB", null, 1L,
                                                "M02B-BANK-VERIFY-002", null), "m02b-funding-submit-2").body();
                var mismatchStatement = floatFundingService.recordSyntheticStatement(new BingoUserPrincipal(verifier),
                                new SyntheticBankStatementRequest(1L, "M02B-BANK-VERIFY-002",
                                                new BigDecimal("9.99"), "ETB", java.time.LocalDate.now(), "SETTLED"));
                var mismatch = floatFundingService.verify(request.id(), new BingoUserPrincipal(verifier),
                                new FloatVerificationRequest(mismatchStatement.statementEntryId(), null, null));
                assertEquals("AMOUNT_MISMATCH", mismatch.outcome());
                assertEquals(FloatTopUpRequestStatus.VERIFICATION_EXCEPTION,
                                floatTopUpRequestRepository.findById(request.id()).orElseThrow().getStatus());
                assertThrows(com.equar.bingo.backend.common.exception.ConflictException.class,
                                () -> floatFundingService.verify(request.id(), agentPrincipal,
                                                new FloatVerificationRequest(mismatchStatement.statementEntryId(), null, null)));
                assertThrows(com.equar.bingo.backend.common.exception.ConflictException.class,
                                () -> floatFundingService.recordSyntheticStatement(agentPrincipal,
                                                new SyntheticBankStatementRequest(1L, "M02B-UNAUTHORIZED-REF",
                                                                new BigDecimal("10.00"), "ETB", java.time.LocalDate.now(), "SETTLED")));
                assertEquals(new BigDecimal("100.00"), agentService.getFloat(agentPrincipal).availableBalance());
                assertEquals(0, jdbcTemplate.queryForObject("select count(*) from float_accounting_journals", Integer.class));
        }

        @Test
        void wrongAccountUnsettledAndDuplicateReferencesAreRejectedWithoutCredit() {
                User verifier = userRepository.save(User.create(phoneNormalizer.normalize("0911000371"), "Finance Verifier Evidence",
                                passwordEncoder.encode("FinancePass123!"), AccountStatus.ACTIVE,
                                Set.of(UserRole.FINANCE_VERIFIER), Instant.now()));

                var wrongAccountRequest = agentService.submitTopUpRequest(agentPrincipal,
                                new com.equar.bingo.backend.feature.agent.dto.SubmitFloatTopUpRequest(
                                                new BigDecimal("10.00"), "ETB", null, 1L, "M02B-WRONG-ACCOUNT", null),
                                "m02b-wrong-account-submit").body();
                var wrongAccountStatement = floatFundingService.recordSyntheticStatement(new BingoUserPrincipal(verifier),
                                new SyntheticBankStatementRequest(2L, "M02B-WRONG-ACCOUNT", new BigDecimal("10.00"),
                                                "ETB", java.time.LocalDate.now(), "SETTLED"));
                var wrongAccount = floatFundingService.verify(wrongAccountRequest.id(), new BingoUserPrincipal(verifier),
                                new FloatVerificationRequest(wrongAccountStatement.statementEntryId(), null, null));
                assertEquals("WRONG_RECEIVING_ACCOUNT", wrongAccount.outcome());
                assertThrows(com.equar.bingo.backend.common.exception.ConflictException.class,
                                () -> floatFundingService.recordSyntheticStatement(new BingoUserPrincipal(verifier),
                                                new SyntheticBankStatementRequest(2L, "M02B-WRONG-ACCOUNT",
                                                                new BigDecimal("10.00"), "ETB",
                                                                java.time.LocalDate.now(), "SETTLED")));

                var unsettledRequest = agentService.submitTopUpRequest(agentPrincipal,
                                new com.equar.bingo.backend.feature.agent.dto.SubmitFloatTopUpRequest(
                                                new BigDecimal("11.00"), "ETB", null, 1L, "M02B-UNSETTLED", null),
                                "m02b-unsettled-submit").body();
                var unsettledStatement = floatFundingService.recordSyntheticStatement(new BingoUserPrincipal(verifier),
                                new SyntheticBankStatementRequest(1L, "M02B-UNSETTLED", new BigDecimal("11.00"),
                                                "ETB", java.time.LocalDate.now(), "UNSETTLED"));
                var unsettled = floatFundingService.verify(unsettledRequest.id(), new BingoUserPrincipal(verifier),
                                new FloatVerificationRequest(unsettledStatement.statementEntryId(), null, null));
                assertEquals("UNSETTLED_OR_REVERSED", unsettled.outcome());
                assertEquals(new BigDecimal("100.00"), agentService.getFloat(agentPrincipal).availableBalance());
                assertEquals(0, jdbcTemplate.queryForObject("select count(*) from float_accounting_journals", Integer.class));
        }

        @Test
        void concurrentApprovalPostsOneBalancedJournalAndOneFloatCredit() throws Exception {
                User verifier = userRepository.save(User.create(phoneNormalizer.normalize("0911000361"), "Finance Verifier Concurrent",
                                passwordEncoder.encode("FinancePass123!"), AccountStatus.ACTIVE,
                                Set.of(UserRole.FINANCE_VERIFIER), Instant.now()));
                User approver = userRepository.save(User.create(phoneNormalizer.normalize("0911000362"), "Finance Approver Concurrent",
                                passwordEncoder.encode("FinancePass123!"), AccountStatus.ACTIVE,
                                Set.of(UserRole.FINANCE_APPROVER), Instant.now()));
                var request = agentService.submitTopUpRequest(agentPrincipal,
                                new com.equar.bingo.backend.feature.agent.dto.SubmitFloatTopUpRequest(
                                                new BigDecimal("13.25"), "ETB", null, 1L, "M02B-CONCURRENT", null),
                                "m02b-concurrent-submit").body();
                var statement = floatFundingService.recordSyntheticStatement(new BingoUserPrincipal(verifier),
                                new SyntheticBankStatementRequest(1L, "M02B-CONCURRENT", new BigDecimal("13.25"),
                                                "ETB", java.time.LocalDate.now(), "SETTLED"));
                floatFundingService.verify(request.id(), new BingoUserPrincipal(verifier),
                                new FloatVerificationRequest(statement.statementEntryId(), null, null));

                var ready = new CountDownLatch(2);
                var start = new CountDownLatch(1);
                var pool = Executors.newFixedThreadPool(2);
                try {
                        var first = pool.submit(() -> {
                                ready.countDown();
                                start.await();
                                return floatFundingService.postVerifiedRequest(request.id(), new BingoUserPrincipal(approver), "Concurrent A");
                        });
                        var second = pool.submit(() -> {
                                ready.countDown();
                                start.await();
                                return floatFundingService.postVerifiedRequest(request.id(), new BingoUserPrincipal(approver), "Concurrent B");
                        });
                        assertTrue(ready.await(5, TimeUnit.SECONDS));
                        start.countDown();
                        var firstResult = first.get(15, TimeUnit.SECONDS);
                        var secondResult = second.get(15, TimeUnit.SECONDS);
                        assertEquals(firstResult.journalId(), secondResult.journalId());
                        assertEquals(new BigDecimal("113.25"), agentService.getFloat(agentPrincipal).availableBalance());
                        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from float_accounting_journals where request_id=?",
                                        Integer.class, request.id()));
                        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from agent_float_transactions where related_entity_id=?",
                                        Integer.class, request.id()));
                } finally {
                        pool.shutdownNow();
                }
        }

        @Test
        void postingFailureRollsBackJournalBalanceAndRequestTransition() {
                User verifier = userRepository.save(User.create(phoneNormalizer.normalize("0911000351"), "Finance Verifier Rollback",
                                passwordEncoder.encode("FinancePass123!"), AccountStatus.ACTIVE,
                                Set.of(UserRole.FINANCE_VERIFIER), Instant.now()));
                User approver = userRepository.save(User.create(phoneNormalizer.normalize("0911000352"), "Finance Approver Rollback",
                                passwordEncoder.encode("FinancePass123!"), AccountStatus.ACTIVE,
                                Set.of(UserRole.FINANCE_APPROVER), Instant.now()));
                var request = agentService.submitTopUpRequest(agentPrincipal,
                                new com.equar.bingo.backend.feature.agent.dto.SubmitFloatTopUpRequest(
                                                new BigDecimal("17.00"), "ETB", null, 1L, "M02B-ROLLBACK", null),
                                "m02b-rollback-submit").body();
                var statement = floatFundingService.recordSyntheticStatement(new BingoUserPrincipal(verifier),
                                new SyntheticBankStatementRequest(1L, "M02B-ROLLBACK", new BigDecimal("17.00"),
                                                "ETB", java.time.LocalDate.now(), "SETTLED"));
                floatFundingService.verify(request.id(), new BingoUserPrincipal(verifier),
                                new FloatVerificationRequest(statement.statementEntryId(), null, null));
                String duplicateOperationId = UUID.nameUUIDFromBytes(("FLOAT-TOP-UP-" + request.id())
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                jdbcTemplate.update("""
                                insert into agent_float_transactions
                                (agent_float_id, transaction_type, amount, available_before, available_after,
                                 clearing_before, clearing_after, related_entity_type, related_entity_id, operation_id,
                                 note, created_at)
                                values (?, 'MANUAL_ADJUSTMENT', 1.00, 100.00, 101.00, 0.00, 0.00,
                                        'ROLLBACK_FIXTURE', null, ?, 'Synthetic collision fixture', ?)
                                """, agentFloatRepository.findByAgentId(agentRepository.findByUserId(agentUser.getId()).orElseThrow().getId())
                                .orElseThrow().getId(), duplicateOperationId, java.sql.Timestamp.from(Instant.now()));

                assertThrows(RuntimeException.class,
                                () -> floatFundingService.postVerifiedRequest(request.id(), new BingoUserPrincipal(approver), "Must roll back"));
                assertEquals(new BigDecimal("100.00"), agentService.getFloat(agentPrincipal).availableBalance());
                assertEquals(FloatTopUpRequestStatus.VERIFIED,
                                floatTopUpRequestRepository.findById(request.id()).orElseThrow().getStatus());
                assertEquals(0, jdbcTemplate.queryForObject("select count(*) from float_accounting_journals where request_id=?",
                                Integer.class, request.id()));
                assertEquals(0, jdbcTemplate.queryForObject("select count(*) from agent_float_transactions where related_entity_id=?",
                                Integer.class, request.id()));
        }

        @Test
        void financePermissionsAreGrantedOnlyByAnAdminToEligibleOtherAccounts() {
                User admin = userRepository.save(User.create(phoneNormalizer.normalize("0911000341"), "Finance Role Admin",
                                passwordEncoder.encode("AdminPass123!"), AccountStatus.ACTIVE,
                                Set.of(UserRole.ADMIN), Instant.now()));
                User support = userRepository.save(User.create(phoneNormalizer.normalize("0911000342"), "Finance Role Support",
                                passwordEncoder.encode("SupportPass123!"), AccountStatus.ACTIVE,
                                Set.of(UserRole.SUPPORT), Instant.now()));
                assertThrows(com.equar.bingo.backend.common.exception.ConflictException.class,
                                () -> financeRoleService.assign(admin, admin.getId(),
                                                new FinanceRoleAssignmentRequest(Set.of(UserRole.FINANCE_VERIFIER))));
                assertThrows(com.equar.bingo.backend.common.exception.ConflictException.class,
                                () -> financeRoleService.assign(admin, agentUser.getId(),
                                                new FinanceRoleAssignmentRequest(Set.of(UserRole.FINANCE_VERIFIER))));
                assertThrows(com.equar.bingo.backend.common.exception.ConflictException.class,
                                () -> financeRoleService.assign(playerUser, support.getId(),
                                                new FinanceRoleAssignmentRequest(Set.of(UserRole.FINANCE_VERIFIER))));
                assertThrows(com.equar.bingo.backend.common.exception.ConflictException.class,
                                () -> financeRoleService.assign(admin, support.getId(),
                                                new FinanceRoleAssignmentRequest(Set.of(UserRole.PLAYER))));

                var assigned = financeRoleService.assign(admin, support.getId(),
                                new FinanceRoleAssignmentRequest(Set.of(UserRole.FINANCE_VERIFIER)));
                assertTrue(assigned.roles().contains(UserRole.FINANCE_VERIFIER));
                var appended = financeRoleService.assign(admin, support.getId(),
                                new FinanceRoleAssignmentRequest(Set.of(UserRole.FINANCE_APPROVER)));
                assertTrue(appended.roles().containsAll(Set.of(UserRole.SUPPORT, UserRole.FINANCE_VERIFIER,
                                UserRole.FINANCE_APPROVER)));
                var revoked = financeRoleService.revoke(admin, support.getId(), UserRole.FINANCE_VERIFIER);
                assertTrue(revoked.roles().contains(UserRole.FINANCE_APPROVER));
                assertTrue(!revoked.roles().contains(UserRole.FINANCE_VERIFIER));
        }

        @Test
        void suspensionAfterVerificationBlocksPosting() {
                User verifier = userRepository.save(User.create(phoneNormalizer.normalize("0911000331"), "Finance Verifier Suspension",
                                passwordEncoder.encode("FinancePass123!"), AccountStatus.ACTIVE,
                                Set.of(UserRole.FINANCE_VERIFIER), Instant.now()));
                User approver = userRepository.save(User.create(phoneNormalizer.normalize("0911000332"), "Finance Approver Suspension",
                                passwordEncoder.encode("FinancePass123!"), AccountStatus.ACTIVE,
                                Set.of(UserRole.FINANCE_APPROVER), Instant.now()));
                var request = agentService.submitTopUpRequest(agentPrincipal,
                                new com.equar.bingo.backend.feature.agent.dto.SubmitFloatTopUpRequest(
                                                new BigDecimal("7.00"), "ETB", null, 1L, "M02B-SUSPENSION", null),
                                "m02b-suspension-submit").body();
                var statement = floatFundingService.recordSyntheticStatement(new BingoUserPrincipal(verifier),
                                new SyntheticBankStatementRequest(1L, "M02B-SUSPENSION", new BigDecimal("7.00"),
                                                "ETB", java.time.LocalDate.now(), "SETTLED"));
                floatFundingService.verify(request.id(), new BingoUserPrincipal(verifier),
                                new FloatVerificationRequest(statement.statementEntryId(), null, null));
                Agent agent = agentRepository.findByUserId(agentUser.getId()).orElseThrow();
                agentService.adminSuspendAgent(agent.getId());

                assertThrows(com.equar.bingo.backend.common.exception.ConflictException.class,
                                () -> floatFundingService.postVerifiedRequest(request.id(), new BingoUserPrincipal(approver), "Suspension blocks credit"));
                assertEquals(new BigDecimal("100.00"), agentFloatRepository.findByAgentId(agent.getId()).orElseThrow().getAvailableBalance());
                assertEquals(FloatTopUpRequestStatus.VERIFIED,
                                floatTopUpRequestRepository.findById(request.id()).orElseThrow().getStatus());
                assertEquals(0, jdbcTemplate.queryForObject("select count(*) from float_accounting_journals where request_id=?",
                                Integer.class, request.id()));
        }

        @Test
        void floatRequestRejectsInvalidAmountsAndUnapprovedAgents() {
                assertThrows(BadRequestException.class, () -> agentService.submitTopUpRequest(agentPrincipal,
                                new com.equar.bingo.backend.feature.agent.dto.SubmitFloatTopUpRequest(
                                                BigDecimal.ZERO, "ETB", "QA invalid zero"), "float-zero"));
                assertThrows(BadRequestException.class, () -> agentService.submitTopUpRequest(agentPrincipal,
                                new com.equar.bingo.backend.feature.agent.dto.SubmitFloatTopUpRequest(
                                                new BigDecimal("-1.00"), "ETB", "QA invalid negative"), "float-negative"));

                User pendingUser = userRepository.save(User.create(phoneNormalizer.normalize("0911000311"),
                                "Pending Float Agent", passwordEncoder.encode("AgentPass123!"), AccountStatus.PENDING,
                                Set.of(UserRole.AGENT), Instant.now()));
                Agent pendingAgent = agentRepository.save(Agent.create(pendingUser, AgentStatus.PENDING,
                                "City", "Territory", "NID", "PENDING-FLOAT-001", null, null));
                var pendingPrincipal = new BingoUserPrincipal(pendingUser);
                assertThrows(com.equar.bingo.backend.common.exception.ConflictException.class,
                                () -> agentService.submitTopUpRequest(pendingPrincipal,
                                                new com.equar.bingo.backend.feature.agent.dto.SubmitFloatTopUpRequest(
                                                                new BigDecimal("10.00"), "ETB", "QA"),
                                                "pending-float"));

                User suspendedUser = userRepository.save(User.create(phoneNormalizer.normalize("0911000312"),
                                "Suspended Float Agent", passwordEncoder.encode("AgentPass123!"),
                                AccountStatus.SUSPENDED, Set.of(UserRole.AGENT), Instant.now()));
                agentRepository.save(Agent.create(suspendedUser, AgentStatus.SUSPENDED,
                                "City", "Territory", "NID", "SUSPENDED-FLOAT-001", null, null));
                assertThrows(com.equar.bingo.backend.common.exception.ConflictException.class,
                                () -> agentService.submitTopUpRequest(new BingoUserPrincipal(suspendedUser),
                                                new com.equar.bingo.backend.feature.agent.dto.SubmitFloatTopUpRequest(
                                                                new BigDecimal("10.00"), "ETB", "QA"),
                                                "suspended-float"));
                assertEquals(0, floatTopUpRequestRepository.count());
                assertEquals(new BigDecimal("100.00"), agentService.getFloat(agentPrincipal).availableBalance());
        }

        @Test
        void agentFloatBalanceAndHistoryAreScopedToTheAuthenticatedAgent() {
                User otherUser = userRepository.save(User.create(phoneNormalizer.normalize("0911000313"),
                                "Other Float Agent", passwordEncoder.encode("AgentPass123!"), AccountStatus.ACTIVE,
                                Set.of(UserRole.AGENT), Instant.now()));
                Agent otherAgent = agentRepository.save(Agent.create(otherUser, AgentStatus.APPROVED,
                                "City", "Territory", "NID", "OTHER-FLOAT-001", null, null));
                agentFloatRepository.save(AgentFloat.create(otherAgent, "ETB", new BigDecimal("17.00"),
                                BigDecimal.ZERO));
                BingoUserPrincipal otherPrincipal = new BingoUserPrincipal(otherUser);

                assertEquals(new BigDecimal("100.00"), agentService.getFloat(agentPrincipal).availableBalance());
                assertEquals(new BigDecimal("17.00"), agentService.getFloat(otherPrincipal).availableBalance());
                assertTrue(agentService.getFloatHistory(agentPrincipal).isEmpty());
                assertTrue(agentService.getFloatHistory(otherPrincipal).isEmpty());
                assertEquals(new BigDecimal("17.00"), agentService.adminFloatBalance(otherAgent.getId())
                                .availableBalance());
                assertTrue(agentService.adminFloatHistory(otherAgent.getId(), PageRequest.of(0, 20))
                                .content().isEmpty());
        }

        @Test
        void adminApproveAndSuspendAgentManageAgentState() {
                User pendingUser = userRepository
                                .save(User.create(phoneNormalizer.normalize("0911000501"), "Pending Agent",
                                                passwordEncoder.encode("AgentPass123!"), AccountStatus.PENDING,
                                                Set.of(UserRole.AGENT), Instant.now()));

                var pendingAgent = agentRepository.save(Agent.create(pendingUser, AgentStatus.PENDING,
                                "City", "Territory", "NID", "N-001", null, null));
                var approved = agentService.approveAgentApplication(pendingAgent.getId());
                assertEquals(AgentStatus.APPROVED, approved.status());

                var suspended = agentService.adminSuspendAgent(approved.agentId());
                assertEquals(AgentStatus.SUSPENDED, suspended.status());
        }

        private PrizeEntitlement createAgentCashEntitlement() {
                BingoRoom room = new BingoRoom();
                room.setName("Agent Cash Room");
                room.setDescription("Fixture room");
                room.setTicketPrice(new BigDecimal("4.00"));
                room.setCurrency("ETB");
                room.setTimezone("Africa/Addis_Ababa");
                room.setScheduleRule("0 0/30 * * * *");
                room.setSessionCapacity(1);
                room.setMaxTicketsPerPlayer(1);
                room.setTicketSalesCloseOnSessionStart(true);
                room.setAllowMultipleWinnersPerPrize(true);
                room.setActive(true);
                room.setConfiguredPatternsJson("[\"LINE\"]");
                room.setConfiguredPrizesJson("[]");
                room = bingoRoomRepository.save(room);

                GameSession session = new GameSession();
                session.setRoom(room);
                session.setSessionName("Cash Session");
                session.setState(GameSessionState.OPEN);
                session.setScheduledStartAt(Instant.now().plusSeconds(3600));
                session.setTicketPriceSnapshot(new BigDecimal("4.00"));
                session.setCurrencySnapshot("ETB");
                session.setSessionCapacitySnapshot(1);
                session.setMaxTicketsPerPlayerSnapshot(1);
                session.setTicketSalesCloseOnSessionStart(true);
                session.setAllowMultipleWinnersPerPrize(true);
                session.setPatternsSnapshotJson("[\"LINE\"]");
                session.setPrizesSnapshotJson("[]");
                session = gameSessionRepository.save(session);

                Ticket ticket = new Ticket();
                ticket.setSession(session);
                ticket.setOwner(playerUser);
                ticket.setPublicTicketCode("TICKET-001");
                ticket.setOperationId(UUID.randomUUID().toString());
                ticket.setGridJson("[[1,2,3,4,5]]");
                ticket = ticketRepository.save(ticket);

                WinClaim winClaim = new WinClaim();
                winClaim.setTicket(ticket);
                winClaim.setSession(session);
                winClaim.setClaimant(playerUser);
                winClaim.setClaimedPattern(BingoPatternType.LINE);
                winClaim.setStatus(WinClaimStatus.APPROVED);
                winClaim.setServerValidationPassed(true);
                winClaim.setServerValidationCode("OK");
                winClaim.setServerValidationMessage("OK");
                winClaim.setDrawSequenceSnapshot(5);
                winClaim.setCalledNumbersSnapshotJson("[1,2,3,4,5]");
                winClaim.setEvidenceJson("{}");
                winClaim.setOperationId(UUID.randomUUID().toString());
                winClaim = winClaimRepository.save(winClaim);

                PrizeEntitlement entitlement = new PrizeEntitlement();
                entitlement.setWinClaim(winClaim);
                entitlement.setSession(session);
                entitlement.setTicket(ticket);
                entitlement.setClaimant(playerUser);
                entitlement.setPrizeType(BingoPatternType.LINE);
                entitlement.setSettlementPath(PrizeSettlementPath.AGENT_CASH);
                entitlement.setStatus(PrizeEntitlementStatus.PENDING);
                entitlement.setPrizeAmount(new BigDecimal("12.34"));
                entitlement.setCurrency("ETB");
                entitlement.setOperationId(UUID.randomUUID().toString());
                entitlement.setPayoutCode("PAYOUT-001");
                return prizeEntitlementRepository.save(entitlement);
        }
}
