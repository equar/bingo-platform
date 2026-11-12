package com.bakaroo.bingo.backend.feature.game.service;

import com.bakaroo.bingo.backend.common.exception.ConflictException;
import com.bakaroo.bingo.backend.common.money.MoneyValues;
import com.bakaroo.bingo.backend.feature.agent.entity.*;
import com.bakaroo.bingo.backend.feature.agent.repository.*;
import com.bakaroo.bingo.backend.feature.game.entity.*;
import com.bakaroo.bingo.backend.feature.game.repository.TicketRepository;
import com.bakaroo.bingo.backend.feature.wallet.entity.*;
import com.bakaroo.bingo.backend.feature.wallet.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Refunds a cancelled, undrawn game to each player's wallet, preserving original promotional buckets. */
@Service
public class GameRefundService {
    private final TicketRepository tickets;
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final AgentFloatTransactionRepository floatTransactions;
    private final AgentFloatRepository floats;
    private final CommissionAccrualRepository commissions;

    public GameRefundService(TicketRepository tickets, WalletRepository wallets, WalletTransactionRepository transactions,
            AgentFloatTransactionRepository floatTransactions, AgentFloatRepository floats, CommissionAccrualRepository commissions) {
        this.tickets = tickets;
        this.wallets = wallets;
        this.transactions = transactions;
        this.floatTransactions = floatTransactions;
        this.floats = floats;
        this.commissions = commissions;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void refund(GameSession session) {
        for (Ticket ticket : tickets.findBySessionIdOrderByIdAsc(session.getId())) {
            List<WalletTransaction> purchases = transactions.findByReferenceTypeAndReferenceIdOrderByCreatedAtAscIdAsc("BINGO_TICKET", ticket.getId())
                    .stream().filter(tx -> tx.getTransactionType() == WalletTransactionType.TICKET_PURCHASE_DEBIT).toList();
            BigDecimal cash;
            BigDecimal promotional;
            if (purchases.size() == 1) {
                WalletTransaction original = purchases.getFirst();
                if (!ticket.getOperationId().equals(original.getOperationId())
                        || !ticket.getOwner().getId().equals(original.getWallet().getOwner().getId())
                        || !session.getCurrencySnapshot().equals(original.getCurrency())
                        || original.getTransferableBalanceBefore() == null || original.getTransferableBalanceAfter() == null
                        || original.getPromotionalBalanceBefore() == null || original.getPromotionalBalanceAfter() == null) {
                    throw new ConflictException("TICKET_REFUND_EVIDENCE_INVALID", "Reconcile the original ticket funding before refunding");
                }
                cash = original.getTransferableBalanceBefore().subtract(original.getTransferableBalanceAfter());
                promotional = original.getPromotionalBalanceBefore().subtract(original.getPromotionalBalanceAfter());
            } else if (purchases.isEmpty()) {
                AgentFloatTransaction original = floatTransactions.findByOperationId(ticket.getOperationId())
                        .filter(tx -> tx.getTransactionType() == AgentFloatTransactionType.TICKET_SALE_DEBIT)
                        .orElseThrow(() -> new ConflictException("TICKET_REFUND_EVIDENCE_MISSING", "Ticket funding evidence is required for a refund"));
                if (!original.getAgentFloat().getCurrency().equals(session.getCurrencySnapshot())) {
                    throw new ConflictException("CURRENCY_MISMATCH", "Original ticket funding currency does not match");
                }
                cash = original.getAmount();
                promotional = BigDecimal.ZERO;
                // Refund the player, not the selling agent. Reverse commission on the cancelled sale.
                commissions.findByTicketId(ticket.getId()).ifPresent(accrual -> {
                    if (accrual.getStatus() == CommissionAccrualStatus.SETTLED) {
                        AgentFloat balance = floats.findByAgentIdForUpdate(accrual.getAgent().getId()).orElseThrow();
                        BigDecimal before = balance.getAvailableBalance();
                        BigDecimal after = MoneyValues.money(before.subtract(accrual.getCommissionAmount()));
                        if (after.signum() < 0) throw new ConflictException("COMMISSION_REVERSAL_UNFUNDED", "Agent float must cover reversal of paid commission");
                        balance.setAvailableBalance(after);
                        floats.save(balance);
                        floatTransactions.save(AgentFloatTransaction.create(balance, AgentFloatTransactionType.COMMISSION_REVERSAL_DEBIT,
                                accrual.getCommissionAmount(), before, after, balance.getClearingBalance(), balance.getClearingBalance(),
                                "CANCELLED_TICKET", ticket.getId(), UUID.randomUUID().toString(), "Cancelled game commission reversal"));
                    }
                    accrual.setStatus(CommissionAccrualStatus.VOID);
                    accrual.setSettlementNote("Ticket refunded after game cancellation");
                    commissions.save(accrual);
                });
            } else {
                throw new ConflictException("TICKET_REFUND_EVIDENCE_INVALID", "Ticket has ambiguous funding evidence");
            }
            if (cash.signum() < 0 || promotional.signum() < 0
                    || cash.add(promotional).compareTo(session.getTicketPriceSnapshot()) != 0) {
                throw new ConflictException("TICKET_REFUND_EVIDENCE_INVALID", "Refund must equal the original ticket price");
            }
            wallets.findByOwnerId(ticket.getOwner().getId()).orElseGet(() -> wallets.save(Wallet.create(ticket.getOwner(), session.getCurrencySnapshot())));
            Wallet wallet = wallets.findByOwnerIdForUpdate(ticket.getOwner().getId()).orElseThrow();
            if (!wallet.getCurrency().equals(session.getCurrencySnapshot())) throw new ConflictException("CURRENCY_MISMATCH", "Refund currency must match player wallet");
            BigDecimal beforeCash = wallet.getAvailableBalance();
            BigDecimal beforePromo = wallet.getPromotionalBalance();
            wallet.setAvailableBalance(MoneyValues.money(beforeCash.add(cash)));
            wallet.setPromotionalBalance(MoneyValues.money(beforePromo.add(promotional)));
            wallets.save(wallet);
            transactions.save(WalletTransaction.create(wallet, UUID.randomUUID().toString(), WalletTransactionType.TICKET_REFUND_CREDIT,
                    cash.add(promotional), beforeCash, wallet.getAvailableBalance(), beforePromo, wallet.getPromotionalBalance(),
                    wallet.getCurrency(), "CANCELLED_TICKET", ticket.getId(), "Cancelled game ticket refund", null, Instant.now()));
        }
    }
}
