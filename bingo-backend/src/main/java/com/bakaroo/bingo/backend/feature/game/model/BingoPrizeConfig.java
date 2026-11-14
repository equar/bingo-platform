package com.bakaroo.bingo.backend.feature.game.model;

import com.bakaroo.bingo.backend.feature.game.entity.BingoPatternType;
import com.bakaroo.bingo.backend.feature.game.entity.PrizeSettlementPath;
import java.math.BigDecimal;

public record BingoPrizeConfig(
        BingoPatternType prizeType,
        BigDecimal amount,
        PrizeSettlementPath settlementPath,
        int maxWinners) {
}