package com.bakaroo.bingo.backend.feature.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DepositPostingResponse(
        Long journalId,
        Long depositClaimId,
        String postingReference,
        String currency,
        boolean simulated,
        BigDecimal walletBalanceBefore,
        BigDecimal walletBalanceAfter,
        Long verifierUserId,
        Long approverUserId,
        Instant createdAt,
        List<DepositJournalLineResponse> lines) {
}
