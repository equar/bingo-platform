package com.bakaroo.bingo.backend.feature.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentPayoutVerificationRequest(
        @NotBlank @Size(max = 80) String payoutCode,
        @NotBlank String pin,
        @NotBlank @Size(max = 120) String clearingReference) {
}