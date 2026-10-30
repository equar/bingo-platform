package com.bakaroo.bingo.backend.feature.agent.dto;

import com.bakaroo.bingo.backend.feature.agent.entity.PayoutVerificationStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PayoutVerificationResponse(
        Long recordId,
        Long entitlementId,
        Long agentId,
        String payoutCode,
        PayoutVerificationStatus status,
        BigDecimal disbursedAmount,
        String clearingReference,
        Instant verifiedAt,
        Long floatTransactionId) {
}