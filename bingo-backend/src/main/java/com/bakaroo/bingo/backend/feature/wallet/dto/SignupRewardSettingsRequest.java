package com.bakaroo.bingo.backend.feature.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record SignupRewardSettingsRequest(
        boolean enabled,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal rewardAmount,
        @NotBlank @Pattern(regexp = "(?i)ETB") String currency,
        @NotNull Instant effectiveFrom,
        Instant effectiveUntil,
        @Size(max = 500) String reason) {
}
