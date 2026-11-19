package com.bakaroo.bingo.backend.feature.auth.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.AssertTrue;

public record UpdateMyPreferencesRequest(
        @Pattern(regexp = "en|am|ti", message = "preferredLanguage must be one of: en, am, ti") String preferredLanguage,
        @Pattern(regexp = "AUTO|ETHIOPIAN|GREGORIAN", message = "preferredCalendar must be one of: AUTO, ETHIOPIAN, GREGORIAN") String preferredCalendar,
        Boolean autoMarkCalledNumbers) {

    public UpdateMyPreferencesRequest(String preferredLanguage, String preferredCalendar) {
        this(preferredLanguage, preferredCalendar, null);
    }

    @AssertTrue(message = "At least one preference must be provided")
    public boolean isAnyPreferenceProvided() {
        return preferredLanguage != null || preferredCalendar != null || autoMarkCalledNumbers != null;
    }
}
