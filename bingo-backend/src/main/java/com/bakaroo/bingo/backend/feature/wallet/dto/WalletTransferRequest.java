package com.bakaroo.bingo.backend.feature.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record WalletTransferRequest(
        @NotBlank @Size(max = 30) String recipientPhoneNumber,
        @NotNull BigDecimal amount,
        @NotBlank @Size(max = 3) String currency,
        @Size(max = 500) String note) {
}