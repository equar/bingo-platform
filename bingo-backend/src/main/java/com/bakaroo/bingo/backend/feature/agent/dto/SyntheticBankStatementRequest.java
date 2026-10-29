package com.bakaroo.bingo.backend.feature.agent.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SyntheticBankStatementRequest(
        @NotNull Long receivingBankAccountId,
        @NotBlank @Size(max = 120) String transactionReference,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "ETB") String currency,
        @NotNull LocalDate transactionDate,
        @NotBlank @Pattern(regexp = "SETTLED|UNSETTLED|REVERSED") String settlementState) {
}
