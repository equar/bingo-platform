package com.bakaroo.bingo.backend.feature.playerapp.dto;

import java.time.Instant;

public record PlayerAppSettingsResponse(
        String bannerTitleEn,
        String bannerTitleAm,
        String bannerBodyEn,
        String bannerBodyAm,
        String bannerImageUrl,
        String termsUrl,
        String telegramUrl,
        String tiktokUrl,
        String supportPhoneNumbers,
        Instant updatedAt) {
}
