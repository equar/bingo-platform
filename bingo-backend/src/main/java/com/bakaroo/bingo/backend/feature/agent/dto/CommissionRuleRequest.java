package com.bakaroo.bingo.backend.feature.agent.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CommissionRuleRequest(
        @Min(0) int commissionRateBps,
        @NotNull Instant effectiveFrom,
        boolean active) {
}