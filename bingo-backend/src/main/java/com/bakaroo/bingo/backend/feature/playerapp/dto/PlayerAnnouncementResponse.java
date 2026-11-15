package com.bakaroo.bingo.backend.feature.playerapp.dto;

import java.time.Instant;

public record PlayerAnnouncementResponse(
        Long id,
        String titleEn,
        String titleAm,
        String bodyEn,
        String bodyAm,
        String imageUrl,
        boolean published,
        boolean showInCarousel,
        int displayOrder,
        boolean read,
        Instant createdAt,
        Instant updatedAt) {
}
