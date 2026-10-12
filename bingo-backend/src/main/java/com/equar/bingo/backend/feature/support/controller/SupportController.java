package com.equar.bingo.backend.feature.support.controller;

import com.equar.bingo.backend.common.api.PageResponse;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.support.dto.SupportTicketCreateRequest;
import com.equar.bingo.backend.feature.support.dto.SupportTicketMessageRequest;
import com.equar.bingo.backend.feature.support.dto.SupportTicketResponse;
import com.equar.bingo.backend.feature.support.dto.SupportTicketSummaryResponse;
import com.equar.bingo.backend.feature.support.service.SupportService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/support/tickets")
@PreAuthorize("hasAnyRole('PLAYER','AGENT')")
public class SupportController {

    private final SupportService supportService;

    public SupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @PostMapping
    public SupportTicketResponse create(@AuthenticationPrincipal BingoUserPrincipal principal,
            @Valid @RequestBody SupportTicketCreateRequest request) {
        return supportService.createTicket(principal, request);
    }

    @GetMapping
    public PageResponse<SupportTicketSummaryResponse> myTickets(@AuthenticationPrincipal BingoUserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return supportService.getMyTickets(principal, pageable);
    }

    @GetMapping("/{ticketId}")
    public SupportTicketResponse myTicket(@AuthenticationPrincipal BingoUserPrincipal principal,
            @PathVariable Long ticketId) {
        return supportService.getMyTicket(principal, ticketId);
    }

    @PostMapping("/{ticketId}/messages")
    public SupportTicketResponse reply(@AuthenticationPrincipal BingoUserPrincipal principal,
            @PathVariable Long ticketId,
            @Valid @RequestBody SupportTicketMessageRequest request) {
        return supportService.addMessage(principal, ticketId, request);
    }
}