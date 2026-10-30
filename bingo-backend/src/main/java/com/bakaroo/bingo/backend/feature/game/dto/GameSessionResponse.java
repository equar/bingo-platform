package com.bakaroo.bingo.backend.feature.game.dto;

import com.bakaroo.bingo.backend.feature.game.entity.BingoPatternType;
import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;
import com.bakaroo.bingo.backend.feature.game.model.BingoPrizeConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record GameSessionResponse(
        Long id,
        Long roomId,
        String roomName,
        String sessionName,
        GameSessionState state,
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
        long drawCount,
        Instant createdAt,
        Instant updatedAt) {
}