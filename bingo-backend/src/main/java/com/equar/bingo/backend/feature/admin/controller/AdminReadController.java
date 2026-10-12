package com.equar.bingo.backend.feature.admin.controller;

import com.equar.bingo.backend.common.api.PageResponse;
import com.equar.bingo.backend.feature.admin.dto.*;
import com.equar.bingo.backend.feature.admin.service.AdminReadService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReadController {

    private final AdminReadService adminReadService;

    public AdminReadController(AdminReadService adminReadService) {
        this.adminReadService = adminReadService;
    }

    @GetMapping("/dashboard/metrics")
    public AdminDashboardMetricsResponse metrics() {
        return adminReadService.dashboardMetrics();
    }

    @GetMapping("/players")
    public PageResponse<AdminPlayerSummaryResponse> players(@RequestParam(required = false) String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(adminReadService.searchPlayers(query, pageable));
    }

    @GetMapping("/players/{playerId}")
    public AdminPlayerDetailResponse player(@PathVariable Long playerId) {
        return adminReadService.playerDetail(playerId);
    }

    @GetMapping("/sessions")
    public PageResponse<AdminSessionSummaryResponse> sessions(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(adminReadService.sessions(pageable));
    }

    @GetMapping("/sessions/live")
    public java.util.List<AdminSessionSummaryResponse> liveSessions() {
        return adminReadService.liveSessions();
    }

    @GetMapping("/agents")
    public PageResponse<AdminAgentSummaryResponse> agents(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(adminReadService.agents(pageable));
    }

    @GetMapping("/agents/{agentId}")
    public AdminAgentSummaryResponse agent(@PathVariable Long agentId) {
        return adminReadService.agentDetail(agentId);
    }

    @GetMapping("/audit-logs")
    public PageResponse<AdminAuditLogSummaryResponse> auditLogs(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(adminReadService.auditLogs(pageable));
    }

    @GetMapping("/review-queue")
    public PageResponse<AdminReviewQueueItemResponse> reviewQueue(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(adminReadService.reviewQueue(pageable));
    }
}