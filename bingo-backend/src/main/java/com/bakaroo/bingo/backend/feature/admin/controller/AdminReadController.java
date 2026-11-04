package com.bakaroo.bingo.backend.feature.admin.controller;

import com.bakaroo.bingo.backend.common.api.PageResponse;
import com.bakaroo.bingo.backend.feature.admin.dto.*;
import com.bakaroo.bingo.backend.feature.admin.service.AdminReadService;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;
import com.bakaroo.bingo.backend.feature.audit.dto.AuditLogDetailResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.Instant;

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

    @GetMapping("/dashboard/trends")
    public AdminDashboardTrendsResponse trends(@RequestParam(defaultValue = "14") int days) {
        if (days < 7 || days > 90) {
            throw new com.bakaroo.bingo.backend.common.exception.BadRequestException(
                    "INVALID_TREND_RANGE", "Dashboard trend range must be between 7 and 90 days");
        }
        return adminReadService.dashboardTrends(days);
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
    public AdminAgentDetailResponse agent(@PathVariable Long agentId) {
        return adminReadService.agentDetail(agentId);
    }

    @GetMapping("/audit-logs")
    public PageResponse<AdminAuditLogSummaryResponse> auditLogs(
            @PageableDefault(size = 20, sort = { "createdAt", "id" }, direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(adminReadService.auditLogs(pageable));
    }

    @GetMapping("/reports/audit-activity")
    public PageResponse<AdminAuditLogSummaryResponse> auditActivityReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 100) Pageable pageable) {
        if (!from.isBefore(to)) {
            throw new com.bakaroo.bingo.backend.common.exception.BadRequestException(
                    "INVALID_REPORT_RANGE", "Report end must be after report start");
        }
        if (to.isAfter(from.plusSeconds(366L * 24 * 60 * 60))) {
            throw new com.bakaroo.bingo.backend.common.exception.BadRequestException(
                    "REPORT_RANGE_TOO_LARGE", "Reports are limited to 366 days");
        }
        return PageResponse.from(adminReadService.auditActivityReport(from, to, pageable));
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
