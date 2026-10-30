package com.bakaroo.bingo.backend.feature.game.dto;

import com.bakaroo.bingo.backend.feature.game.entity.BingoPatternType;
import com.bakaroo.bingo.backend.feature.game.entity.PrizeEntitlementStatus;
import com.bakaroo.bingo.backend.feature.game.entity.PrizeSettlementPath;
import java.math.BigDecimal;
import java.time.Instant;

public record PrizeEntitlementResponse(
        Long id,
        Long winClaimId,
        Long sessionId,
        Long ticketId,
        Long claimantId,
        BingoPatternType prizeType,
        PrizeSettlementPath settlementPath,
        PrizeEntitlementStatus status,
        BigDecimal prizeAmount,
        String currency,
        String operationId,
        Instant createdAt,
        Instant settledAt,
        Long settledBy,
        String settlementReference,
        String settlementNote) {
}