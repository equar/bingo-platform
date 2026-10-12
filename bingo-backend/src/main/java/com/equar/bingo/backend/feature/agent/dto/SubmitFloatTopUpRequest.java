package com.equar.bingo.backend.feature.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SubmitFloatTopUpRequest(
        @NotNull BigDecimal amount,
        @NotBlank String currency,
        String note) {
}