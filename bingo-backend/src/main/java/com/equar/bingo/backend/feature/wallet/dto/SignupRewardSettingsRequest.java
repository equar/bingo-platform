package com.equar.bingo.backend.feature.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record SignupRewardSettingsRequest(
        boolean enabled,
        @NotNull BigDecimal rewardAmount,
        @NotBlank @Size(max = 3) String currency,
        @NotNull Instant effectiveFrom,
        Instant effectiveUntil,
        @Size(max = 500) String reason) {
}