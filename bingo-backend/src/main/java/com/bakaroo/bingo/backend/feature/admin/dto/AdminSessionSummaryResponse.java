package com.bakaroo.bingo.backend.feature.admin.dto;

import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;
import com.bakaroo.bingo.backend.feature.game.model.BingoPrizeConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
        int sessionCapacity,
        long ticketCount,
        long drawCount,
        long winClaimCount,
        long supportTicketCount,
        List<BingoPrizeConfig> configuredPrizes) {

    public AdminSessionSummaryResponse(Long id, Long roomId, String roomName, String sessionName,
            GameSessionState state, Instant scheduledStartAt, Instant startedAt, Instant endedAt,
            BigDecimal ticketPrice, String currency, int sessionCapacity, long ticketCount, long drawCount,
            long winClaimCount, long supportTicketCount) {
        this(id, roomId, roomName, sessionName, state, scheduledStartAt, startedAt, endedAt, ticketPrice,
                currency, sessionCapacity, ticketCount, drawCount, winClaimCount, supportTicketCount, List.of());
    }
}
