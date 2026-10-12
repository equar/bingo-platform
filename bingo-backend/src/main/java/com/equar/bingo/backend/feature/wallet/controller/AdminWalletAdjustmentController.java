package com.equar.bingo.backend.feature.wallet.controller;

import com.equar.bingo.backend.common.idempotency.IdempotentResult;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.wallet.dto.WalletAdjustmentRequest;
import com.equar.bingo.backend.feature.wallet.dto.WalletAdjustmentResponse;
import com.equar.bingo.backend.feature.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/players")
@PreAuthorize("hasRole('ADMIN')")
public class AdminWalletAdjustmentController {

    private final WalletService walletService;

    public AdminWalletAdjustmentController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/{id}/wallet-adjustments")
    public ResponseEntity<WalletAdjustmentResponse> adjust(@PathVariable Long id,
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody WalletAdjustmentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        IdempotentResult<WalletAdjustmentResponse> result = walletService.adjustPlayerWallet(id, principal, request,
                idempotencyKey);
        return ResponseEntity.status(result.statusCode()).body(result.body());
    }
}