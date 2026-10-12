package com.equar.bingo.backend.feature.support.dto;

import java.time.Instant;

public record SupportMessageResponse(
        Long id,
        Long authorUserId,
        String body,
        Instant createdAt) {
}