package com.equar.bingo.backend.feature.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletBalanceResponse(
        Long walletId,
        String currency,
        BigDecimal availableBalance,
        Instant updatedAt) {
}