package com.equar.bingo.backend.feature.wallet.service;

import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.common.exception.NotFoundException;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.wallet.dto.WalletAdjustmentRequestResponse;
import com.equar.bingo.backend.feature.wallet.dto.WalletAdjustmentReviewRequest;
import com.equar.bingo.backend.feature.wallet.dto.WalletAdjustmentSubmissionRequest;
import com.equar.bingo.backend.feature.wallet.entity.Wallet;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransaction;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransactionType;
import com.equar.bingo.backend.feature.wallet.repository.WalletRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import com.equar.bingo.backend.common.money.MoneyValues;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WalletAdjustmentService {
    private static final String DEBIT_ACCOUNT = "WALLET_ADJUSTMENT_CLEARING";
    private static final String CREDIT_ACCOUNT = "PLAYER_WALLET_LIABILITY";

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final LocalFinancialSimulationGuard simulationGuard;
    private final boolean simulationEnabled;

    public WalletAdjustmentService(JdbcTemplate jdbc, UserRepository users, WalletRepository wallets,
            WalletTransactionRepository transactions, LocalFinancialSimulationGuard simulationGuard,
            @Value("${app.wallet.adjustment-simulation-enabled:false}") boolean simulationEnabled) {
        this.jdbc = jdbc;
        this.users = users;
        this.wallets = wallets;
        this.transactions = transactions;
        this.simulationGuard = simulationGuard;
        this.simulationEnabled = simulationEnabled;
        simulationGuard.validateConfiguration(simulationEnabled, "Wallet adjustment");
    }

    @Transactional
    public WalletAdjustmentRequestResponse submit(Long playerId, BingoUserPrincipal principal,
            WalletAdjustmentSubmissionRequest input) {
        User maker = requireRole(principal.getUser(), UserRole.FINANCE_VERIFIER);
        User player = users.findById(playerId)
                .orElseThrow(() -> new NotFoundException("PLAYER_NOT_FOUND", "Player account not found"));
        if (!player.hasRole(UserRole.PLAYER)) {
            throw new ConflictException("NOT_A_PLAYER", "Wallet adjustments can only target player accounts");
        }
        BigDecimal amount = MoneyValues.money(input.amount());
        if (amount.signum() == 0 || !"ETB".equals(input.currency())) {
            throw new ConflictException("INVALID_WALLET_ADJUSTMENT",
                    "Wallet adjustments require a non-zero amount in ETB");
        }
        String operationId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbc.update("""
                insert into wallet_adjustment_requests
                (player_id, maker_user_id, amount, currency, reason, supporting_reference, status,
                 operation_id, submitted_at)
                values (?, ?, ?, 'ETB', ?, ?, 'PENDING', ?, ?)
                """, playerId, maker.getId(), amount, input.reason().trim(), input.supportingReference().trim(),
                operationId, Timestamp.from(now));
        Long id = jdbc.queryForObject("select last_insert_id()", Long.class);
        return findRequest(id);
    }

    @Transactional
    public WalletAdjustmentRequestResponse approve(Long requestId, BingoUserPrincipal principal,
            WalletAdjustmentReviewRequest input) {
        User checker = requireRole(principal.getUser(), UserRole.FINANCE_APPROVER);
        AdjustmentRequest request = findForUpdate(requestId);
        if ("APPROVED".equals(request.status())) {
            return findRequest(requestId);
        }
        if (!"PENDING".equals(request.status())) {
            throw new ConflictException("WALLET_ADJUSTMENT_NOT_PENDING",
                    "Only pending adjustment requests can be approved");
        }
        if (checker.getId().equals(request.makerId())) {
            throw new ConflictException("WALLET_ADJUSTMENT_SEPARATION_OF_DUTIES_REQUIRED",
                    "The maker and checker must be different users");
        }
        if (!simulationEnabled) {
            throw new ConflictException("WALLET_ADJUSTMENT_POSTING_DISABLED",
                    "Wallet adjustment posting is disabled pending approved finance and accounting policy");
        }
        simulationGuard.requireEnabled(simulationEnabled, "Wallet adjustment");

        Wallet wallet = wallets.findByOwnerIdForUpdate(request.playerId())
                .orElseThrow(() -> new NotFoundException("WALLET_NOT_FOUND", "Player wallet not found"));
        BigDecimal before = MoneyValues.money(wallet.getAvailableBalance());
        BigDecimal after = MoneyValues.money(before.add(request.amount()));
        if (after.signum() < 0) {
            throw new ConflictException("INSUFFICIENT_BALANCE", "Wallet balance cannot go below zero");
        }
        Instant now = Instant.now();
        String postingReference = "QA-WALLET-ADJUSTMENT-" + requestId;
        jdbc.update("""
                insert into wallet_adjustment_accounting_journals
                (adjustment_request_id, posting_reference, currency, simulated, maker_user_id,
                 checker_user_id, created_at)
                values (?, ?, 'ETB', true, ?, ?, ?)
                """, requestId, postingReference, request.makerId(), checker.getId(), Timestamp.from(now));
        Long journalId = jdbc.queryForObject("select last_insert_id()", Long.class);
        boolean credit = request.amount().signum() > 0;
        String debitAccount = credit ? DEBIT_ACCOUNT : CREDIT_ACCOUNT;
        String creditAccount = credit ? CREDIT_ACCOUNT : DEBIT_ACCOUNT;
        BigDecimal magnitude = request.amount().abs();
        jdbc.update("""
                insert into wallet_adjustment_accounting_journal_lines
                (journal_id, line_number, account_code, debit_amount, credit_amount, currency)
                values (?, 1, ?, ?, 0.00, 'ETB'), (?, 2, ?, 0.00, ?, 'ETB')
                """, journalId, debitAccount, magnitude, journalId, creditAccount, magnitude);
        wallet.setAvailableBalance(after);
        wallets.save(wallet);
        transactions.save(WalletTransaction.create(wallet, request.operationId(),
                WalletTransactionType.WALLET_ADJUSTMENT, request.amount(), before, after,
                wallet.getPromotionalBalance(), wallet.getPromotionalBalance(), wallet.getCurrency(),
                "WALLET_ADJUSTMENT_REQUEST", requestId, input.reviewNote().trim(),
                "{\"journalId\":" + journalId + ",\"simulated\":true}", now));
        jdbc.update("""
                update wallet_adjustment_requests
                set status='APPROVED', checker_user_id=?, review_note=?, reviewed_at=?
                where id=? and status='PENDING'
                """, checker.getId(), input.reviewNote().trim(), Timestamp.from(now), requestId);
        return findRequest(requestId);
    }

    @Transactional
    public WalletAdjustmentRequestResponse reject(Long requestId, BingoUserPrincipal principal,
            WalletAdjustmentReviewRequest input) {
        User checker = requireRole(principal.getUser(), UserRole.FINANCE_APPROVER);
        AdjustmentRequest request = findForUpdate(requestId);
        if (!"PENDING".equals(request.status())) {
            throw new ConflictException("WALLET_ADJUSTMENT_NOT_PENDING",
                    "Only pending adjustment requests can be rejected");
        }
        if (checker.getId().equals(request.makerId())) {
            throw new ConflictException("WALLET_ADJUSTMENT_SEPARATION_OF_DUTIES_REQUIRED",
                    "The maker and checker must be different users");
        }
        jdbc.update("""
                update wallet_adjustment_requests
                set status='REJECTED', checker_user_id=?, review_note=?, reviewed_at=?
                where id=? and status='PENDING'
                """, checker.getId(), input.reviewNote().trim(), Timestamp.from(Instant.now()), requestId);
        return findRequest(requestId);
    }

    @Transactional(readOnly = true)
    public List<WalletAdjustmentRequestResponse> history(Long playerId) {
        List<Long> ids = jdbc.query(
                "select id from wallet_adjustment_requests where player_id=? order by submitted_at desc, id desc",
                (rs, row) -> rs.getLong(1), playerId);
        return ids.stream().map(this::findRequest).toList();
    }

    @Transactional(readOnly = true)
    public List<WalletAdjustmentRequestResponse> pending() {
        List<Long> ids = jdbc.query(
                "select id from wallet_adjustment_requests where status='PENDING' order by submitted_at, id",
                (rs, row) -> rs.getLong(1));
        return ids.stream().map(this::findRequest).toList();
    }

    private WalletAdjustmentRequestResponse findRequest(Long id) {
        return jdbc.query("""
                select r.id, r.player_id, r.maker_user_id, r.amount, r.currency, r.reason,
                       r.supporting_reference, r.status, r.operation_id, r.checker_user_id,
                       r.review_note, r.submitted_at, r.reviewed_at, j.id journal_id,
                       j.posting_reference, t.transferable_balance_before, t.transferable_balance_after,
                       coalesce(j.simulated, false) simulated
                from wallet_adjustment_requests r
                left join wallet_adjustment_accounting_journals j on j.adjustment_request_id=r.id
                left join wallet_transactions t on t.reference_type='WALLET_ADJUSTMENT_REQUEST'
                    and t.reference_id=r.id
                where r.id=?
                """, (rs, row) -> response(rs), id).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("WALLET_ADJUSTMENT_NOT_FOUND",
                        "Wallet adjustment request not found"));
    }

    private WalletAdjustmentRequestResponse response(ResultSet rs) throws SQLException {
        return new WalletAdjustmentRequestResponse(rs.getLong("id"), rs.getLong("player_id"),
                rs.getLong("maker_user_id"), rs.getBigDecimal("amount"), rs.getString("currency"),
                rs.getString("reason"), rs.getString("supporting_reference"), rs.getString("status"),
                rs.getString("operation_id"), nullableLong(rs, "checker_user_id"), rs.getString("review_note"),
                rs.getTimestamp("submitted_at").toInstant(),
                rs.getTimestamp("reviewed_at") == null ? null : rs.getTimestamp("reviewed_at").toInstant(),
                nullableLong(rs, "journal_id"), rs.getString("posting_reference"),
                rs.getBigDecimal("transferable_balance_before"), rs.getBigDecimal("transferable_balance_after"),
                rs.getBoolean("simulated"));
    }

    private AdjustmentRequest findForUpdate(Long id) {
        return jdbc.query("""
                select id, player_id, maker_user_id, amount, status, operation_id
                from wallet_adjustment_requests where id=? for update
                """, (rs, row) -> new AdjustmentRequest(rs.getLong("id"), rs.getLong("player_id"),
                rs.getLong("maker_user_id"), rs.getBigDecimal("amount"), rs.getString("status"),
                rs.getString("operation_id")), id).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("WALLET_ADJUSTMENT_NOT_FOUND",
                        "Wallet adjustment request not found"));
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

    private record AdjustmentRequest(Long id, Long playerId, Long makerId, BigDecimal amount, String status,
            String operationId) { }
}
