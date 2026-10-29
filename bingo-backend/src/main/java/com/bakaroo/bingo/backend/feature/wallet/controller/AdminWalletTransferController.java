package com.bakaroo.bingo.backend.feature.wallet.controller;

import com.bakaroo.bingo.backend.common.api.PageResponse;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletTransferResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletTransferDetailResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletTransferSettingsRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletTransferSettingsResponse;
import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransferStatus;
import com.bakaroo.bingo.backend.feature.wallet.service.WalletTransferService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/wallet/transfers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminWalletTransferController {

    private final WalletTransferService walletTransferService;

    public AdminWalletTransferController(WalletTransferService walletTransferService) {
        this.walletTransferService = walletTransferService;
    }

    @GetMapping("/settings")
    public WalletTransferSettingsResponse currentSettings() {
        return walletTransferService.currentSettings();
    }

    @GetMapping("/settings/history")
    public PageResponse<WalletTransferSettingsResponse> settingsHistory(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(walletTransferService.settingsHistory(pageable));
    }

    @PostMapping("/settings")
    public WalletTransferSettingsResponse updateSettings(@AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody WalletTransferSettingsRequest request) {
        return walletTransferService.saveSettings(principal, request);
    }

    @GetMapping
    public PageResponse<WalletTransferResponse> list(@RequestParam(required = false) String query,
            @RequestParam(required = false) WalletTransferStatus status,
            @RequestParam(required = false) String senderPhone,
            @RequestParam(required = false) String recipientPhone,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(walletTransferService.searchTransfers(query, status, senderPhone, recipientPhone,
                pageable));
    }

    @GetMapping("/{id}")
    public WalletTransferDetailResponse detail(@PathVariable Long id) {
        return walletTransferService.getTransferDetail(id);
    }
}
