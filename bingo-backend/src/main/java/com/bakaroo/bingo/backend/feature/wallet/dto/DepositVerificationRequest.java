package com.bakaroo.bingo.backend.feature.wallet.dto;

import jakarta.validation.constraints.NotNull;

public record DepositVerificationRequest(@NotNull Long statementEntryId) {
}
