package com.bakaroo.bingo.backend.feature.game.dto;

import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;
import java.time.Instant;

public record PlayerGameHistoryResponse(
        Long ticketId,
        Long sessionId,
        String roomName,
        String sessionName,
        GameSessionState sessionState,
        String ticketCode,
        Instant purchasedAt) {
}
