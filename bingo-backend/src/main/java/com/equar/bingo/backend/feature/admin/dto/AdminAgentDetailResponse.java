package com.equar.bingo.backend.feature.admin.dto;

import com.equar.bingo.backend.feature.agent.entity.AgentStatus;
import java.time.Instant;

public record AdminAgentDetailResponse(
        Long agentId,
        Long userId,
        String displayName,
        String normalizedPhone,
        AgentStatus status,
        String city,
        String territory,
        String idDocumentType,
        String idDocumentNumber,
        String idDocumentFrontReference,
        String idDocumentBackReference,
        Instant createdAt,
        Instant updatedAt,
        long ticketSalesCount,
        long floatTransactionCount,
        long topUpRequestCount,
        long payoutVerificationCount) {
}
