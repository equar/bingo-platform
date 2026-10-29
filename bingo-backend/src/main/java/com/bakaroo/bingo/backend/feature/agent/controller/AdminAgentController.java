package com.bakaroo.bingo.backend.feature.agent.controller;

import com.bakaroo.bingo.backend.feature.agent.dto.AgentProfileResponse;
import com.bakaroo.bingo.backend.feature.agent.dto.FloatTopUpRequestResponse;
import com.bakaroo.bingo.backend.feature.agent.dto.CommissionRuleRequest;
import com.bakaroo.bingo.backend.feature.agent.dto.AdminCommissionRuleResponse;
import com.bakaroo.bingo.backend.feature.agent.dto.ReviewFloatTopUpRequest;
import com.bakaroo.bingo.backend.common.api.PageResponse;
import com.bakaroo.bingo.backend.feature.agent.dto.AdminFloatTopUpRequestResponse;
import com.bakaroo.bingo.backend.feature.agent.dto.AgentFloatResponse;
import com.bakaroo.bingo.backend.feature.agent.dto.AgentFloatTransactionResponse;
import com.bakaroo.bingo.backend.feature.agent.service.AgentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/agents")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAgentController {

    private final AgentService agentService;

    public AdminAgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/{agentId}/approve")
    public AgentProfileResponse approve(@PathVariable Long agentId) {
        return agentService.approveAgentApplication(agentId);
    }

    @PostMapping("/{agentId}/suspend")
    public AgentProfileResponse suspend(@PathVariable Long agentId) {
        return agentService.adminSuspendAgent(agentId);
    }

    @GetMapping("/commission-rules/current")
    public AdminCommissionRuleResponse currentCommissionRule() {
        return agentService.currentCommissionRule();
    }

    @PostMapping("/commission-rules")
    public CommissionRuleRequest commissionRule(
            @AuthenticationPrincipal com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal principal,
            @Valid @RequestBody CommissionRuleRequest request) {
        return agentService.upsertCommissionRule(principal, request);
    }

    @PostMapping("/float-top-up-requests/{requestId}/review")
    public FloatTopUpRequestResponse reviewTopUp(@PathVariable Long requestId,
            @AuthenticationPrincipal com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal principal,
            @Valid @RequestBody ReviewFloatTopUpRequest request) {
        return agentService.reviewTopUpRequest(requestId, principal, request);
    }

    @GetMapping("/float-top-up-requests")
    public PageResponse<AdminFloatTopUpRequestResponse> floatTopUpRequests(
            @PageableDefault(size = 20, sort = { "createdAt", "id" }, direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String query) {
        return agentService.adminTopUpRequests(pageable, status, query);
    }

    @GetMapping("/{agentId}/float")
    public AgentFloatResponse floatBalance(@PathVariable Long agentId) {
        return agentService.adminFloatBalance(agentId);
    }

    @GetMapping("/{agentId}/float/history")
    public PageResponse<AgentFloatTransactionResponse> floatHistory(
            @PathVariable Long agentId,
            @PageableDefault(size = 20, sort = { "createdAt", "id" }, direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return agentService.adminFloatHistory(agentId, pageable);
    }
}
