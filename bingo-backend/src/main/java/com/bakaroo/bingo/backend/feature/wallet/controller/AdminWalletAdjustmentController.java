package com.bakaroo.bingo.backend.feature.wallet.controller;

import com.bakaroo.bingo.backend.common.idempotency.IdempotentResult;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletAdjustmentRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletAdjustmentResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletAdjustmentRequestResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletAdjustmentReviewRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletAdjustmentSubmissionRequest;
import com.bakaroo.bingo.backend.feature.wallet.service.WalletAdjustmentService;
import com.bakaroo.bingo.backend.feature.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('ADMIN','FINANCE_VERIFIER','FINANCE_APPROVER')")
public class AdminWalletAdjustmentController {

    private final WalletService walletService;
    private final WalletAdjustmentService adjustmentService;

    public AdminWalletAdjustmentController(WalletService walletService, WalletAdjustmentService adjustmentService) {
        this.walletService = walletService;
        this.adjustmentService = adjustmentService;
    }

    @PostMapping("/players/{id}/wallet-adjustments")
    public ResponseEntity<WalletAdjustmentResponse> adjust(@PathVariable Long id,
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody WalletAdjustmentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        IdempotentResult<WalletAdjustmentResponse> result = walletService.adjustPlayerWallet(id, principal, request,
                idempotencyKey);
        return ResponseEntity.status(result.statusCode()).body(result.body());
    }

    @PostMapping("/players/{id}/wallet-adjustments/requests")
    @PreAuthorize("hasRole('FINANCE_VERIFIER')")
    public WalletAdjustmentRequestResponse submit(@PathVariable Long id,
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody WalletAdjustmentSubmissionRequest request) {
        return adjustmentService.submit(id, principal, request);
    }

    @GetMapping("/players/{id}/wallet-adjustments/requests")
    public List<WalletAdjustmentRequestResponse> history(@PathVariable Long id) {
        return adjustmentService.history(id);
    }

    @GetMapping("/wallet-adjustment-requests")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_APPROVER')")
    public List<WalletAdjustmentRequestResponse> pending() {
        return adjustmentService.pending();
    }

    @PostMapping("/wallet-adjustment-requests/{requestId}/approve")
    @PreAuthorize("hasRole('FINANCE_APPROVER')")
    public WalletAdjustmentRequestResponse approve(@PathVariable Long requestId,
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody WalletAdjustmentReviewRequest request) {
        return adjustmentService.approve(requestId, principal, request);
    }

    @PostMapping("/wallet-adjustment-requests/{requestId}/reject")
    @PreAuthorize("hasRole('FINANCE_APPROVER')")
    public WalletAdjustmentRequestResponse reject(@PathVariable Long requestId,
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody WalletAdjustmentReviewRequest request) {
        return adjustmentService.reject(requestId, principal, request);
    }
}