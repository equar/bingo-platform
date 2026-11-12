package com.bakaroo.bingo.backend.feature.playerapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlayerAnnouncementRequest(
        @NotBlank @Size(max = 160) String titleEn,
        @Size(max = 160) String titleAm,
        @NotBlank @Size(max = 2000) String bodyEn,
        @Size(max = 2000) String bodyAm,
        boolean published) {
}
