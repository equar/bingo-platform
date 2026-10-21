package com.equar.bingo.backend.feature.wallet.controller;

import com.equar.bingo.backend.common.api.PageResponse;
import com.equar.bingo.backend.common.idempotency.IdempotentResult;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.wallet.dto.DepositClaimResponse;
import com.equar.bingo.backend.feature.wallet.dto.DepositReviewRequest;
import com.equar.bingo.backend.feature.wallet.entity.DepositClaimStatus;
import com.equar.bingo.backend.feature.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/deposits")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDepositController {

    private final WalletService walletService;

    public AdminDepositController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    public PageResponse<DepositClaimResponse> list(@RequestParam(required = false) DepositClaimStatus status,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(walletService.getAdminDeposits(status, query, pageable));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<DepositClaimResponse> approve(@PathVariable Long id,
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody DepositReviewRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        IdempotentResult<DepositClaimResponse> result = walletService.approveDeposit(id, principal, request,
                idempotencyKey);
        return ResponseEntity.status(result.statusCode()).body(result.body());
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<DepositClaimResponse> reject(@PathVariable Long id,
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody DepositReviewRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        IdempotentResult<DepositClaimResponse> result = walletService.rejectDeposit(id, principal, request,
                idempotencyKey);
        return ResponseEntity.status(result.statusCode()).body(result.body());
    }
}