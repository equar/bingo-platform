package com.bakaroo.bingo.backend.feature.admin.controller;

import com.bakaroo.bingo.backend.common.api.PageResponse;
import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import com.bakaroo.bingo.backend.common.validation.ReportRangeValidator;
import com.bakaroo.bingo.backend.feature.admin.dto.*;
import com.bakaroo.bingo.backend.feature.admin.service.AdminReadService;
import com.bakaroo.bingo.backend.feature.agent.entity.AgentStatus;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;
import com.bakaroo.bingo.backend.feature.audit.dto.AuditLogDetailResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
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
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
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
    public PageResponse<AdminAgentSummaryResponse> agents(@RequestParam(required = false) String query,
            @RequestParam(required = false) AgentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) List<String> sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("INVALID_PAGINATION",
                    "Agent page must be non-negative and size must be between 1 and 100");
        }
        Pageable pageable = PageRequest.of(page, size, agentSort(sort));
        return PageResponse.from(adminReadService.agents(query, status, pageable));
    }

    private Sort agentSort(List<String> sortParameters) {
        if (sortParameters == null || sortParameters.isEmpty()) {
            return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (String sortParameter : sortParameters) {
            String[] parts = sortParameter.split(",", -1);
            if (parts.length < 1 || parts.length > 2 || parts[0].isBlank()) {
                throw new BadRequestException("INVALID_SORT", "Agent sort must use field[,direction]");
            }
            Sort.Direction direction;
            try {
                direction = parts.length == 1 ? Sort.Direction.ASC : Sort.Direction.fromString(parts[1].trim());
            } catch (IllegalArgumentException exception) {
                throw new BadRequestException("INVALID_SORT_DIRECTION", "Unsupported agent sort direction");
            }
            orders.add(new Sort.Order(direction, parts[0].trim()));
        }
        return Sort.by(orders);
    }

    @GetMapping("/agents/{agentId}")
    public AdminAgentDetailResponse agent(@PathVariable Long agentId) {
        return adminReadService.agentDetail(agentId);
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AdminAuditLogSummaryResponse> auditLogs(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) Long auditLogId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) String operationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BadRequestException("INVALID_AUDIT_DATE_RANGE", "Audit search start must be before its end");
        }
        return PageResponse.from(adminReadService.searchAuditLogs(
                query, actorUserId, auditLogId, action, targetType, targetId, operationId, from, to, pageable));
    }

    @GetMapping("/reports/audit-activity")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AdminAuditLogSummaryResponse> auditActivityReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 100) Pageable pageable) {
        ReportRangeValidator.validate(from, to);
        return PageResponse.from(adminReadService.auditActivityReport(from, to, query, pageable));
    }

    @GetMapping("/reports/sessions")
    public PageResponse<AdminSessionSummaryResponse> sessionsReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) GameSessionState state,
            @PageableDefault(size = 100) Pageable pageable) {
        ReportRangeValidator.validate(from, to);
        return PageResponse.from(adminReadService.sessionsReport(from, to, query, state, pageable));
    }

    @GetMapping("/audit-logs/{auditLogId}")
    @PreAuthorize("hasRole('ADMIN')")
    public AuditLogDetailResponse auditLog(@PathVariable Long auditLogId) {
        return adminReadService.auditLogDetail(auditLogId);
    }

    @GetMapping("/review-queue")
    public PageResponse<AdminReviewQueueItemResponse> reviewQueue(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(adminReadService.reviewQueue(pageable));
    }
}
