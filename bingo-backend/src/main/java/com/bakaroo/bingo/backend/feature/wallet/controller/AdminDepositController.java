package com.bakaroo.bingo.backend.feature.wallet.controller;

import com.bakaroo.bingo.backend.common.api.PageResponse;
import com.bakaroo.bingo.backend.common.idempotency.IdempotentResult;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.wallet.dto.DepositClaimResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.DepositReviewRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.DepositPostingResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.DepositVerificationRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.DepositVerificationResponse;
import com.bakaroo.bingo.backend.feature.wallet.entity.DepositClaimStatus;
import com.bakaroo.bingo.backend.feature.wallet.service.DepositFinancialService;
import com.bakaroo.bingo.backend.feature.wallet.service.WalletService;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/deposits")
@PreAuthorize("hasAnyRole('ADMIN','FINANCE_VERIFIER','FINANCE_APPROVER')")
public class AdminDepositController {

    private final WalletService walletService;
    private final DepositFinancialService depositFinancialService;

    public AdminDepositController(WalletService walletService, DepositFinancialService depositFinancialService) {
        this.walletService = walletService;
        this.depositFinancialService = depositFinancialService;
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
        IdempotentResult<DepositClaimResponse> result = depositFinancialService.approve(id, principal,
                request.reviewNote(),
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

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('FINANCE_VERIFIER')")
    public DepositVerificationResponse verify(@PathVariable Long id,
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody DepositVerificationRequest request) {
        return depositFinancialService.verify(id, principal, request);
    }

    @GetMapping("/{id}/verifications")
    public List<DepositVerificationResponse> verificationHistory(@PathVariable Long id) {
        return depositFinancialService.verificationHistory(id);
    }

    @GetMapping("/{id}/posting")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_APPROVER')")
    public DepositPostingResponse posting(@PathVariable Long id) {
        return depositFinancialService.getPosting(id);
    }
}