package com.bakaroo.bingo.backend.feature.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record DepositClaimRequest(
        @NotNull @DecimalMin(value = "0.01", inclusive = true)
        @DecimalMax("99999999999999999.99") @Digits(integer = 17, fraction = 2)
        BigDecimal submittedCashAmount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a three letter ISO code") String currency,
        @NotBlank @ValidTelebirrReference
        String telebirrReference) {
}
