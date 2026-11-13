package com.bakaroo.bingo.backend.feature.playerapp.dto;

import jakarta.validation.constraints.Size;

public record PlayerAppSettingsRequest(
        @Size(max = 160) String bannerTitleEn,
        @Size(max = 160) String bannerTitleAm,
        @Size(max = 1000) String bannerBodyEn,
        @Size(max = 1000) String bannerBodyAm,
        @Size(max = 1000) String bannerImageUrl,
        @Size(max = 1000) String termsUrl,
        @Size(max = 500) String telegramUrl,
        @Size(max = 500) String tiktokUrl,
        @Size(max = 500) String supportPhoneNumbers) {
}
