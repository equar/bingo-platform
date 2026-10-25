package com.equar.bingo.backend.feature.game.dto;

import com.equar.bingo.backend.feature.game.entity.BingoPatternType;
import com.equar.bingo.backend.feature.game.entity.GameSessionState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PlayerGameSessionResponse(
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
        List<BingoPatternType> configuredPatterns,
        long ticketCount,
        long drawCount,
        Instant createdAt,
        Instant updatedAt) {
}
