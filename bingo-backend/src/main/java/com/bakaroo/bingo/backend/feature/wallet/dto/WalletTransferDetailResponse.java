package com.bakaroo.bingo.backend.feature.wallet.dto;

import java.util.List;

public record WalletTransferDetailResponse(
        WalletTransferResponse transfer,
        List<WalletTransferLedgerEntryResponse> ledgerEntries) {
    public WalletTransferDetailResponse {
        ledgerEntries = List.copyOf(ledgerEntries);
    }
}
