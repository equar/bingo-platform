package com.bakaroo.bingo.backend.feature.game.dto;

import com.bakaroo.bingo.backend.feature.game.entity.BingoPatternType;
import com.bakaroo.bingo.backend.feature.game.model.BingoPrizeConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BingoRoomResponse(
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
        List<BingoPrizeConfig> configuredPrizes,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
