package com.bakaroo.bingo.backend.feature.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepositVerificationRequest(@NotBlank @Size(max = 120) String paymentReference) {
}
