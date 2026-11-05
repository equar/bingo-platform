package com.bakaroo.bingo.backend.feature.admin.controller;

import com.bakaroo.bingo.backend.common.api.PageResponse;
import com.bakaroo.bingo.backend.feature.admin.dto.AdminSessionSummaryResponse;
import com.bakaroo.bingo.backend.feature.admin.service.AdminReadService;
import com.bakaroo.bingo.backend.feature.game.dto.SessionTicketLookupResponse;
import com.bakaroo.bingo.backend.feature.game.service.BingoGameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/v1/admin/support/session-tickets")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT')")
@Tag(name = "Support Session Tickets")
public class SessionTicketLookupController {

    private final AdminReadService adminReadService;
    private final BingoGameService bingoGameService;

    public SessionTicketLookupController(AdminReadService adminReadService, BingoGameService bingoGameService) {
        this.adminReadService = adminReadService;
        this.bingoGameService = bingoGameService;
    }

    @GetMapping("/sessions")
    @Operation(operationId = "searchSessionTicketSessions", summary = "Search sessions for ticket lookup")
    public PageResponse<AdminSessionSummaryResponse> sessions(
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = { "scheduledStartAt", "id" }, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.from(adminReadService.sessions(query, null, null, null, pageable));
    }

    @GetMapping("/sessions/{sessionId}/tickets")
    @Operation(operationId = "searchSessionTickets", summary = "Search tickets purchased for a session")
    public PageResponse<SessionTicketLookupResponse> tickets(
            @PathVariable Long sessionId,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(bingoGameService.getAdminSessionTickets(sessionId, query, pageable));
    }
}
