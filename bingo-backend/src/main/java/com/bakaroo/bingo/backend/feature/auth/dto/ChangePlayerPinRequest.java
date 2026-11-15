package com.bakaroo.bingo.backend.feature.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePlayerPinRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String currentPin,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String newPin) {
}
