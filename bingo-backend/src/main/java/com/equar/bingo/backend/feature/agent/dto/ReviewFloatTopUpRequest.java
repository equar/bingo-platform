package com.equar.bingo.backend.feature.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record ReviewFloatTopUpRequest(
        boolean approve,
        @NotBlank String reviewNote) {
}