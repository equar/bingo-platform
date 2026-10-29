package com.bakaroo.bingo.backend.feature.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record DepositClaimResponse(
        Long id,
        String operationId,
        Long playerId,
        BigDecimal submittedCashAmount,
        String currency,
        BigDecimal conversionRateSnapshot,
        BigDecimal calculatedCreditAmount,
        String normalizedTelebirrReference,
        String status,
        Instant submittedAt,
        Long reviewedBy,
        Instant reviewedAt,
        String reviewNote,
        Long verifiedBy,
        Instant verifiedAt,
        PaymentInstructionsResponse paymentInstructions) {
}