package com.equar.bingo.backend.feature.game.model;

import com.equar.bingo.backend.feature.game.entity.BingoPatternType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BingoSessionSnapshot(
        Long id,
        Long roomId,
        String roomName,
        String sessionName,
        String state,
        Instant scheduledStartAt,
        Instant startedAt,
        Instant endedAt,
        BigDecimal ticketPrice,
        String currency,
        int sessionCapacity,
        int maxTicketsPerPlayer,
        boolean ticketSalesCloseOnSessionStart,
        boolean allowMultipleWinnersPerPrize,
        List<BingoPatternType> configuredPatterns,
        List<BingoPrizeConfig> configuredPrizes,
        long ticketCount,
        long drawCount) {
}