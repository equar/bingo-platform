package com.bakaroo.bingo.backend.feature.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SetAgentPinRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{4,8}$", message = "PIN must contain 4 to 8 digits") String pin) {
}