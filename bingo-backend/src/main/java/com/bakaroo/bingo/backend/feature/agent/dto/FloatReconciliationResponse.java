package com.bakaroo.bingo.backend.feature.agent.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FloatReconciliationResponse(
        LocalDate statementDate,
        boolean syntheticQaData,
        long settledStatementCount,
        BigDecimal settledStatementAmount,
        long postingCount,
        BigDecimal postedAmount,
        BigDecimal debitTotal,
        BigDecimal creditTotal,
        long unmatchedSettledStatementCount,
        long verifiedUnpostedRequestCount,
        long unresolvedExceptionCount,
        BigDecimal statementToPostingDifference,
        boolean journalBalanced) {
}
