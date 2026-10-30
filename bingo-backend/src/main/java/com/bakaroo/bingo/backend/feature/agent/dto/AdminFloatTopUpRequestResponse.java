package com.bakaroo.bingo.backend.feature.agent.dto;

import com.bakaroo.bingo.backend.feature.agent.entity.FloatTopUpRequestStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record AdminFloatTopUpRequestResponse(
        Long id,
        Long agentId,
        String agentDisplayName,
        String agentPhoneNumber,
        Long requestedByUserId,
        BigDecimal amount,
        String currency,
        FloatTopUpRequestStatus status,
        String submissionNote,
        String reviewNote,
        Long reviewedByUserId,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt,
        Long receivingBankAccountId,
        String bankNameSnapshot,
        String maskedAccountSnapshot,
        String accountHolderSnapshot,
        String transferReference,
        String supportingEvidenceNote) {
}
