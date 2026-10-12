package com.equar.bingo.backend.feature.agent.dto;

import com.equar.bingo.backend.feature.agent.entity.AgentFloatTransactionType;
import java.math.BigDecimal;
import java.time.Instant;

public record AgentFloatTransactionResponse(
        Long id,
        Long agentFloatId,
        AgentFloatTransactionType transactionType,
        BigDecimal amount,
        BigDecimal availableBefore,
        BigDecimal availableAfter,
        BigDecimal clearingBefore,
        BigDecimal clearingAfter,
        String relatedEntityType,
        Long relatedEntityId,
        String operationId,
        String note,
        Instant createdAt) {
}