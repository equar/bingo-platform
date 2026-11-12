package com.bakaroo.bingo.backend.feature.wallet.dto;

import com.bakaroo.bingo.backend.feature.wallet.entity.SignupRewardPolicy;
import java.math.BigDecimal;
import java.time.Instant;

public record PromotionalRewardCampaignResponse(Long id, String name,
        SignupRewardPolicy.TriggerType triggerType, boolean enabled, BigDecimal rewardAmount,
        String currency, Instant effectiveFrom, Instant effectiveUntil, Long changedByUserId,
        String reason, Instant createdAt, Instant updatedAt, boolean activeNow) { }
