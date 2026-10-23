package com.equar.bingo.backend.feature.agent.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record FloatPostingResponse(
        Long journalId,
        Long requestId,
        String postingReference,
        boolean simulated,
        String currency,
        BigDecimal amount,
        BigDecimal availableBalance,
        Long verifierUserId,
        Long approverUserId,
        Instant postedAt) {
}
