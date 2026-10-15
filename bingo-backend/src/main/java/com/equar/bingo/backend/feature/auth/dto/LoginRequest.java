package com.equar.bingo.backend.feature.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
                @NotBlank @Size(max = 30) String phoneNumber,
                @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "PIN must contain exactly 6 digits") String password) {
}