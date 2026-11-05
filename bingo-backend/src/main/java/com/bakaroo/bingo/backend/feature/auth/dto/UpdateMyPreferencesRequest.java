package com.bakaroo.bingo.backend.feature.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateMyPreferencesRequest(
        @NotBlank @Pattern(regexp = "en|am", message = "preferredLanguage must be one of: en, am")
        String preferredLanguage) {
}
