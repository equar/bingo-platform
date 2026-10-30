package com.bakaroo.bingo.backend.feature.game.dto;

import com.bakaroo.bingo.backend.feature.game.entity.BingoPatternType;
import com.bakaroo.bingo.backend.feature.game.entity.PrizeSettlementPath;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record BingoPrizeConfigRequest(
        @NotNull BingoPatternType prizeType,
        @NotNull @DecimalMin(value = "0.01", inclusive = true) @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotNull PrizeSettlementPath settlementPath,
        @Min(1) int maxWinners) {
}
