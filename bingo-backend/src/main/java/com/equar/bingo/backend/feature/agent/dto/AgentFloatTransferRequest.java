package com.equar.bingo.backend.feature.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AgentFloatTransferRequest(
        @NotBlank String playerPhoneNumber,
        @NotNull BigDecimal amount,
        @NotBlank String currency,
        @NotBlank String pin,
        String note) {
}