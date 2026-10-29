package com.bakaroo.bingo.backend.feature.agent.dto;

import com.bakaroo.bingo.backend.feature.game.entity.PrizeEntitlementStatus;
import com.bakaroo.bingo.backend.feature.game.entity.PrizeSettlementPath;
import java.math.BigDecimal;
import java.time.Instant;

public record AgentPayoutLookupResponse(
        Long entitlementId,
        Long sessionId,
        Long ticketId,
        Long claimantId,
        PrizeSettlementPath settlementPath,
        PrizeEntitlementStatus status,
        BigDecimal prizeAmount,
        String currency,
        String payoutCode,
        Instant createdAt) {
}