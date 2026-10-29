package com.bakaroo.bingo.backend.feature.agent.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FloatVerificationRequest(
        Long statementEntryId,
        @Size(max = 40) String exceptionOutcome,
        @Size(max = 500) String exceptionReason) {
}
