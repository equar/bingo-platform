package com.bakaroo.bingo.backend.feature.wallet.dto;

import java.math.BigDecimal;

public record DepositJournalLineResponse(
        int lineNumber,
        String accountCode,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String currency) {
}
