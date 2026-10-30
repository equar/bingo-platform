package com.bakaroo.bingo.backend.feature.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletBalanceResponse(
                Long walletId,
                String currency,
                BigDecimal availableBalance,
                BigDecimal promotionalBalance,
                BigDecimal totalBalance,
                Instant updatedAt) {
}