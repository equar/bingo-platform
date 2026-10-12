package com.equar.bingo.backend.feature.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record AgentPayoutVerificationRequest(
        @NotBlank String payoutCode,
        @NotBlank String pin,
        String clearingReference) {
}