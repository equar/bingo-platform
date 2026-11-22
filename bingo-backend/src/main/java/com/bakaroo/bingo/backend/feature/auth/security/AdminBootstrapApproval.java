package com.bakaroo.bingo.backend.feature.auth.security;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/** A GitHub workflow approval is valid only during the short window recorded in its run inputs. */
final class AdminBootstrapApproval {
    private static final Duration MAX_WINDOW = Duration.ofMinutes(60);

    private AdminBootstrapApproval() {}

    static boolean isActive(String rawExpiry, Instant now) {
        if (rawExpiry == null || rawExpiry.isBlank()) return false;
        try {
            Instant expiry = Instant.parse(rawExpiry.trim());
            return expiry.isAfter(now) && !expiry.isAfter(now.plus(MAX_WINDOW));
        } catch (DateTimeParseException exception) {
            return false;
        }
    }
}
