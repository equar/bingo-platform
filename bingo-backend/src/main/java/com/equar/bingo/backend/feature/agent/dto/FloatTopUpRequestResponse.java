package com.equar.bingo.backend.feature.agent.dto;

import com.equar.bingo.backend.feature.agent.entity.FloatTopUpRequestStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record FloatTopUpRequestResponse(
        Long id,
        Long agentId,
        Long requestedByUserId,
        BigDecimal amount,
        String currency,
        FloatTopUpRequestStatus status,
        String operationId,
        String reviewNote,
        Long reviewedByUserId,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt,
        String submissionNote,
        Long receivingBankAccountId,
        String bankNameSnapshot,
        String maskedAccountSnapshot,
        String accountHolderSnapshot,
        String transferReference,
        String supportingEvidenceNote) {
}
