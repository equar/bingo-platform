package com.bakaroo.bingo.backend.feature.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
                @NotBlank @Size(max = 120) String displayName,
                @NotBlank @Size(max = 30) String phoneNumber,
                @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "PIN must contain exactly 6 digits") String password,
                @jakarta.validation.constraints.Size(max = 24) String referralCode) {
    public RegisterRequest(String displayName, String phoneNumber, String password) {
        this(displayName, phoneNumber, password, null);
    }
}
