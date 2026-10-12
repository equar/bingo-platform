package com.equar.bingo.backend.feature.agent.config;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.agent")
public record AgentProperties(
        @NotNull BigDecimal pinReconfirmThreshold,
        int loginAttemptLimit,
        int pinAttemptLimit,
        int payoutCodeAttemptLimit,
        @NotNull Duration attemptWindow) {
}