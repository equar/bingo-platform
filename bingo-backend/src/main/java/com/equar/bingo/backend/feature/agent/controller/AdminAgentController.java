package com.equar.bingo.backend.feature.agent.controller;

import com.equar.bingo.backend.feature.agent.dto.AgentProfileResponse;
import com.equar.bingo.backend.feature.agent.dto.FloatTopUpRequestResponse;
import com.equar.bingo.backend.feature.agent.dto.CommissionRuleRequest;
import com.equar.bingo.backend.feature.agent.dto.ReviewFloatTopUpRequest;
import com.equar.bingo.backend.feature.agent.service.AgentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/agents")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAgentController {

    private final AgentService agentService;

    public AdminAgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/{userId}/approve")
    public AgentProfileResponse approve(@PathVariable Long userId, @Valid @RequestBody AgentProfileResponse request) {
        return agentService.adminApproveAgent(userId, request);
    }

    @PostMapping("/{agentId}/suspend")
    public AgentProfileResponse suspend(@PathVariable Long agentId) {
        return agentService.adminSuspendAgent(agentId);
    }

    @PostMapping("/commission-rules")
    public CommissionRuleRequest commissionRule(
            @AuthenticationPrincipal com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal principal,
            @Valid @RequestBody CommissionRuleRequest request) {
        return agentService.upsertCommissionRule(principal, request);
    }

    @PostMapping("/float-top-up-requests/{requestId}/review")
    public FloatTopUpRequestResponse reviewTopUp(@PathVariable Long requestId,
            @AuthenticationPrincipal com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal principal,
            @Valid @RequestBody ReviewFloatTopUpRequest request) {
        return agentService.reviewTopUpRequest(requestId, principal, request);
    }
}