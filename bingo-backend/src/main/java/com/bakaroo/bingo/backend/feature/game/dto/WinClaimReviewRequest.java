package com.bakaroo.bingo.backend.feature.game.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WinClaimReviewRequest(@NotBlank @Size(max = 500) String reviewNote) {
}