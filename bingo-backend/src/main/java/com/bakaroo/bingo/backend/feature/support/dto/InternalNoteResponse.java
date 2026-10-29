package com.bakaroo.bingo.backend.feature.support.dto;

import java.time.Instant;

public record InternalNoteResponse(
        Long id,
        Long authorUserId,
        String body,
        Instant createdAt) {
}