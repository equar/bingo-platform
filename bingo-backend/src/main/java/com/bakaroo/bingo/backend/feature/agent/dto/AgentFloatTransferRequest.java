package com.bakaroo.bingo.backend.feature.agent.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record AgentFloatTransferRequest(
        @NotBlank String playerPhoneNumber,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank String currency,
        @NotBlank String pin,
        String note) {
}