package com.equar.bingo.backend.feature.wallet.service;

import com.equar.bingo.backend.common.exception.BadRequestException;
import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.common.exception.NotFoundException;
import com.equar.bingo.backend.common.idempotency.IdempotentResult;
import com.equar.bingo.backend.common.idempotency.IdempotencyService;
import com.equar.bingo.backend.common.money.MoneyValues;
import com.equar.bingo.backend.common.request.RequestHashService;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.wallet.config.WalletProperties;
import com.equar.bingo.backend.feature.wallet.dto.DepositClaimResponse;
import com.equar.bingo.backend.feature.wallet.dto.DepositJournalLineResponse;
import com.equar.bingo.backend.feature.wallet.dto.DepositPostingResponse;
import com.equar.bingo.backend.feature.wallet.dto.DepositVerificationRequest;
import com.equar.bingo.backend.feature.wallet.dto.DepositVerificationResponse;
import com.equar.bingo.backend.feature.wallet.dto.PaymentInstructionsResponse;
import com.equar.bingo.backend.feature.wallet.entity.DepositClaim;
import com.equar.bingo.backend.feature.wallet.entity.DepositClaimStatus;
import com.equar.bingo.backend.feature.wallet.entity.Wallet;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransaction;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransactionType;
import com.equar.bingo.backend.feature.wallet.payment.PaymentProvider;
import com.equar.bingo.backend.feature.wallet.repository.DepositClaimRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepositFinancialService {
    private static final String SYNTHETIC_SOURCE = "SYNTHETIC_QA_BANK_STATEMENT";
    private static final String DEBIT_ACCOUNT = "QA_BANK_CLEARING_ASSET";
    private static final String CREDIT_ACCOUNT = "PLAYER_WALLET_LIABILITY";

    private final JdbcTemplate jdbc;
    private final DepositClaimRepository claims;
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final UserRepository users;
    private final PaymentProvider paymentProvider;
    private final WalletProperties walletProperties;
    private final IdempotencyService idempotencyService;
    private final RequestHashService requestHashService;
    private final TelebirrReferenceNormalizer referenceNormalizer;
    private final LocalFinancialSimulationGuard simulationGuard;
    private final boolean simulationEnabled;

    public DepositFinancialService(JdbcTemplate jdbc,
            DepositClaimRepository claims,
            WalletRepository wallets,
            WalletTransactionRepository transactions,
            UserRepository users,
            PaymentProvider paymentProvider,
            WalletProperties walletProperties,
            IdempotencyService idempotencyService,
            RequestHashService requestHashService,
            TelebirrReferenceNormalizer referenceNormalizer,
            LocalFinancialSimulationGuard simulationGuard,
            @Value("${app.wallet.deposit-simulation-enabled:false}") boolean simulationEnabled) {
        this.jdbc = jdbc;
        this.claims = claims;
        this.wallets = wallets;
        this.transactions = transactions;
        this.users = users;
        this.paymentProvider = paymentProvider;
        this.walletProperties = walletProperties;
        this.idempotencyService = idempotencyService;
        this.requestHashService = requestHashService;
        this.referenceNormalizer = referenceNormalizer;
        this.simulationGuard = simulationGuard;
        this.simulationEnabled = simulationEnabled;
        simulationGuard.validateConfiguration(simulationEnabled, "Player deposit");
    }

    @Transactional
    public DepositVerificationResponse verify(Long depositId, BingoUserPrincipal principal,
            DepositVerificationRequest input) {
        User verifier = requireRole(principal.getUser(), UserRole.FINANCE_VERIFIER);
        DepositClaim claim = claims.findByIdForUpdate(depositId)
                .orElseThrow(() -> new NotFoundException("DEPOSIT_NOT_FOUND", "Deposit claim not found"));
        if (verifier.getId().equals(claim.getPlayer().getId())) {
            throw new ConflictException("DEPOSIT_SELF_VERIFICATION_DENIED",
                    "A player cannot verify their own deposit");
        }
        if (claim.getStatus() != DepositClaimStatus.PENDING
                && claim.getStatus() != DepositClaimStatus.AWAITING_VERIFICATION
                && claim.getStatus() != DepositClaimStatus.VERIFIED
                && claim.getStatus() != DepositClaimStatus.VERIFICATION_EXCEPTION) {
            throw new ConflictException("DEPOSIT_NOT_VERIFIABLE", "Only unposted deposit claims can be verified");
        }

        BankStatement statement = findStatement(input.statementEntryId());
        if (statement.synthetic()) {
            simulationGuard.requireEnabled(simulationEnabled, "Player deposit");
        }
        String outcome = matchOutcome(claim, statement);
        long verificationId;
        try {
            verificationId = insertVerification(depositId, statement, verifier.getId(), outcome);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("BANK_STATEMENT_ALREADY_USED",
                    "This bank transaction evidence has already been applied");
        }

        Instant now = Instant.now();
        if ("MATCHED".equals(outcome)) {
            claim.setStatus(DepositClaimStatus.VERIFIED);
            claim.setVerifiedBy(verifier);
            claim.setVerifiedAt(now);
        } else {
            claim.setStatus(DepositClaimStatus.VERIFICATION_EXCEPTION);
            claim.setVerifiedBy(null);
            claim.setVerifiedAt(null);
        }
        claims.save(claim);
        return toVerification(verificationId);
    }

    @Transactional
    public IdempotentResult<DepositClaimResponse> approve(Long depositId, BingoUserPrincipal principal,
            String reviewNote, String idempotencyKey) {
        User approver = requireRole(principal.getUser(), UserRole.FINANCE_APPROVER);
        DepositReviewPayload payload = new DepositReviewPayload(reviewNote);
        String requestHash = requestHashService.hash("POST",
                "/api/v1/admin/deposits/" + depositId + "/approve", payload);
        String operationId = UUID.nameUUIDFromBytes(("QA-PLAYER-DEPOSIT-" + depositId)
                .getBytes(StandardCharsets.UTF_8)).toString();

        return idempotencyService.execute(approver.getId(), idempotencyKey, requestHash, operationId,
                HttpStatus.OK.value(), DepositClaimResponse.class, () -> {
                    DepositClaim claim = claims.findByIdForUpdate(depositId)
                            .orElseThrow(() -> new NotFoundException("DEPOSIT_NOT_FOUND", "Deposit claim not found"));
                    if (claim.getStatus() == DepositClaimStatus.APPROVED) {
                        requireExistingPosting(depositId);
                        return toDepositResponse(claim);
                    }
                    if (claim.getStatus() != DepositClaimStatus.VERIFIED) {
                        throw new ConflictException("DEPOSIT_NOT_VERIFIED",
                                "An unverified deposit cannot be approved or credited");
                    }

                    DepositVerificationResponse verification = latestVerification(depositId);
                    if (!"MATCHED".equals(verification.outcome())
                            || !claim.getVerifiedBy().getId().equals(verification.verifierUserId())) {
                        throw new ConflictException("DEPOSIT_VERIFICATION_REQUIRED",
                                "A current matched bank verification is required before approval");
                    }
                    BankStatement currentStatement = findStatement(verification.statementEntryId());
                    if (!"MATCHED".equals(matchOutcome(claim, currentStatement))) {
                        throw new ConflictException("DEPOSIT_VERIFICATION_STALE",
                                "The bank evidence no longer confirms this deposit");
                    }
                    if (verification.verifierUserId().equals(approver.getId())) {
                        throw new ConflictException("FINANCE_SEPARATION_OF_DUTIES_REQUIRED",
                                "The verifier and approver must be different users");
                    }
                    User verifier = users.findById(verification.verifierUserId())
                            .orElseThrow(() -> new ConflictException("FINANCE_VERIFIER_NOT_AUTHORIZED",
                                    "The verifying user is no longer authorized"));
                    if (!verifier.isActive() || !verifier.hasRole(UserRole.FINANCE_VERIFIER)) {
                        throw new ConflictException("FINANCE_VERIFIER_NOT_AUTHORIZED",
                                "The verifying user is no longer authorized");
                    }
                    if (!verification.synthetic()) {
                        throw new ConflictException("DEPOSIT_POSTING_DISABLED",
                                "Production deposit posting remains disabled pending approved accounting and settlement policies");
                    }
                    simulationGuard.requireEnabled(simulationEnabled, "Player deposit");
                    if (!"ETB".equals(claim.getCurrency())
                            || claim.getSubmittedCashAmount().compareTo(claim.getCalculatedCreditAmount()) != 0) {
                        throw new ConflictException("DEPOSIT_ACCOUNTING_REVIEW_REQUIRED",
                                "Deposit conversion requires an approved balancing account before posting");
                    }

                    Wallet wallet = wallets.findByOwnerIdForUpdate(claim.getPlayer().getId()).orElseGet(() ->
                            wallets.save(Wallet.create(claim.getPlayer(), claim.getCurrency())));
                    if (!"ETB".equals(wallet.getCurrency())) {
                        throw new ConflictException("DEPOSIT_WALLET_CURRENCY_MISMATCH",
                                "Player wallet currency does not match the verified deposit");
                    }
                    BigDecimal amount = MoneyValues.money(claim.getCalculatedCreditAmount());
                    if (amount.signum() <= 0) {
                        throw new BadRequestException("INVALID_DEPOSIT_AMOUNT",
                                "A deposit must have a positive credit amount");
                    }
                    BigDecimal before = MoneyValues.money(wallet.getAvailableBalance());
                    BigDecimal after = MoneyValues.money(before.add(amount));
                    Instant now = Instant.now();
                    String postingReference = "QA-PLAYER-DEPOSIT-" + depositId;
                    jdbc.update("""
                            insert into deposit_accounting_journals
                            (deposit_claim_id, posting_reference, currency, simulated, verifier_user_id,
                             approver_user_id, created_at)
                            values (?, ?, 'ETB', true, ?, ?, ?)
                            """, depositId, postingReference, verifier.getId(), approver.getId(), Timestamp.from(now));
                    Long journalId = jdbc.queryForObject("select last_insert_id()", Long.class);
                    jdbc.update("""
                            insert into deposit_accounting_journal_lines
                            (journal_id, line_number, account_code, debit_amount, credit_amount, currency)
                            values (?, 1, ?, ?, 0.00, 'ETB'), (?, 2, ?, 0.00, ?, 'ETB')
                            """, journalId, DEBIT_ACCOUNT, amount, journalId, CREDIT_ACCOUNT, amount);
                    assertBalancedJournal(journalId, amount);

                    wallet.setAvailableBalance(after);
                    wallets.save(wallet);
                    transactions.save(WalletTransaction.create(wallet, operationId,
                            WalletTransactionType.DEPOSIT_CREDIT, amount, before, after,
                            wallet.getPromotionalBalance(), wallet.getPromotionalBalance(), wallet.getCurrency(),
                            "DEPOSIT_CLAIM", claim.getId(), "Simulated QA posting " + postingReference,
                            "{\"journalId\":" + journalId + ",\"simulated\":true}", now));
                    claim.setStatus(DepositClaimStatus.APPROVED);
                    claim.setReviewedBy(approver);
                    claim.setReviewedAt(now);
                    claim.setReviewNote(reviewNote);
                    return toDepositResponse(claims.save(claim));
                });
    }

    @Transactional(readOnly = true)
    public List<DepositVerificationResponse> verificationHistory(Long depositId) {
        if (!claims.existsById(depositId)) {
            throw new NotFoundException("DEPOSIT_NOT_FOUND", "Deposit claim not found");
        }
        return jdbc.query("""
                select id from float_top_up_verification_attempts
                where deposit_claim_id=? order by id
                """, (rs, row) -> toVerification(rs.getLong(1)), depositId);
    }

    @Transactional(readOnly = true)
    public DepositPostingResponse getPosting(Long depositId) {
        return jdbc.query("""
                select id, deposit_claim_id, posting_reference, currency, simulated, verifier_user_id,
                       approver_user_id, created_at
                from deposit_accounting_journals where deposit_claim_id=?
                """, (rs, row) -> posting(rs), depositId).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("DEPOSIT_POSTING_NOT_FOUND",
                        "No financial posting exists for this deposit"));
    }

    private String matchOutcome(DepositClaim claim, BankStatement statement) {
        if (!statement.active()) {
            return "RECEIVING_ACCOUNT_INACTIVE";
        }
        if (!statement.playerDepositsEnabled()) {
            return "RECEIVING_ACCOUNT_NOT_APPROVED_FOR_PLAYER_DEPOSITS";
        }
        if (statement.synthetic() && !SYNTHETIC_SOURCE.equals(statement.source())) {
            return "UNTRUSTED_EVIDENCE";
        }
        if (!statement.synthetic() && !"BANK_STATEMENT_IMPORT".equals(statement.source())) {
            return "UNTRUSTED_EVIDENCE";
        }
        if (!referenceNormalizer.normalize(statement.reference())
                .equals(claim.getNormalizedTelebirrReference())) {
            return "REFERENCE_MISMATCH";
        }
        if (!"ETB".equals(statement.currency()) || !claim.getCurrency().equals(statement.currency())) {
            return "CURRENCY_MISMATCH";
        }
        if (MoneyValues.money(claim.getSubmittedCashAmount()).compareTo(MoneyValues.money(statement.amount())) != 0) {
            return "AMOUNT_MISMATCH";
        }
        if (!"SETTLED".equals(statement.settlementState())) {
            return "UNSETTLED_OR_REVERSED";
        }
        if (statement.transactionDate().isAfter(LocalDate.now())) {
            return "FUTURE_TRANSACTION_DATE";
        }
        return "MATCHED";
    }

    private long insertVerification(Long depositId, BankStatement statement, Long verifierId, String outcome) {
        jdbc.update("""
                insert into float_top_up_verification_attempts
                (request_id, deposit_claim_id, statement_entry_id, reviewer_user_id, outcome, exception_reason,
                 evidence_source, created_at)
                values (null, ?, ?, ?, ?, ?, ?, ?)
                """, depositId, statement.id(), verifierId, outcome,
                "MATCHED".equals(outcome) ? null : outcome,
                statement.synthetic() ? SYNTHETIC_SOURCE : statement.source(), Timestamp.from(Instant.now()));
        return jdbc.queryForObject("select last_insert_id()", Long.class);
    }

    private BankStatement findStatement(Long id) {
        return jdbc.query("""
                select s.id, s.receiving_bank_account_id, s.transaction_reference, s.amount, s.currency,
                       s.transaction_date, s.settlement_state, s.source, s.synthetic, a.active,
                       a.player_deposits_enabled
                from float_bank_statement_entries s
                join float_receiving_bank_accounts a on a.id=s.receiving_bank_account_id
                where s.id=?
                """, (rs, row) -> new BankStatement(rs.getLong("id"),
                    rs.getLong("receiving_bank_account_id"), rs.getString("transaction_reference"),
                    rs.getBigDecimal("amount"), rs.getString("currency"), rs.getDate("transaction_date").toLocalDate(),
                    rs.getString("settlement_state"), rs.getString("source"), rs.getBoolean("synthetic"),
                    rs.getBoolean("active"), rs.getBoolean("player_deposits_enabled")), id).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("BANK_STATEMENT_ENTRY_NOT_FOUND",
                        "Bank statement entry not found"));
    }

    private DepositVerificationResponse latestVerification(Long depositId) {
        Long id = jdbc.query("""
                select id from float_top_up_verification_attempts
                where deposit_claim_id=? order by id desc limit 1
                """, rs -> rs.next() ? rs.getLong(1) : null, depositId);
        if (id == null) {
            throw new ConflictException("DEPOSIT_VERIFICATION_REQUIRED",
                    "No independent bank verification record exists");
        }
        return toVerification(id);
    }

    private DepositVerificationResponse toVerification(Long id) {
        return jdbc.query("""
                select v.id, v.deposit_claim_id, v.statement_entry_id, v.reviewer_user_id, v.outcome,
                       v.evidence_source, v.created_at, s.receiving_bank_account_id, s.transaction_reference,
                       s.amount, s.currency, s.transaction_date, s.synthetic
                from float_top_up_verification_attempts v
                left join float_bank_statement_entries s on s.id=v.statement_entry_id
                where v.id=? and v.deposit_claim_id is not null
                """, (rs, row) -> new DepositVerificationResponse(rs.getLong("id"),
                    rs.getLong("deposit_claim_id"), nullableLong(rs, "statement_entry_id"),
                    nullableLong(rs, "receiving_bank_account_id"), rs.getString("transaction_reference"),
                    rs.getBigDecimal("amount"), rs.getString("currency"),
                    rs.getDate("transaction_date") == null ? null : rs.getDate("transaction_date").toLocalDate(),
                    rs.getString("outcome"), rs.getString("evidence_source"), rs.getBoolean("synthetic"),
                    rs.getLong("reviewer_user_id"), rs.getTimestamp("created_at").toInstant()), id).stream()
                .findFirst().orElseThrow(() -> new NotFoundException("DEPOSIT_VERIFICATION_NOT_FOUND",
                        "Deposit verification record not found"));
    }

    private DepositPostingResponse posting(ResultSet rs) throws SQLException {
        Long journalId = rs.getLong("id");
        List<DepositJournalLineResponse> lines = jdbc.query("""
                select line_number, account_code, debit_amount, credit_amount, currency
                from deposit_accounting_journal_lines where journal_id=? order by line_number
                """, (line, row) -> new DepositJournalLineResponse(line.getInt("line_number"),
                    line.getString("account_code"), line.getBigDecimal("debit_amount"),
                    line.getBigDecimal("credit_amount"), line.getString("currency")), journalId);
        BigDecimal balanceBefore = transactions.findFirstByReferenceTypeAndReferenceIdOrderByCreatedAtDescIdDesc(
                        "DEPOSIT_CLAIM", rs.getLong("deposit_claim_id"))
                .map(WalletTransaction::getTransferableBalanceBefore).orElse(BigDecimal.ZERO.setScale(2));
        BigDecimal balanceAfter = transactions.findFirstByReferenceTypeAndReferenceIdOrderByCreatedAtDescIdDesc(
                        "DEPOSIT_CLAIM", rs.getLong("deposit_claim_id"))
                .map(WalletTransaction::getTransferableBalanceAfter).orElse(balanceBefore);
        return new DepositPostingResponse(journalId, rs.getLong("deposit_claim_id"),
                rs.getString("posting_reference"), rs.getString("currency"), rs.getBoolean("simulated"),
                balanceBefore, balanceAfter, rs.getLong("verifier_user_id"), rs.getLong("approver_user_id"),
                rs.getTimestamp("created_at").toInstant(), lines);
    }

    private void assertBalancedJournal(Long journalId, BigDecimal amount) {
        List<BigDecimal> totals = jdbc.query("""
                select coalesce(sum(debit_amount),0), coalesce(sum(credit_amount),0)
                from deposit_accounting_journal_lines where journal_id=?
                """, (rs, row) -> List.of(rs.getBigDecimal(1), rs.getBigDecimal(2)), journalId)
                .stream().findFirst().orElseThrow();
        if (totals.get(0).compareTo(totals.get(1)) != 0 || totals.get(0).compareTo(amount) != 0) {
            throw new IllegalStateException("Player deposit journal is not balanced");
        }
    }

    private void requireExistingPosting(Long depositId) {
        if (jdbc.queryForObject("select count(*) from deposit_accounting_journals where deposit_claim_id=?",
                Integer.class, depositId) != 1) {
            throw new ConflictException("DEPOSIT_POSTING_INCONSISTENT",
                    "Approved deposit is missing its unique accounting journal");
        }
    }

    private DepositClaimResponse toDepositResponse(DepositClaim claim) {
        var instructions = paymentProvider.currentInstructions();
        return new DepositClaimResponse(claim.getId(), claim.getOperationId(), claim.getPlayer().getId(),
                claim.getSubmittedCashAmount(), claim.getCurrency(), claim.getConversionRateSnapshot(),
                claim.getCalculatedCreditAmount(), claim.getNormalizedTelebirrReference(), claim.getStatus().name(),
                claim.getSubmittedAt(), claim.getReviewedBy() == null ? null : claim.getReviewedBy().getId(),
                claim.getReviewedAt(), claim.getReviewNote(),
                claim.getVerifiedBy() == null ? null : claim.getVerifiedBy().getId(), claim.getVerifiedAt(),
                new PaymentInstructionsResponse(instructions.providerName(), instructions.businessAccount(),
                        instructions.paymentInstructions(), instructions.currency()));
    }

    private User requireRole(User user, UserRole role) {
        boolean staffUser = user.hasRole(UserRole.ADMIN) || user.hasRole(UserRole.SUPPORT);
        if (!user.isActive() || !staffUser || !user.hasRole(role)) {
            throw new ConflictException("FINANCE_AUTHORIZATION_REQUIRED",
                    "An active authorized finance user is required");
        }
        return user;
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private record BankStatement(Long id, Long receivingBankAccountId, String reference, BigDecimal amount,
            String currency, LocalDate transactionDate, String settlementState, String source,
            boolean synthetic, boolean active, boolean playerDepositsEnabled) { }

    private record DepositReviewPayload(String reviewNote) { }
}
