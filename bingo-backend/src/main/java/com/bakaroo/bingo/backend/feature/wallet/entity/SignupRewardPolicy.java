package com.bakaroo.bingo.backend.feature.wallet.entity;

import com.bakaroo.bingo.backend.feature.user.entity.User;
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
@Table(name = "signup_reward_policies")
public class SignupRewardPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "reward_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal rewardAmount;

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

    protected SignupRewardPolicy() {
    }

    public static SignupRewardPolicy create(boolean enabled,
            BigDecimal rewardAmount,
            String currency,
            Instant effectiveFrom,
            Instant effectiveUntil,
            User changedBy,
            String reason,
            Instant now) {
        SignupRewardPolicy policy = new SignupRewardPolicy();
        policy.enabled = enabled;
        policy.rewardAmount = rewardAmount;
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

    public BigDecimal getRewardAmount() {
        return rewardAmount;
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