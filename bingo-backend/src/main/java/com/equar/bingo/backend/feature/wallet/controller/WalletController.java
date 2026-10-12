package com.equar.bingo.backend.feature.wallet.controller;

import com.equar.bingo.backend.common.api.PageResponse;
import com.equar.bingo.backend.common.idempotency.IdempotentResult;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.wallet.dto.DepositClaimRequest;
import com.equar.bingo.backend.feature.wallet.dto.DepositClaimResponse;
import com.equar.bingo.backend.feature.wallet.dto.WalletBalanceResponse;
import com.equar.bingo.backend.feature.wallet.dto.WalletTransactionResponse;
import com.equar.bingo.backend.feature.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
@PreAuthorize("hasRole('PLAYER')")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/balance")
    public WalletBalanceResponse balance(@AuthenticationPrincipal BingoUserPrincipal principal) {
        return walletService.getBalance(principal);
    }

    @GetMapping("/transactions")
    public PageResponse<WalletTransactionResponse> transactions(@AuthenticationPrincipal BingoUserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(walletService.getTransactions(principal, pageable));
    }

    @PostMapping("/deposits")
    public ResponseEntity<DepositClaimResponse> createDeposit(@AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody DepositClaimRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        IdempotentResult<DepositClaimResponse> result = walletService.submitDeposit(principal, request, idempotencyKey);
        return ResponseEntity.status(result.statusCode()).body(result.body());
    }

    @GetMapping("/deposits")
    public PageResponse<DepositClaimResponse> deposits(@AuthenticationPrincipal BingoUserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(walletService.getOwnDeposits(principal, pageable));
    }
}