package com.bakaroo.bingo.backend.feature.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record WalletAdjustmentRequest(
        @NotNull @DecimalMin(value = "-99999999999999999.99")
        @DecimalMax("99999999999999999.99") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a three letter ISO code") String currency,
        @NotBlank String reason) {
}
