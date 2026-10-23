package com.equar.bingo.backend.feature.agent.dto;

import java.time.Instant;

public record FloatBankAccountResponse(
        Long id,
        String bankName,
        String maskedAccountNumber,
        String accountHolderName,
        String currency,
        boolean active,
        boolean playerDepositsEnabled,
        Instant createdAt,
        Instant updatedAt) {
}
