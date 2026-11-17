package com.bakaroo.bingo.backend.feature.wallet.controller;

import com.bakaroo.bingo.backend.common.api.PageResponse;
import com.bakaroo.bingo.backend.common.idempotency.IdempotentResult;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.wallet.dto.DepositClaimRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.DepositClaimResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletBalanceResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletRecipientLookupResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletTransferRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletOwnTransferResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletTransferResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletTransactionResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.WithdrawalRequestDto;
import com.bakaroo.bingo.backend.feature.wallet.dto.WithdrawalResponse;
import com.bakaroo.bingo.backend.feature.wallet.service.WithdrawalService;
import com.bakaroo.bingo.backend.feature.wallet.service.WalletService;
import com.bakaroo.bingo.backend.feature.wallet.service.WalletTransferService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
@PreAuthorize("hasRole('PLAYER')")
public class WalletController {

    private final WalletService walletService;
    private final WalletTransferService walletTransferService;
    private final WithdrawalService withdrawalService;

    public WalletController(WalletService walletService, WalletTransferService walletTransferService, WithdrawalService withdrawalService) {
        this.walletService = walletService;
        this.walletTransferService = walletTransferService;
        this.withdrawalService = withdrawalService;
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
    @ApiResponse(responseCode = "201", description = "Deposit claim created", useReturnTypeSchema = true)
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

    @PostMapping("/withdrawals")
    public ResponseEntity<WithdrawalResponse> createWithdrawal(@AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody WithdrawalRequestDto request, @RequestHeader("Idempotency-Key") String key) {
        var result = withdrawalService.submit(principal, request, key);
        return ResponseEntity.status(result.statusCode()).body(result.body());
    }

    @GetMapping("/withdrawals")
    public PageResponse<WithdrawalResponse> withdrawals(@AuthenticationPrincipal BingoUserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(withdrawalService.own(principal, pageable));
    }

    @GetMapping("/transfers/recipient")
    public WalletRecipientLookupResponse lookupRecipient(@RequestParam String phoneNumber) {
        return walletTransferService.lookupRecipient(phoneNumber);
    }

    @GetMapping("/transfers")
    public PageResponse<WalletOwnTransferResponse> transfers(@AuthenticationPrincipal BingoUserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(walletTransferService.getOwnTransfers(principal, pageable));
    }

    @PostMapping("/transfers")
    public ResponseEntity<WalletTransferResponse> transfer(@AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody WalletTransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        IdempotentResult<WalletTransferResponse> result = walletTransferService.transfer(principal, request,
                idempotencyKey);
        return ResponseEntity.status(result.statusCode()).body(result.body());
    }
}
