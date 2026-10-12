package com.equar.bingo.backend.feature.wallet.dto;

public record PaymentInstructionsResponse(
        String providerName,
        String businessAccount,
        String paymentInstructions,
        String currency) {
}