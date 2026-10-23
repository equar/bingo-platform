package com.equar.bingo.backend.feature.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record DepositVerificationResponse(
        Long verificationId,
        Long depositClaimId,
        Long statementEntryId,
        Long receivingBankAccountId,
        String transactionReference,
        BigDecimal clearedAmount,
        String currency,
        LocalDate transactionDate,
        String outcome,
        String evidenceSource,
        boolean synthetic,
        Long verifierUserId,
        Instant verifiedAt) {
}
