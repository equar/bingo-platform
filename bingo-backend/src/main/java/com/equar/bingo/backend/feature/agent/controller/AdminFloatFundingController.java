package com.equar.bingo.backend.feature.agent.controller;

import com.equar.bingo.backend.feature.agent.dto.FloatPostingResponse;
import com.equar.bingo.backend.feature.agent.dto.FloatReconciliationResponse;
import com.equar.bingo.backend.feature.agent.dto.FloatVerificationRequest;
import com.equar.bingo.backend.feature.agent.dto.FloatVerificationResponse;
import com.equar.bingo.backend.feature.agent.dto.ReviewFloatTopUpRequest;
import com.equar.bingo.backend.feature.agent.dto.SyntheticBankStatementRequest;
import com.equar.bingo.backend.feature.agent.service.FloatFundingService;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/admin")
public class AdminFloatFundingController {
    private final FloatFundingService service;

    public AdminFloatFundingController(FloatFundingService service) { this.service = service; }

    @PostMapping("/float-bank-statements/synthetic")
    @PreAuthorize("hasRole('FINANCE_VERIFIER')")
    public FloatVerificationResponse recordSyntheticStatement(@AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody SyntheticBankStatementRequest request) {
        return service.recordSyntheticStatement(principal, request);
    }

    @PostMapping("/agents/float-top-up-requests/{requestId}/verify")
    @PreAuthorize("hasRole('FINANCE_VERIFIER')")
    public FloatVerificationResponse verify(@AuthenticationPrincipal BingoUserPrincipal principal,
            @PathVariable Long requestId, @Valid @RequestBody FloatVerificationRequest request) {
        return service.verify(requestId, principal, request);
    }

    @GetMapping("/agents/float-top-up-requests/{requestId}/verifications")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_VERIFIER','FINANCE_APPROVER')")
    public List<FloatVerificationResponse> verificationHistory(@PathVariable Long requestId) {
        return service.verificationHistory(requestId);
    }

    @PostMapping("/agents/float-top-up-requests/{requestId}/post")
    @PreAuthorize("hasRole('FINANCE_APPROVER')")
    public FloatPostingResponse post(@AuthenticationPrincipal BingoUserPrincipal principal,
            @PathVariable Long requestId, @Valid @RequestBody ReviewFloatTopUpRequest request) {
        if (!request.approve()) {
            throw new com.equar.bingo.backend.common.exception.BadRequestException(
                    "APPROVAL_REQUIRED", "This endpoint only posts verified approvals");
        }
        return service.postVerifiedRequest(requestId, principal, request.reviewNote());
    }

    @GetMapping("/float-accounting/postings")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_APPROVER')")
    public List<FloatPostingResponse> postings() { return service.postings(); }

    @GetMapping("/float-reconciliation/daily")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_APPROVER')")
    public FloatReconciliationResponse dailyReconciliation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.reconcile(date);
    }
}
