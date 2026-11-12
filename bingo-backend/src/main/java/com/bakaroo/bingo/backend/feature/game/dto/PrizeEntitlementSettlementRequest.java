package com.bakaroo.bingo.backend.feature.game.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrizeEntitlementSettlementRequest(
        @NotBlank @Size(max = 120) String settlementReference,
        @Size(max = 500) String settlementNote) {
}