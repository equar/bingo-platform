package com.bakaroo.bingo.backend.feature.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SignupRewardSettingsResponse(
        Long id,
        boolean enabled,
        BigDecimal rewardAmount,
        String currency,
        Instant effectiveFrom,
        Instant effectiveUntil,
        Long changedByUserId,
        String reason,
        Instant createdAt,
        Instant updatedAt,
        boolean activeNow) {
}