package com.equar.bingo.backend.feature.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgentTicketSaleRequest(
        @NotNull Long sessionId,
        @NotBlank String playerPhoneNumber,
        @NotBlank String pin) {
}