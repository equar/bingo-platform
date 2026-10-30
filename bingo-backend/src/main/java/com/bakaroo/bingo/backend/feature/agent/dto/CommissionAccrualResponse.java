package com.bakaroo.bingo.backend.feature.agent.dto;

import com.bakaroo.bingo.backend.feature.agent.entity.CommissionAccrualStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record CommissionAccrualResponse(
        Long id,
        Long agentId,
        Long ticketId,
        Long sessionId,
        int commissionRuleVersion,
        BigDecimal saleAmount,
        BigDecimal commissionAmount,
        CommissionAccrualStatus status,
        String operationId,
        Instant createdAt,
        Instant settledAt,
        String settlementReference,
        String settlementNote) {
}