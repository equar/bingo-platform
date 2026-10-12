package com.equar.bingo.backend.feature.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record AgentPlayerRegistrationRequest(
        @NotBlank String phoneNumber,
        @NotBlank String displayName) {
}