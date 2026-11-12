package com.bakaroo.bingo.backend.feature.playerapp.dto;

import java.time.Instant;

public record PlayerAnnouncementResponse(
        Long id,
        String titleEn,
        String titleAm,
        String bodyEn,
        String bodyAm,
        boolean published,
        boolean read,
        Instant createdAt,
        Instant updatedAt) {
}
