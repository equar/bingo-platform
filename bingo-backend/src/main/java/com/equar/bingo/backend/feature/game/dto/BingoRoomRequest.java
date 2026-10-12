package com.equar.bingo.backend.feature.game.dto;

import com.equar.bingo.backend.feature.game.entity.BingoPatternType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;

public record BingoRoomRequest(
        @NotBlank String name,
        String description,
        @NotNull @DecimalMin(value = "0.01", inclusive = true) BigDecimal ticketPrice,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotBlank String timezone,
        @NotBlank String scheduleRule,
        @Min(1) int sessionCapacity,
        @Min(1) int maxTicketsPerPlayer,
        boolean ticketSalesCloseOnSessionStart,
        boolean allowMultipleWinnersPerPrize,
        boolean active,
        @Valid List<@NotNull BingoPatternType> configuredPatterns,
        @Valid List<@NotNull BingoPrizeConfigRequest> configuredPrizes) {
}