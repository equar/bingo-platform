package com.bakaroo.bingo.backend.feature.game.dto;

import java.time.Instant;

public record DrawEventResponse(
        Long id,
        Long sessionId,
        int sequenceNo,
        int numberDrawn,
        String operationId,
        Instant drawnAt) {
}