package com.equar.bingo.backend.feature.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WalletAdjustmentReviewRequest(@NotBlank @Size(max = 500) String reviewNote) {
}
