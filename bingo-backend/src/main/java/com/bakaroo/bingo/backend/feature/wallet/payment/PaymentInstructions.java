package com.bakaroo.bingo.backend.feature.wallet.payment;

public record PaymentInstructions(
        String providerName,
        String businessAccount,
        String paymentInstructions,
        String currency) {
}