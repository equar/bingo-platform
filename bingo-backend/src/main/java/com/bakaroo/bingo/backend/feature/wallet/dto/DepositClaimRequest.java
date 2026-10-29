package com.bakaroo.bingo.backend.feature.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record DepositClaimRequest(
        @NotNull @DecimalMin(value = "0.01", inclusive = true) BigDecimal submittedCashAmount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a three letter ISO code") String currency,
        @NotBlank String telebirrReference) {
}