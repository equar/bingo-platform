package com.equar.bingo.backend.feature.wallet.entity;

import com.equar.bingo.backend.feature.user.entity.User;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallet_transfers", uniqueConstraints = {
        @UniqueConstraint(name = "uk_wallet_transfers_operation", columnNames = "operation_id")
})
public class WalletTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_id", nullable = false, length = 36)
    private String operationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(name = "sender_phone_snapshot", nullable = false, length = 32)
    private String senderPhoneSnapshot;

    @Column(name = "recipient_phone_snapshot", nullable = false, length = 32)
    private String recipientPhoneSnapshot;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "note", length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WalletTransferStatus status;

    @Column(name = "sender_balance_before", nullable = false, precision = 19, scale = 2)
    private BigDecimal senderBalanceBefore;

    @Column(name = "sender_balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal senderBalanceAfter;

    @Column(name = "recipient_balance_before", nullable = false, precision = 19, scale = 2)
    private BigDecimal recipientBalanceBefore;

    @Column(name = "recipient_balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal recipientBalanceAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreUpdate
    void preUpdate() {
        if (status == WalletTransferStatus.COMPLETED && completedAt == null) {
            completedAt = Instant.now();
        }
    }

    public static WalletTransfer create(User sender,
            User recipient,
            String senderPhoneSnapshot,
            String recipientPhoneSnapshot,
            String operationId,
            BigDecimal amount,
            String currency,
            String note,
            BigDecimal senderBalanceBefore,
            BigDecimal senderBalanceAfter,
            BigDecimal recipientBalanceBefore,
            BigDecimal recipientBalanceAfter,
            Instant createdAt) {
        WalletTransfer transfer = new WalletTransfer();
        transfer.sender = sender;
        transfer.recipient = recipient;
        transfer.senderPhoneSnapshot = senderPhoneSnapshot;
        transfer.recipientPhoneSnapshot = recipientPhoneSnapshot;
        transfer.operationId = operationId;
        transfer.amount = amount;
        transfer.currency = currency;
        transfer.note = note;
        transfer.status = WalletTransferStatus.COMPLETED;
        transfer.senderBalanceBefore = senderBalanceBefore;
        transfer.senderBalanceAfter = senderBalanceAfter;
        transfer.recipientBalanceBefore = recipientBalanceBefore;
        transfer.recipientBalanceAfter = recipientBalanceAfter;
        transfer.createdAt = createdAt;
        transfer.completedAt = createdAt;
        return transfer;
    }

    public Long getId() {
        return id;
    }

    public String getOperationId() {
        return operationId;
    }

    public User getSender() {
        return sender;
    }

    public User getRecipient() {
        return recipient;
    }

    public String getSenderPhoneSnapshot() {
        return senderPhoneSnapshot;
    }

    public String getRecipientPhoneSnapshot() {
        return recipientPhoneSnapshot;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getNote() {
        return note;
    }

    public WalletTransferStatus getStatus() {
        return status;
    }

    public BigDecimal getSenderBalanceBefore() {
        return senderBalanceBefore;
    }

    public BigDecimal getSenderBalanceAfter() {
        return senderBalanceAfter;
    }

    public BigDecimal getRecipientBalanceBefore() {
        return recipientBalanceBefore;
    }

    public BigDecimal getRecipientBalanceAfter() {
        return recipientBalanceAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setStatus(WalletTransferStatus status) {
        this.status = status;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}