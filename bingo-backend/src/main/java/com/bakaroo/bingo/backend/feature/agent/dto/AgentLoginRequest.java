package com.bakaroo.bingo.backend.feature.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AgentLoginRequest(
        @NotBlank String phoneNumber,
        @NotBlank @Pattern(regexp = "^[0-9]{4,8}$", message = "PIN must contain 4 to 8 digits") String pin) {
}
