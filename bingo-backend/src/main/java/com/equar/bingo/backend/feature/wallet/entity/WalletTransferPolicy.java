package com.equar.bingo.backend.feature.wallet.entity;

import com.equar.bingo.backend.feature.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallet_transfer_policies")
public class WalletTransferPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "max_single_transfer_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal maxSingleTransferAmount;

    @Column(name = "daily_outgoing_limit_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyOutgoingLimitAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_until")
    private Instant effectiveUntil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id")
    private User changedBy;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WalletTransferPolicy() {
    }

    public static WalletTransferPolicy create(boolean enabled,
            BigDecimal maxSingleTransferAmount,
            BigDecimal dailyOutgoingLimitAmount,
            String currency,
            Instant effectiveFrom,
            Instant effectiveUntil,
            User changedBy,
            String reason,
            Instant now) {
        WalletTransferPolicy policy = new WalletTransferPolicy();
        policy.enabled = enabled;
        policy.maxSingleTransferAmount = maxSingleTransferAmount;
        policy.dailyOutgoingLimitAmount = dailyOutgoingLimitAmount;
        policy.currency = currency;
        policy.effectiveFrom = effectiveFrom;
        policy.effectiveUntil = effectiveUntil;
        policy.changedBy = changedBy;
        policy.reason = reason;
        policy.createdAt = now;
        policy.updatedAt = now;
        return policy;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public BigDecimal getMaxSingleTransferAmount() {
        return maxSingleTransferAmount;
    }

    public BigDecimal getDailyOutgoingLimitAmount() {
        return dailyOutgoingLimitAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveUntil() {
        return effectiveUntil;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}