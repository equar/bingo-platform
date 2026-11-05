package com.bakaroo.bingo.backend.feature.game.dto;

import java.time.Instant;

public record SessionTicketLookupResponse(
        Long ticketId,
        Long sessionId,
        Long playerId,
        String playerName,
        String playerPhone,
        String publicTicketCode,
        Instant purchasedAt) {
}
