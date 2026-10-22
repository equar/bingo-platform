package com.equar.bingo.backend.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.authentication")
public record AuthenticationThrottleProperties(
        @Min(1) int accountAttemptLimit,
        @Min(1) int networkAttemptLimit,
        @NotNull Duration window,
        @NotNull Duration lockout,
        @NotNull List<String> trustedProxyAddresses) {

    public AuthenticationThrottleProperties {
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Authentication attempt window must be positive");
        }
        if (lockout == null || lockout.isNegative() || lockout.isZero()) {
            throw new IllegalArgumentException("Authentication lockout duration must be positive");
        }
    }
}
