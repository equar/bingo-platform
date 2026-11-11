package com.bakaroo.bingo.backend.feature.wallet.dto;

import com.bakaroo.bingo.backend.feature.wallet.entity.SignupRewardPolicy;
import java.math.BigDecimal;
import java.time.Instant;

public record PromotionalRewardGrantResponse(Long id, String campaignName,
        SignupRewardPolicy.TriggerType triggerType, Long playerId, String playerDisplayName,
        String playerPhoneNumber, Long campaignId, Long awardedByUserId, String operationId,
        BigDecimal rewardAmount, String currency, Instant awardedAt) { }
