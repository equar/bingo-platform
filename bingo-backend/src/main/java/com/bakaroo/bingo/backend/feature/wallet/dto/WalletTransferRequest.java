package com.bakaroo.bingo.backend.feature.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record WalletTransferRequest(
        @NotBlank @Size(max = 30) String recipientPhoneNumber,
        @NotNull @DecimalMin("0.01") @DecimalMax("99999999999999999.99") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 3) String currency,
        @Size(max = 500) String note) {
}
