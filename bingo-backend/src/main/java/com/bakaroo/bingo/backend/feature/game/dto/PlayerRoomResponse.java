package com.bakaroo.bingo.backend.feature.game.dto;

import com.bakaroo.bingo.backend.feature.game.entity.BingoPatternType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PlayerRoomResponse(
        Long id,
        String name,
        String description,
        BigDecimal ticketPrice,
        String currency,
        String timezone,
        String scheduleRule,
        int sessionCapacity,
        boolean active,
        List<BingoPatternType> configuredPatterns,
        Instant createdAt,
        Instant updatedAt) {
}
