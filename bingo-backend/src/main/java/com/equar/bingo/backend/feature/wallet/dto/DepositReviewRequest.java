package com.equar.bingo.backend.feature.wallet.dto;

import jakarta.validation.constraints.NotBlank;

public record DepositReviewRequest(@NotBlank String reviewNote) {
}