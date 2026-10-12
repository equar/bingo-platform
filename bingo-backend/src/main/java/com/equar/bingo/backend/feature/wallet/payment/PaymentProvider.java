package com.equar.bingo.backend.feature.wallet.payment;

public interface PaymentProvider {

    PaymentInstructions currentInstructions();
}