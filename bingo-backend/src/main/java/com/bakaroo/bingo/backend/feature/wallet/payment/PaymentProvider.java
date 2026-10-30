package com.bakaroo.bingo.backend.feature.wallet.payment;

public interface PaymentProvider {

    PaymentInstructions currentInstructions();
}