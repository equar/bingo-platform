package com.bakaroo.bingo.backend.feature.game.dto;

import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;
import com.bakaroo.bingo.backend.feature.game.entity.BingoPatternType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record GameSessionRequest(
        @NotBlank @Size(max = 120) String sessionName,
        @NotNull @Future Instant scheduledStartAt,
        GameSessionState state,
        @DecimalMin(value = "0.01", inclusive = true) @Digits(integer = 17, fraction = 2) BigDecimal ticketPrice,
        @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @Min(1) Integer sessionCapacity,
        @Min(1) Integer maxTicketsPerPlayer,
        Boolean ticketSalesCloseOnSessionStart,
        Boolean allowMultipleWinnersPerPrize,
        List<@NotNull BingoPatternType> configuredPatterns,
        List<@NotNull @Valid BingoPrizeConfigRequest> configuredPrizes) {
}
