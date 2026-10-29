package com.bakaroo.bingo.backend.feature.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SignupRewardGrantResponse(
        Long id,
        Long playerId,
        String playerDisplayName,
        String playerPhoneNumber,
        Long policyId,
        String operationId,
        BigDecimal rewardAmount,
        String currency,
        Instant awardedAt) {
}