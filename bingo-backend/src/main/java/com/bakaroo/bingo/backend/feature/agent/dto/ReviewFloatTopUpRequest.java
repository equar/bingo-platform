package com.bakaroo.bingo.backend.feature.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewFloatTopUpRequest(
        boolean approve,
        @NotBlank @Size(max = 500) String reviewNote) {
}
