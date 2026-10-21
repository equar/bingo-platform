package com.equar.bingo.backend.feature.agent.controller;

import com.equar.bingo.backend.common.api.PageResponse;
import com.equar.bingo.backend.common.idempotency.IdempotentResult;
import com.equar.bingo.backend.feature.agent.dto.*;
import com.equar.bingo.backend.feature.agent.service.AgentService;
import com.equar.bingo.backend.feature.auth.dto.AuthResponse;
import com.equar.bingo.backend.feature.auth.dto.UserProfileResponse;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.game.dto.TicketPurchaseResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.util.List;
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

import org.springframework.data.domain.Pageable;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/login")
    @Operation
    @SecurityRequirements
    public AuthResponse login(@Valid @RequestBody AgentLoginRequest request) {
        return agentService.login(request);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('AGENT')")
    public AgentDashboardResponse dashboard(@AuthenticationPrincipal BingoUserPrincipal principal) {
        return agentService.dashboard(principal);
    }

    @GetMapping("/players/lookup")
    @PreAuthorize("hasRole('AGENT')")
    public UserProfileResponse lookupPlayer(@RequestParam String phoneNumber) {
        return agentService.lookupPlayer(phoneNumber);
    }

    @PostMapping("/players")
    @PreAuthorize("hasRole('AGENT')")
    public UserProfileResponse registerPlayer(@Valid @RequestBody AgentPlayerRegistrationRequest request) {
        return agentService.registerPlayer(request);
    }

    @PostMapping("/tickets")
    @PreAuthorize("hasRole('AGENT')")
    @ApiResponse(responseCode = "201", description = "Ticket sold", useReturnTypeSchema = true)
    public ResponseEntity<TicketPurchaseResponse> sellTicket(@AuthenticationPrincipal BingoUserPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AgentTicketSaleRequest request) {
        IdempotentResult<TicketPurchaseResponse> result = agentService.sellTicket(principal, request, idempotencyKey);
        return ResponseEntity.status(result.statusCode()).body(result.body());
    }

    @PostMapping("/float/transfers")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<AgentFloatResponse> transferFloat(@AuthenticationPrincipal BingoUserPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AgentFloatTransferRequest request) {
        IdempotentResult<AgentFloatResponse> result = agentService.transferFloat(principal, request, idempotencyKey);
        return ResponseEntity.status(result.statusCode()).body(result.body());
    }

    @GetMapping("/payouts/{code}")
    @PreAuthorize("hasRole('AGENT')")
    public AgentPayoutLookupResponse payout(@AuthenticationPrincipal BingoUserPrincipal principal,
            @PathVariable String code) {
        return agentService.lookupApprovedPayout(principal, code);
    }

    @PostMapping("/payouts/verify")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<PayoutVerificationResponse> verifyPayout(
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AgentPayoutVerificationRequest request) {
        IdempotentResult<PayoutVerificationResponse> result = agentService.verifyAndDisbursePayout(principal, request,
                idempotencyKey);
        return ResponseEntity.status(result.statusCode()).body(result.body());
    }

    @GetMapping("/float")
    @PreAuthorize("hasRole('AGENT')")
    public AgentFloatResponse floatBalance(@AuthenticationPrincipal BingoUserPrincipal principal) {
        return agentService.getFloat(principal);
    }

    @GetMapping("/float/history")
    @PreAuthorize("hasRole('AGENT')")
    public List<AgentFloatTransactionResponse> floatHistory(@AuthenticationPrincipal BingoUserPrincipal principal) {
        return agentService.getFloatHistory(principal);
    }

    @PostMapping("/float-top-up-requests")
    @PreAuthorize("hasRole('AGENT')")
    @ApiResponse(responseCode = "201", description = "Float top-up request created", useReturnTypeSchema = true)
    public ResponseEntity<FloatTopUpRequestResponse> submitTopUp(
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SubmitFloatTopUpRequest request) {
        IdempotentResult<FloatTopUpRequestResponse> result = agentService.submitTopUpRequest(principal, request,
                idempotencyKey);
        return ResponseEntity.status(result.statusCode()).body(result.body());
    }

    @GetMapping("/float-top-up-requests")
    @PreAuthorize("hasRole('AGENT')")
    public List<FloatTopUpRequestResponse> topUpRequests(@AuthenticationPrincipal BingoUserPrincipal principal) {
        return agentService.getTopUpRequests(principal);
    }

    @GetMapping("/commission-history")
    @PreAuthorize("hasRole('AGENT')")
    public List<CommissionAccrualResponse> commissionHistory(@AuthenticationPrincipal BingoUserPrincipal principal) {
        return agentService.getCommissionHistory(principal);
    }
}