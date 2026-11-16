package com.bakaroo.bingo.backend.feature.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateMyPreferencesRequest(
        @NotBlank @Pattern(regexp = "en|am|ti", message = "preferredLanguage must be one of: en, am, ti")
        String preferredLanguage) {
}
