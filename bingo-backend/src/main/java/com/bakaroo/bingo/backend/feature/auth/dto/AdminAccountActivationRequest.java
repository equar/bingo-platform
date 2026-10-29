package com.bakaroo.bingo.backend.feature.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminAccountActivationRequest(
        @NotBlank @Size(max = 100) String token,
        @NotBlank @Size(min = 8, max = 72) String newPassword) {
}
