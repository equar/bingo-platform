package com.bakaroo.bingo.backend.feature.game.dto;

import java.time.Instant;
import java.util.List;

public record TicketResponse(
        Long id,
        Long sessionId,
        Long ownerId,
        String publicTicketCode,
        String operationId,
        List<List<Integer>> grid,
        Instant purchasedAt) {
}