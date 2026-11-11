package com.bakaroo.bingo.backend.feature.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PromotionalRewardGrantRequest(@NotNull @Positive Long campaignId,
        @NotNull @Positive Long playerId) { }
