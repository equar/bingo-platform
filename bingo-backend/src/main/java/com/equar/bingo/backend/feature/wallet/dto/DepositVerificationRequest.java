package com.equar.bingo.backend.feature.wallet.dto;

import jakarta.validation.constraints.NotNull;

public record DepositVerificationRequest(@NotNull Long statementEntryId) {
}
