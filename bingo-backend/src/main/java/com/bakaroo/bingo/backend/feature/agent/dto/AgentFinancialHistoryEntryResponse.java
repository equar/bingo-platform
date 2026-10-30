package com.bakaroo.bingo.backend.feature.agent.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AgentFinancialHistoryEntryResponse(
        String type,
        BigDecimal amount,
        String currency,
        Instant createdAt,
        String status,
        String reference,
        String counterparty,
        String note,
        String relatedEntity) {
}
