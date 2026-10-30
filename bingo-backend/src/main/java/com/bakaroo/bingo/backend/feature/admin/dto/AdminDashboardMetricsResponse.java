package com.bakaroo.bingo.backend.feature.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminDashboardMetricsResponse(
        Instant generatedAt,
        String businessTimezone,
        long activePlayers,
        long activeAgents,
        long openSupportTickets,
        long openDepositReviews,
        long openAgentFloatReviews,
        long openWinClaims,
        long activeSessions,
        BigDecimal playerWalletBalanceTotal,
        BigDecimal agentFloatAvailableTotal,
        BigDecimal agentFloatClearingTotal) {
}