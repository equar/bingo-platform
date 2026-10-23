package com.equar.bingo.backend.feature.agent.service;

import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.common.exception.NotFoundException;
import com.equar.bingo.backend.feature.agent.dto.FloatPostingResponse;
import com.equar.bingo.backend.feature.agent.dto.FloatReconciliationResponse;
import com.equar.bingo.backend.feature.agent.dto.FloatVerificationRequest;
import com.equar.bingo.backend.feature.agent.dto.FloatVerificationResponse;
import com.equar.bingo.backend.feature.agent.dto.SyntheticBankStatementRequest;
import com.equar.bingo.backend.feature.agent.entity.AgentFloat;
import com.equar.bingo.backend.feature.agent.entity.AgentFloatTransaction;
import com.equar.bingo.backend.feature.agent.entity.AgentFloatTransactionType;
import com.equar.bingo.backend.feature.agent.entity.FloatTopUpRequest;
import com.equar.bingo.backend.feature.agent.entity.FloatTopUpRequestStatus;
import com.equar.bingo.backend.feature.agent.repository.AgentFloatRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentFloatTransactionRepository;
import com.equar.bingo.backend.feature.agent.repository.FloatTopUpRequestRepository;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FloatFundingService {
    private static final String SYNTHETIC_SOURCE = "SYNTHETIC_QA_BANK_STATEMENT";
    private static final String DEBIT_ACCOUNT = "QA_BANK_CLEARING_ASSET";
    private static final String CREDIT_ACCOUNT = "AGENT_PREPAID_FLOAT_LIABILITY";

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final Environment environment;
    private final FloatTopUpRequestRepository requests;
    private final AgentFloatRepository floats;
    private final AgentFloatTransactionRepository floatTransactions;
    private final UserRepository users;
    private final boolean simulationEnabled;

    public FloatFundingService(JdbcTemplate jdbc, DataSource dataSource, Environment environment,
            FloatTopUpRequestRepository requests, AgentFloatRepository floats,
            AgentFloatTransactionRepository floatTransactions, UserRepository users,
            @Value("${app.agent.float-simulation-enabled:false}") boolean simulationEnabled) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.environment = environment;
        this.requests = requests;
        this.floats = floats;
        this.floatTransactions = floatTransactions;
        this.users = users;
        this.simulationEnabled = simulationEnabled;
        if (simulationEnabled && !environment.acceptsProfiles(org.springframework.core.env.Profiles.of("qa", "test"))) {
            throw new IllegalStateException("Float simulation can only be enabled in local QA/test profiles");
        }
    }

    @Transactional
    public FloatVerificationResponse recordSyntheticStatement(BingoUserPrincipal principal,
            SyntheticBankStatementRequest request) {
        requireRole(principal.getUser(), UserRole.FINANCE_VERIFIER);
        requireSimulationEnabled();
        validateReceivingAccount(request.receivingBankAccountId());
        if (!"ETB".equals(request.currency())) throw new ConflictException("UNSUPPORTED_CURRENCY", "Only ETB is supported");
        Instant now = Instant.now();
        try {
            jdbc.update("""
                    insert into float_bank_statement_entries
                    (receiving_bank_account_id, transaction_reference, amount, currency, transaction_date,
                     settlement_state, source, synthetic, recorded_by_user_id, recorded_at)
                    values (?, ?, ?, 'ETB', ?, ?, ?, true, ?, ?)
                    """, request.receivingBankAccountId(), request.transactionReference().trim(), request.amount(),
                    request.transactionDate(), request.settlementState(), SYNTHETIC_SOURCE,
                    principal.getUser().getId(), Timestamp.from(now));
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("DUPLICATE_BANK_REFERENCE",
                    "That bank transaction reference already exists for this receiving account");
        }
        Long id = jdbc.queryForObject("select last_insert_id()", Long.class);
        return statementAsVerification(id, null, principal.getUser().getId(), "STATEMENT_RECORDED", null, now);
    }

    @Transactional
    public FloatVerificationResponse verify(Long requestId, BingoUserPrincipal principal,
            FloatVerificationRequest input) {
        User reviewer = requireRole(principal.getUser(), UserRole.FINANCE_VERIFIER);
        requireSimulationEnabled();
        FloatTopUpRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new NotFoundException("TOP_UP_REQUEST_NOT_FOUND", "Float top-up request not found"));
        if (request.getStatus() != FloatTopUpRequestStatus.PENDING
                && request.getStatus() != FloatTopUpRequestStatus.VERIFICATION_EXCEPTION) {
            throw new ConflictException("TOP_UP_NOT_VERIFIABLE", "Only pending or exception requests can be verified");
        }

        if (input.statementEntryId() == null) {
            String outcome = input.exceptionOutcome();
            if (outcome == null || outcome.isBlank() || "MATCHED".equals(outcome)) {
                throw new ConflictException("INDEPENDENT_STATEMENT_REQUIRED",
                        "An independently recorded bank statement entry is required to verify a payment");
            }
            if (input.exceptionReason() == null || input.exceptionReason().isBlank()) {
                throw new ConflictException("EXCEPTION_REASON_REQUIRED", "A reason is required for a verification exception");
            }
            long attemptId = insertAttempt(requestId, null, reviewer.getId(), outcome,
                    input.exceptionReason().trim(), "NO_INDEPENDENT_STATEMENT");
            request.setStatus(FloatTopUpRequestStatus.VERIFICATION_EXCEPTION);
            requests.save(request);
            return verificationAttempt(attemptId);
        }

        StatementEntry entry = findStatement(input.statementEntryId());
        String outcome = matchOutcome(request, entry);
        String reason = outcome.equals("MATCHED") ? null : automaticExceptionReason(outcome);
        if (outcome.equals("DUPLICATE_REFERENCE")) {
            throw new ConflictException("DUPLICATE_BANK_REFERENCE", "Bank transaction reference has already been applied");
        }
        long attemptId;
        try {
            attemptId = insertAttempt(requestId, entry.id(), reviewer.getId(), outcome, reason,
                    entry.synthetic() ? SYNTHETIC_SOURCE : "BANK_STATEMENT");
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("BANK_STATEMENT_ALREADY_VERIFIED", "Bank statement entry has already been used");
        }
        request.setStatus(outcome.equals("MATCHED")
                ? FloatTopUpRequestStatus.VERIFIED : FloatTopUpRequestStatus.VERIFICATION_EXCEPTION);
        requests.save(request);
        return verificationAttempt(attemptId);
    }

    @Transactional
    public FloatPostingResponse postVerifiedRequest(Long requestId, BingoUserPrincipal principal, String reviewNote) {
        User approver = requireRole(principal.getUser(), UserRole.FINANCE_APPROVER);
        requireSimulationEnabled();
        FloatTopUpRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new NotFoundException("TOP_UP_REQUEST_NOT_FOUND", "Float top-up request not found"));
        if (request.getStatus() == FloatTopUpRequestStatus.APPROVED) {
            return findPosting(requestId);
        }
        if (request.getStatus() != FloatTopUpRequestStatus.VERIFIED) {
            throw new ConflictException("TOP_UP_NOT_VERIFIED", "Only independently verified requests can be posted");
        }
        if (request.getReceivingBankAccountId() == null || !"ETB".equals(request.getCurrency())) {
            throw new ConflictException("TOP_UP_ACCOUNTING_DATA_INVALID", "Request lacks an eligible ETB receiving account");
        }
        if (request.getAgent().getStatus().name().equals("SUSPENDED") || !request.getAgent().getUser().isActive()) {
            throw new ConflictException("AGENT_NOT_ELIGIBLE_FOR_FLOAT", "Suspended or inactive agents cannot receive float");
        }
        FloatVerificationResponse latest = latestVerification(requestId);
        if (!"MATCHED".equals(latest.outcome())) {
            throw new ConflictException("TOP_UP_NOT_VERIFIED", "A matched independent bank statement is required");
        }
        if (latest.reviewerUserId().equals(approver.getId())) {
            throw new ConflictException("FINANCE_SEPARATION_OF_DUTIES_REQUIRED",
                    "The verifier and approver must be different users");
        }
        User verifier = users.findById(latest.reviewerUserId()).orElseThrow();
        if (!verifier.isActive() || !verifier.hasRole(UserRole.FINANCE_VERIFIER)) {
            throw new ConflictException("FINANCE_VERIFIER_NOT_AUTHORIZED", "The verifying user is no longer authorized");
        }

        AgentFloat agentFloat = floats.findByAgentIdForUpdate(request.getAgent().getId())
                .orElseThrow(() -> new NotFoundException("AGENT_FLOAT_NOT_FOUND", "Agent float not found"));
        if (!"ETB".equals(agentFloat.getCurrency())) {
            throw new ConflictException("UNSUPPORTED_CURRENCY", "Only ETB float funding can be posted");
        }
        BigDecimal amount = request.getAmount();
        BigDecimal before = agentFloat.getAvailableBalance();
        BigDecimal after = before.add(amount).setScale(2, java.math.RoundingMode.UNNECESSARY);
        String postingReference = "QA-FLOAT-TOPUP-" + requestId;
        Instant now = Instant.now();
        jdbc.update("""
                insert into float_accounting_journals
                (request_id, posting_reference, currency, simulated, approver_user_id, verifier_user_id, created_at)
                values (?, ?, 'ETB', true, ?, ?, ?)
                """, requestId, postingReference, approver.getId(), verifier.getId(), Timestamp.from(now));
        Long journalId = jdbc.queryForObject("select last_insert_id()", Long.class);
        jdbc.update("""
                insert into float_accounting_journal_lines
                (journal_id, line_number, account_code, debit_amount, credit_amount, currency)
                values (?, 1, ?, ?, 0.00, 'ETB'), (?, 2, ?, 0.00, ?, 'ETB')
                """, journalId, DEBIT_ACCOUNT, amount, journalId, CREDIT_ACCOUNT, amount);
        BigDecimal totalDebit = jdbc.queryForObject(
                "select coalesce(sum(debit_amount),0) from float_accounting_journal_lines where journal_id=?",
                BigDecimal.class, journalId);
        BigDecimal totalCredit = jdbc.queryForObject(
                "select coalesce(sum(credit_amount),0) from float_accounting_journal_lines where journal_id=?",
                BigDecimal.class, journalId);
        if (totalDebit.compareTo(totalCredit) != 0 || totalDebit.compareTo(amount) != 0) {
            throw new IllegalStateException("Float top-up journal is not balanced");
        }

        agentFloat.setAvailableBalance(after);
        floats.save(agentFloat);
        String operationId = UUID.nameUUIDFromBytes(("FLOAT-TOP-UP-" + requestId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        floatTransactions.save(AgentFloatTransaction.create(agentFloat, AgentFloatTransactionType.TOP_UP, amount,
                before, after, agentFloat.getClearingBalance(), agentFloat.getClearingBalance(),
                "FLOAT_TOP_UP_REQUEST", requestId, operationId,
                "SIMULATED QA bank transfer funding; journal " + journalId));
        request.setStatus(FloatTopUpRequestStatus.APPROVED);
        request.setReviewedBy(approver);
        request.setReviewedAt(now);
        request.setReviewNote(reviewNote == null || reviewNote.isBlank()
                ? "Simulated QA posting " + postingReference : reviewNote.trim());
        requests.save(request);
        return new FloatPostingResponse(journalId, requestId, postingReference, true, "ETB", amount,
                after, verifier.getId(), approver.getId(), now);
    }

    @Transactional(readOnly = true)
    public FloatPostingResponse findPosting(Long requestId) {
        return jdbc.query("""
                select j.id journal_id, j.request_id, j.posting_reference, j.simulated, j.currency,
                       r.amount, f.available_balance, j.verifier_user_id, j.approver_user_id, j.created_at
                from float_accounting_journals j
                join agent_float_top_up_requests r on r.id=j.request_id
                join agent_floats f on f.agent_id=r.agent_id
                where j.request_id=?
                """, (rs, row) -> posting(rs), requestId).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("FLOAT_POSTING_NOT_FOUND", "Float posting not found for request " + requestId));
    }

    @Transactional(readOnly = true)
    public List<FloatVerificationResponse> verificationHistory(Long requestId) {
        return jdbc.query("select id from float_top_up_verification_attempts where request_id=? order by id",
                (rs, row) -> verificationAttempt(rs.getLong(1)), requestId);
    }

    @Transactional(readOnly = true)
    public List<FloatPostingResponse> postings() {
        return jdbc.query("select request_id from float_accounting_journals order by id desc",
                (rs, row) -> findPosting(rs.getLong(1)));
    }

    @Transactional(readOnly = true)
    public FloatReconciliationResponse reconcile(LocalDate date) {
        long statementCount = scalarLong("select count(*) from float_bank_statement_entries where synthetic=true and transaction_date=? and settlement_state='SETTLED'", date);
        BigDecimal statementAmount = scalarMoney("select coalesce(sum(amount),0) from float_bank_statement_entries where synthetic=true and transaction_date=? and settlement_state='SETTLED'", date);
        long postingCount = scalarLong("select count(*) from float_accounting_journals where simulated=true and date(created_at)=?", date);
        BigDecimal postedAmount = scalarMoney("""
                select coalesce(sum(r.amount),0) from float_accounting_journals j
                join agent_float_top_up_requests r on r.id=j.request_id
                where j.simulated=true and date(j.created_at)=?
                """, date);
        BigDecimal debits = scalarMoney("""
                select coalesce(sum(l.debit_amount),0) from float_accounting_journal_lines l
                join float_accounting_journals j on j.id=l.journal_id
                where j.simulated=true and date(j.created_at)=?
                """, date);
        BigDecimal credits = scalarMoney("""
                select coalesce(sum(l.credit_amount),0) from float_accounting_journal_lines l
                join float_accounting_journals j on j.id=l.journal_id
                where j.simulated=true and date(j.created_at)=?
                """, date);
        long unmatched = scalarLong("""
                select count(*) from float_bank_statement_entries s
                left join float_top_up_verification_attempts v on v.statement_entry_id=s.id and v.outcome='MATCHED'
                where s.synthetic=true and s.transaction_date=? and s.settlement_state='SETTLED' and v.id is null
                """, date);
        long verifiedUnposted = scalarLong("select count(*) from agent_float_top_up_requests where status='VERIFIED' and date(updated_at)=?", date);
        long exceptions = scalarLong("select count(*) from float_top_up_verification_attempts where outcome<>'MATCHED' and date(created_at)=?", date);
        BigDecimal difference = statementAmount.subtract(postedAmount).setScale(2, java.math.RoundingMode.HALF_UP);
        boolean balanced = debits.compareTo(credits) == 0 && difference.compareTo(BigDecimal.ZERO) == 0
                && unmatched == 0 && verifiedUnposted == 0 && exceptions == 0;
        return new FloatReconciliationResponse(date, true, statementCount, statementAmount, postingCount,
                postedAmount, debits, credits, unmatched, verifiedUnposted, exceptions, difference, balanced);
    }

    private long scalarLong(String sql, LocalDate date) {
        Long value = jdbc.queryForObject(sql, Long.class, date);
        return value == null ? 0L : value;
    }

    private BigDecimal scalarMoney(String sql, LocalDate date) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, date);
        return value == null ? BigDecimal.ZERO.setScale(2) : value;
    }

    private long insertAttempt(Long requestId, Long statementId, Long reviewerId, String outcome,
            String reason, String source) {
        jdbc.update("""
                insert into float_top_up_verification_attempts
                (request_id, statement_entry_id, reviewer_user_id, outcome, exception_reason, evidence_source, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, requestId, statementId, reviewerId, outcome, reason, source, Timestamp.from(Instant.now()));
        return jdbc.queryForObject("select last_insert_id()", Long.class);
    }

    private FloatVerificationResponse verificationAttempt(Long id) {
        return jdbc.queryForObject("""
                select v.id, v.request_id, v.statement_entry_id, v.reviewer_user_id, v.outcome,
                       v.exception_reason, v.evidence_source, s.receiving_bank_account_id,
                       s.transaction_reference, s.amount, s.transaction_date, v.created_at, coalesce(s.synthetic,false) synthetic
                from float_top_up_verification_attempts v left join float_bank_statement_entries s
                  on s.id=v.statement_entry_id where v.id=?
                """, (rs, row) -> verification(rs), id);
    }

    private FloatVerificationResponse latestVerification(Long requestId) {
        Long id = jdbc.query("select id from float_top_up_verification_attempts where request_id=? order by id desc limit 1",
                rs -> rs.next() ? rs.getLong(1) : null, requestId);
        if (id == null) throw new ConflictException("TOP_UP_NOT_VERIFIED", "No independent verification record exists");
        return verificationAttempt(id);
    }

    private FloatVerificationResponse statementAsVerification(Long id, Long requestId, Long reviewerId,
            String outcome, String reason, Instant now) {
        StatementEntry statement = findStatement(id);
        return new FloatVerificationResponse(null, requestId, id, reviewerId, outcome, reason, SYNTHETIC_SOURCE,
                statement.accountId(), statement.reference(), statement.amount(), statement.date(), now, true);
    }

    private StatementEntry findStatement(Long id) {
        return jdbc.query("""
                select id, receiving_bank_account_id, transaction_reference, amount, currency, transaction_date,
                       settlement_state, source, synthetic from float_bank_statement_entries where id=?
                """, (rs, row) -> new StatementEntry(rs.getLong("id"), rs.getLong("receiving_bank_account_id"),
                    rs.getString("transaction_reference"), rs.getBigDecimal("amount"), rs.getString("currency"),
                    rs.getDate("transaction_date").toLocalDate(), rs.getString("settlement_state"),
                    rs.getString("source"), rs.getBoolean("synthetic")), id).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("BANK_STATEMENT_ENTRY_NOT_FOUND", "Bank statement entry not found"));
    }

    private String matchOutcome(FloatTopUpRequest request, StatementEntry entry) {
        if (!entry.synthetic() || !SYNTHETIC_SOURCE.equals(entry.source())) return "UNTRUSTED_EVIDENCE";
        if (!request.getReceivingBankAccountId().equals(entry.accountId())) return "WRONG_RECEIVING_ACCOUNT";
        if (!request.getTransferReference().equals(entry.reference())) return "CONFLICTING_EVIDENCE";
        if (!"ETB".equals(entry.currency()) || request.getAmount().compareTo(entry.amount()) != 0) return "AMOUNT_MISMATCH";
        if (!"SETTLED".equals(entry.settlementState())) return "UNSETTLED_OR_REVERSED";
        if (!request.getAgent().getStatus().name().equals("APPROVED") || !request.getAgent().getUser().isActive()) {
            return "SUSPENDED_AGENT";
        }
        return "MATCHED";
    }

    private String automaticExceptionReason(String outcome) {
        return switch (outcome) {
            case "WRONG_RECEIVING_ACCOUNT" -> "Bank statement account does not match the selected receiving account";
            case "CONFLICTING_EVIDENCE" -> "Bank statement reference does not match the agent-submitted reference";
            case "AMOUNT_MISMATCH" -> "Bank statement amount or currency does not match the request";
            case "UNSETTLED_OR_REVERSED" -> "Bank transaction is not settled";
            case "SUSPENDED_AGENT" -> "Agent is not active and approved";
            default -> "Evidence is not from a trusted independent source";
        };
    }

    private void validateReceivingAccount(Long accountId) {
        Integer count = jdbc.queryForObject("select count(*) from float_receiving_bank_accounts where id=? and active=true",
                Integer.class, accountId);
        if (count == null || count != 1) throw new ConflictException("FLOAT_BANK_ACCOUNT_INACTIVE", "Receiving account is not active");
    }

    private User requireRole(User user, UserRole role) {
        if (!user.isActive() || !user.hasRole(role)) {
            throw new ConflictException("FINANCE_AUTHORIZATION_REQUIRED", "An active authorized finance user is required");
        }
        return user;
    }

    private void requireSimulationEnabled() {
        if (!simulationEnabled || !environment.acceptsProfiles(org.springframework.core.env.Profiles.of("qa", "test"))) {
            throw new ConflictException("FLOAT_SIMULATION_DISABLED", "Synthetic bank verification and float posting are disabled");
        }
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery("select database()")) {
            if (!result.next()) throw new IllegalStateException("Cannot identify connected database");
            String catalog = result.getString(1);
            if (!"bingo_qa".equals(catalog) && !"bingo_qa_test".equals(catalog)) {
                throw new ConflictException("FLOAT_SIMULATION_DATABASE_DENIED",
                        "Synthetic float posting is restricted to the approved QA databases");
            }
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Unable to validate simulation database", exception);
        }
    }

    private FloatPostingResponse posting(ResultSet rs) throws SQLException {
        return new FloatPostingResponse(rs.getLong("journal_id"), rs.getLong("request_id"),
                rs.getString("posting_reference"), rs.getBoolean("simulated"), rs.getString("currency"),
                rs.getBigDecimal("amount"), rs.getBigDecimal("available_balance"),
                rs.getLong("verifier_user_id"), rs.getLong("approver_user_id"), rs.getTimestamp("created_at").toInstant());
    }

    private FloatVerificationResponse verification(ResultSet rs) throws SQLException {
        Long statementId = (Long) rs.getObject("statement_entry_id");
        return new FloatVerificationResponse(rs.getLong("id"), rs.getLong("request_id"), statementId,
                rs.getLong("reviewer_user_id"), rs.getString("outcome"), rs.getString("exception_reason"),
                rs.getString("evidence_source"), (Long) rs.getObject("receiving_bank_account_id"),
                rs.getString("transaction_reference"), rs.getBigDecimal("amount"),
                rs.getDate("transaction_date") == null ? null : rs.getDate("transaction_date").toLocalDate(),
                rs.getTimestamp("created_at").toInstant(), rs.getBoolean("synthetic"));
    }

    private record StatementEntry(Long id, Long accountId, String reference, BigDecimal amount, String currency,
            LocalDate date, String settlementState, String source, boolean synthetic) {}
}
