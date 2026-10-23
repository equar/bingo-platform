package com.equar.bingo.backend.feature.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FloatBankAccountRequest(
        @NotBlank @Size(max = 120) String bankName,
        @NotBlank @Size(min = 4, max = 34) @Pattern(regexp = "[A-Za-z0-9 -]+") String accountNumber,
        @NotBlank @Size(max = 120) String accountHolderName,
        @NotBlank @Pattern(regexp = "ETB") String currency) {
}
