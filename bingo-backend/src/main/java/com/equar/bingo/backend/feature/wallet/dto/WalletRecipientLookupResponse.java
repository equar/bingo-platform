package com.equar.bingo.backend.feature.wallet.dto;

public record WalletRecipientLookupResponse(
        Long userId,
        String displayName,
        String normalizedPhone) {
}