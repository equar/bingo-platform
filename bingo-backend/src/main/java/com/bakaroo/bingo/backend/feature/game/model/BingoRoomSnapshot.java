package com.bakaroo.bingo.backend.feature.game.model;

import com.bakaroo.bingo.backend.feature.game.entity.BingoPatternType;
import java.math.BigDecimal;
import java.util.List;

public record BingoRoomSnapshot(
        Long id,
        String name,
        String description,
        BigDecimal ticketPrice,
        String currency,
        String timezone,
        String scheduleRule,
        int sessionCapacity,
        int maxTicketsPerPlayer,
        boolean ticketSalesCloseOnSessionStart,
        boolean allowMultipleWinnersPerPrize,
        boolean active,
        List<BingoPatternType> configuredPatterns,
        List<BingoPrizeConfig> configuredPrizes) {
}