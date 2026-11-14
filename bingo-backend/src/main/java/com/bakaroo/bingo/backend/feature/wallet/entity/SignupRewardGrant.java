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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "signup_reward_grants", uniqueConstraints = {
        @UniqueConstraint(name = "uk_signup_reward_grants_campaign_event", columnNames = {"policy_id", "trigger_reference_key"}),
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

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private SignupRewardPolicy.TriggerType triggerType;

    @Column(name = "trigger_reference_key", nullable = false, length = 80)
    private String triggerReferenceKey;

    @Column(name = "signup_player_id", insertable = false, updatable = false)
    private Long signupPlayerId;

    @Column(name = "operation_id", nullable = false, length = 36)
    private String operationId;

    @Column(name = "reward_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal rewardAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "awarded_at", nullable = false)
    private Instant awardedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "awarded_by_user_id")
    private User awardedBy;

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
        grant.triggerType = policy.getTriggerType();
        grant.triggerReferenceKey = "PLAYER-" + player.getId();
        grant.operationId = operationId;
        grant.rewardAmount = rewardAmount;
        grant.currency = currency;
        grant.awardedAt = awardedAt;
        return grant;
    }

    public static SignupRewardGrant createForTrigger(User player, SignupRewardPolicy policy, String operationId,
            BigDecimal rewardAmount, String currency, Instant awardedAt, String triggerReferenceKey) {
        SignupRewardGrant grant = create(player, policy, operationId, rewardAmount, currency, awardedAt);
        grant.triggerReferenceKey = triggerReferenceKey;
        return grant;
    }

    public static SignupRewardGrant create(User player, SignupRewardPolicy policy, String operationId,
            BigDecimal rewardAmount, String currency, Instant awardedAt, User awardedBy) {
        SignupRewardGrant grant = create(player, policy, operationId, rewardAmount, currency, awardedAt);
        grant.awardedBy = awardedBy;
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

    public SignupRewardPolicy.TriggerType getTriggerType() {
        return triggerType;
    }

    public String getTriggerReferenceKey() {
        return triggerReferenceKey;
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

    public User getAwardedBy() { return awardedBy; }
}
