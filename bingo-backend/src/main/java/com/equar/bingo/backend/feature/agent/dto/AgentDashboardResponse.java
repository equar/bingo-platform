package com.equar.bingo.backend.feature.agent.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AgentDashboardResponse(
        Long agentId,
        String status,
        BigDecimal availableFloat,
        BigDecimal clearingFloat,
        long ticketsSold,
        long commissionAccruals,
        long topUpRequests,
        Instant generatedAt) {
}