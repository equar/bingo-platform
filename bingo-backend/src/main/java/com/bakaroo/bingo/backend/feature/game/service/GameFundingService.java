package com.bakaroo.bingo.backend.feature.game.service;

import com.bakaroo.bingo.backend.common.exception.ConflictException;
import com.bakaroo.bingo.backend.feature.game.entity.GameSession;
import com.bakaroo.bingo.backend.feature.game.model.BingoPrizeConfig;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/** A session's sales and commission ledger are frozen by its session lock at start. */
@Service
public class GameFundingService {
    private final EntityManager entityManager;
    private final GameSnapshotMapper snapshots;

    public GameFundingService(EntityManager entityManager, GameSnapshotMapper snapshots) {
        this.entityManager = entityManager;
        this.snapshots = snapshots;
    }

    public FundingSummary summarize(GameSession session) {
        BigDecimal walletSales = sum("""
                select sum(w.transferableBalanceBefore - w.transferableBalanceAfter)
                from WalletTransaction w, Ticket t
                where t.session.id = :id and w.operationId = t.operationId
                  and w.referenceType = 'BINGO_TICKET' and w.referenceId = t.id
                  and w.transactionType = com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransactionType.TICKET_PURCHASE_DEBIT
                  and w.currency = t.session.currencySnapshot
                """, session.getId());
        BigDecimal agentSales = sum("""
                select sum(f.amount) from AgentFloatTransaction f, Ticket t
                where t.session.id = :id and f.operationId = t.operationId
                  and f.transactionType = com.bakaroo.bingo.backend.feature.agent.entity.AgentFloatTransactionType.TICKET_SALE_DEBIT
                  and f.agentFloat.currency = t.session.currencySnapshot
                """, session.getId());
        BigDecimal commissions = sum("select sum(c.commissionAmount) from CommissionAccrual c where c.sessionId = :id",
                session.getId());
        BigDecimal revenue = walletSales.add(agentSales);
        BigDecimal liability = snapshots.readPrizes(session.getPrizesSnapshotJson()).stream()
                .map(BingoPrizeConfig::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal costs = session.getSessionCostReserve() == null ? BigDecimal.ZERO : session.getSessionCostReserve();
        int margin = session.getMinimumHouseMarginBps() == null ? 10000 : session.getMinimumHouseMarginBps();
        BigDecimal retained = revenue.multiply(BigDecimal.valueOf(margin)).divide(BigDecimal.valueOf(10000), 2, RoundingMode.CEILING);
        BigDecimal required = liability.add(commissions).add(costs).add(retained);
        return new FundingSummary(revenue, commissions, liability, costs, retained, required,
                revenue.signum() > 0 && revenue.compareTo(required) >= 0);
    }

    public void requireFunded(GameSession session) {
        if (!"EARLIEST_SPLIT_V1".equals(session.getRulesVersion())) {
            throw new ConflictException("LEGACY_SESSION_REQUIRES_REVIEW", "Legacy game terms require reconciliation before operation");
        }
        FundingSummary summary = summarize(session);
        if (!summary.funded()) {
            throw new ConflictException("SESSION_UNDERFUNDED",
                    "Cash-backed sales must cover the prize pools, commissions, cost reserve, and configured house margin");
        }
    }

    private BigDecimal sum(String query, Long id) {
        BigDecimal result = entityManager.createQuery(query, BigDecimal.class).setParameter("id", id).getSingleResult();
        return result == null ? BigDecimal.ZERO : result;
    }

    public record FundingSummary(BigDecimal cashBackedRevenue, BigDecimal commissionLiability,
            BigDecimal prizeLiability, BigDecimal costReserve, BigDecimal requiredHouseRetention,
            BigDecimal totalRequired, boolean funded) { }
}
