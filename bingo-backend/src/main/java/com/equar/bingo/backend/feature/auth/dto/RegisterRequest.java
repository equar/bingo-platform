package com.equar.bingo.backend.feature.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Size(max = 30) String phoneNumber,
        @NotBlank @Size(min = 8, max = 72) String password) {
}