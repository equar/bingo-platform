package com.equar.bingo.backend.feature.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record DrawEventRequest(@Min(1) @Max(75) int numberDrawn) {
}