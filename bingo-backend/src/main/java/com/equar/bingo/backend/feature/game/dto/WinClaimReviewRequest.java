package com.equar.bingo.backend.feature.game.dto;

import jakarta.validation.constraints.NotBlank;

public record WinClaimReviewRequest(@NotBlank String reviewNote) {
}