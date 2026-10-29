package com.bakaroo.bingo.backend.feature.admin.dto;

import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;
import java.math.BigDecimal;
import java.time.Instant;

public record AdminSessionSummaryResponse(
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
        long ticketCount,
        long drawCount,
        long winClaimCount,
        long supportTicketCount) {
}