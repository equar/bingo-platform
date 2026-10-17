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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "signup_reward_grants", uniqueConstraints = {
        @UniqueConstraint(name = "uk_signup_reward_grants_player", columnNames = "player_id"),
        @UniqueConstraint(name = "uk_signup_reward_grants_operation", columnNames = "operation_id")
})
public class SignupRewardGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private User player;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private SignupRewardPolicy policy;

    @Column(name = "operation_id", nullable = false, length = 36)
    private String operationId;

    @Column(name = "reward_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal rewardAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "awarded_at", nullable = false)
    private Instant awardedAt;

    protected SignupRewardGrant() {
    }

    public static SignupRewardGrant create(User player,
            SignupRewardPolicy policy,
            String operationId,
            BigDecimal rewardAmount,
            String currency,
            Instant awardedAt) {
        SignupRewardGrant grant = new SignupRewardGrant();
        grant.player = player;
        grant.policy = policy;
        grant.operationId = operationId;
        grant.rewardAmount = rewardAmount;
        grant.currency = currency;
        grant.awardedAt = awardedAt;
        return grant;
    }

    public Long getId() {
        return id;
    }

    public User getPlayer() {
        return player;
    }

    public SignupRewardPolicy getPolicy() {
        return policy;
    }

    public String getOperationId() {
        return operationId;
    }

    public BigDecimal getRewardAmount() {
        return rewardAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getAwardedAt() {
        return awardedAt;
    }
}