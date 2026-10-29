package com.bakaroo.bingo.backend.feature.wallet.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record WalletAdjustmentSubmissionRequest(
        @NotNull @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a three letter ISO code") String currency,
        @NotBlank @Size(max = 350) String reason,
        @NotBlank @Size(max = 120) String supportingReference) {
}
