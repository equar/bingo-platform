package com.equar.bingo.backend.feature.game.config;

import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Validated
@ConfigurationProperties(prefix = "app.game")
public record GameProperties(
        @Positive int maxTicketsPerPlayer,
        @Positive int sessionCapacity,
        boolean ticketSalesCloseOnSessionStart) {
}