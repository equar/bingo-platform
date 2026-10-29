package com.bakaroo.bingo.backend.feature.game.dto;

import jakarta.validation.constraints.NotBlank;

public record PrizeEntitlementSettlementRequest(
        @NotBlank String settlementReference,
        String settlementNote) {
}