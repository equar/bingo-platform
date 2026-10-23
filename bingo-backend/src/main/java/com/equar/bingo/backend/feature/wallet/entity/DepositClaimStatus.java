package com.equar.bingo.backend.feature.wallet.entity;

public enum DepositClaimStatus {
    PENDING,
    AWAITING_VERIFICATION,
    VERIFIED,
    VERIFICATION_EXCEPTION,
    APPROVED,
    REJECTED
}