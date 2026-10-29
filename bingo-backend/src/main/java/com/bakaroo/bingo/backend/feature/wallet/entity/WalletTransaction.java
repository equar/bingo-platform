package com.bakaroo.bingo.backend.feature.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(name = "operation_id", nullable = false, length = 36)
    private String operationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 40)
    private WalletTransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "transferable_balance_before", precision = 19, scale = 2)
    private BigDecimal transferableBalanceBefore;

    @Column(name = "transferable_balance_after", precision = 19, scale = 2)
    private BigDecimal transferableBalanceAfter;

    @Column(name = "promotional_balance_before", precision = 19, scale = 2)
    private BigDecimal promotionalBalanceBefore;

    @Column(name = "promotional_balance_after", precision = 19, scale = 2)
    private BigDecimal promotionalBalanceAfter;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "reference_type", length = 40)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WalletTransaction() {
    }

    public static WalletTransaction create(Wallet wallet,
            String operationId,
            WalletTransactionType transactionType,
            BigDecimal amount,
            BigDecimal transferableBalanceBefore,
            BigDecimal transferableBalanceAfter,
            BigDecimal promotionalBalanceBefore,
            BigDecimal promotionalBalanceAfter,
            String currency,
            String referenceType,
            Long referenceId,
            String note,
            String metadataJson,
            Instant createdAt) {
        Objects.requireNonNull(transferableBalanceBefore, "transferableBalanceBefore");
        Objects.requireNonNull(transferableBalanceAfter, "transferableBalanceAfter");
        Objects.requireNonNull(promotionalBalanceBefore, "promotionalBalanceBefore");
        Objects.requireNonNull(promotionalBalanceAfter, "promotionalBalanceAfter");

        WalletTransaction transaction = new WalletTransaction();
        transaction.wallet = wallet;
        transaction.operationId = operationId;
        transaction.transactionType = transactionType;
        transaction.amount = amount;
        transaction.balanceBefore = transferableBalanceBefore.add(promotionalBalanceBefore);
        transaction.balanceAfter = transferableBalanceAfter.add(promotionalBalanceAfter);
        transaction.currency = currency;
        transaction.referenceType = referenceType;
        transaction.referenceId = referenceId;
        transaction.note = note;
        transaction.transferableBalanceBefore = transferableBalanceBefore;
        transaction.transferableBalanceAfter = transferableBalanceAfter;
        transaction.promotionalBalanceBefore = promotionalBalanceBefore;
        transaction.promotionalBalanceAfter = promotionalBalanceAfter;
        transaction.metadataJson = metadataJson;
        transaction.createdAt = createdAt;
        return transaction;
    }

    public Long getId() {
        return id;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public String getOperationId() {
        return operationId;
    }

    public WalletTransactionType getTransactionType() {
        return transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceBefore() {
        return balanceBefore;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public BigDecimal getTransferableBalanceBefore() {
        return transferableBalanceBefore;
    }

    public BigDecimal getTransferableBalanceAfter() {
        return transferableBalanceAfter;
    }

    public BigDecimal getPromotionalBalanceBefore() {
        return promotionalBalanceBefore;
    }

    public BigDecimal getPromotionalBalanceAfter() {
        return promotionalBalanceAfter;
    }

    public String getCurrency() {
        return currency;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public String getNote() {
        return note;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}