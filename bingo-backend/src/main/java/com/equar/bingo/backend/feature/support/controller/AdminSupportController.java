package com.equar.bingo.backend.feature.support.controller;

import com.equar.bingo.backend.common.api.PageResponse;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.support.dto.SupportTicketAssignmentRequest;
import com.equar.bingo.backend.feature.support.dto.SupportTicketMessageRequest;
import com.equar.bingo.backend.feature.support.dto.SupportTicketResponse;
import com.equar.bingo.backend.feature.support.dto.SupportTicketStatusUpdateRequest;
import com.equar.bingo.backend.feature.support.dto.SupportTicketSummaryResponse;
import com.equar.bingo.backend.feature.support.entity.SupportTicketCategory;
import com.equar.bingo.backend.feature.support.entity.SupportTicketStatus;
import com.equar.bingo.backend.feature.support.service.SupportService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/support/tickets")
@PreAuthorize("hasAnyRole('ADMIN','SUPPORT')")
public class AdminSupportController {

    private final SupportService supportService;

    public AdminSupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @GetMapping
    public PageResponse<SupportTicketSummaryResponse> search(@RequestParam(required = false) String query,
            @RequestParam(required = false) SupportTicketStatus status,
            @RequestParam(required = false) SupportTicketCategory category,
            @RequestParam(required = false) Long assignedToUserId,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(supportService.searchTickets(query, status, category, assignedToUserId, pageable));
    }

    @GetMapping("/{ticketId}")
    public SupportTicketResponse detail(@PathVariable Long ticketId) {
        return supportService.getTicket(ticketId);
    }

    @PostMapping("/{ticketId}/assign")
    public SupportTicketResponse assign(@PathVariable Long ticketId,
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody SupportTicketAssignmentRequest request) {
        return supportService.assignTicket(ticketId, principal, request);
    }

    @PostMapping("/{ticketId}/messages")
    public SupportTicketResponse reply(@PathVariable Long ticketId,
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody SupportTicketMessageRequest request) {
        return supportService.addAdminReply(ticketId, principal, request);
    }

    @PostMapping("/{ticketId}/notes")
    public SupportTicketResponse note(@PathVariable Long ticketId,
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody SupportTicketMessageRequest request) {
        return supportService.addInternalNote(ticketId, principal, request);
    }

    @PutMapping("/{ticketId}/status")
    public SupportTicketResponse status(@PathVariable Long ticketId,
            @AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody SupportTicketStatusUpdateRequest request) {
        return supportService.updateStatus(ticketId, principal, request);
    }
}