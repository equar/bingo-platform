package com.equar.bingo.backend.feature.admin.controller;

import com.equar.bingo.backend.common.api.PageResponse;
import com.equar.bingo.backend.feature.admin.dto.*;
import com.equar.bingo.backend.feature.admin.service.AdminReadService;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.game.entity.GameSessionState;
import com.equar.bingo.backend.feature.audit.dto.AuditLogDetailResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
            @RequestParam(required = false) AccountStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(adminReadService.searchPlayers(query, status, pageable));
    }

    @GetMapping("/players/{playerId}")
    public AdminPlayerDetailResponse player(@PathVariable Long playerId) {
        return adminReadService.playerDetail(playerId);
    }

    @GetMapping("/sessions")
    public PageResponse<AdminSessionSummaryResponse> sessions(@RequestParam(required = false) String query,
            @RequestParam(required = false) GameSessionState state,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Boolean upcoming,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(adminReadService.sessions(query, state, roomId, upcoming, pageable));
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
    public PageResponse<AdminAuditLogSummaryResponse> auditLogs(
            @PageableDefault(size = 20, sort = { "createdAt", "id" }, direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(adminReadService.auditLogs(pageable));
    }

    @GetMapping("/audit-logs/{auditLogId}")
    public AuditLogDetailResponse auditLog(@PathVariable Long auditLogId) {
        return adminReadService.auditLogDetail(auditLogId);
    }

    @GetMapping("/review-queue")
    public PageResponse<AdminReviewQueueItemResponse> reviewQueue(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(adminReadService.reviewQueue(pageable));
    }
}
