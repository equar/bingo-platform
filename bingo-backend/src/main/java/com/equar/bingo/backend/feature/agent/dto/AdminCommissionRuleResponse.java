package com.equar.bingo.backend.feature.agent.dto;

import java.time.Instant;

public record AdminCommissionRuleResponse(
        Long id,
        int versionNumber,
        int commissionRateBps,
        boolean active,
        Instant effectiveFrom,
        Instant createdAt,
        Instant updatedAt) {
}
