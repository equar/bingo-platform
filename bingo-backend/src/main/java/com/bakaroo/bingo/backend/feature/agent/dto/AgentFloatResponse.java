package com.bakaroo.bingo.backend.feature.agent.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AgentFloatResponse(
        Long agentId,
        Long floatId,
        String currency,
        BigDecimal availableBalance,
        BigDecimal clearingBalance,
        Instant updatedAt) {
}