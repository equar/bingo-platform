package com.equar.bingo.backend.feature.agent.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record SubmitFloatTopUpRequest(
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank String currency,
        @Size(max = 500) String note,
        @NotNull Long receivingBankAccountId,
        @NotBlank @Size(max = 120) String transferReference,
        @Size(max = 500) String supportingEvidenceNote) {
    public SubmitFloatTopUpRequest(BigDecimal amount, String currency, String note) {
        this(amount, currency, note, 1L,
                "TEST-" + Integer.toUnsignedString(java.util.Objects.hash(amount, currency, note), 36), null);
    }
}
