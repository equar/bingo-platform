package com.bakaroo.bingo.backend.feature.agent.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FloatVerificationResponse(
        Long id,
        Long requestId,
        Long statementEntryId,
        Long reviewerUserId,
        String outcome,
        String exceptionReason,
        String evidenceSource,
        Long actualReceivingBankAccountId,
        String bankTransactionReference,
        BigDecimal verifiedAmount,
        LocalDate bankTransactionDate,
        Instant verifiedAt,
        boolean synthetic) {
}
