package com.bakaroo.bingo.backend.feature.game.dto;

import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;
import com.bakaroo.bingo.backend.feature.game.entity.BingoPatternType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record GameSessionRequest(
        @NotBlank String sessionName,
        @NotNull Instant scheduledStartAt,
        GameSessionState state,
        @DecimalMin(value = "0.01", inclusive = true) @Digits(integer = 17, fraction = 2) BigDecimal ticketPrice,
        @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @Min(1) Integer sessionCapacity,
        @Min(1) Integer maxTicketsPerPlayer,
        Boolean ticketSalesCloseOnSessionStart,
        Boolean allowMultipleWinnersPerPrize,
        @Valid List<@NotNull BingoPatternType> configuredPatterns,
        @Valid List<@NotNull BingoPrizeConfigRequest> configuredPrizes) {
}
