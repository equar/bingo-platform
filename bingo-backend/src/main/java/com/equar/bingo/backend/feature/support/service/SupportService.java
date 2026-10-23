package com.equar.bingo.backend.feature.support.service;

import com.equar.bingo.backend.common.exception.BadRequestException;
import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.common.exception.NotFoundException;
import com.equar.bingo.backend.common.api.PageResponse;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.support.dto.*;
import com.equar.bingo.backend.feature.support.entity.*;
import com.equar.bingo.backend.feature.support.repository.InternalNoteRepository;
import com.equar.bingo.backend.feature.support.repository.SupportMessageRepository;
import com.equar.bingo.backend.feature.support.repository.SupportTicketRepository;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.wallet.entity.DepositClaim;
import com.equar.bingo.backend.feature.wallet.repository.DepositClaimRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportService {

    private final SupportTicketRepository supportTicketRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final InternalNoteRepository internalNoteRepository;
    private final UserRepository userRepository;
    private final DepositClaimRepository depositClaimRepository;
    private final WalletRepository walletRepository;
    private final ObjectMapper objectMapper;

    public SupportService(SupportTicketRepository supportTicketRepository,
            SupportMessageRepository supportMessageRepository,
            InternalNoteRepository internalNoteRepository,
            UserRepository userRepository,
            DepositClaimRepository depositClaimRepository,
            WalletRepository walletRepository,
            ObjectMapper objectMapper) {
        this.supportTicketRepository = supportTicketRepository;
        this.supportMessageRepository = supportMessageRepository;
        this.internalNoteRepository = internalNoteRepository;
        this.userRepository = userRepository;
        this.depositClaimRepository = depositClaimRepository;
        this.walletRepository = walletRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SupportTicketResponse createTicket(BingoUserPrincipal principal, SupportTicketCreateRequest request) {
        User requester = principal.getUser();
        ensureRequesterRole(requester);
        validateLinkedEntityOwnership(requester, request.linkedEntityType(), request.linkedEntityId());
        String operationId = UUID.randomUUID().toString();

        SupportTicket ticket = SupportTicket.create(requester, request.subject().trim(), request.description().trim(),
                request.category(), request.priority(), request.linkedEntityType(), request.linkedEntityId(),
                request.linkedEntityReference(), operationId);
        ticket = supportTicketRepository.save(ticket);
        supportMessageRepository
                .save(SupportMessage.create(ticket, requester, request.description().trim(), operationId));
        return toResponse(ticket, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<SupportTicketSummaryResponse> getMyTickets(BingoUserPrincipal principal, Pageable pageable) {
        User requester = principal.getUser();
        ensureRequesterRole(requester);
        return PageResponse
                .from(supportTicketRepository.findByRequesterIdOrderByCreatedAtDesc(requester.getId(), pageable)
                        .map(this::toSummaryResponse));
    }

    @Transactional(readOnly = true)
    public Page<SupportTicketSummaryResponse> getAdminPlayerTickets(Long playerId, Pageable pageable) {
        return supportTicketRepository.findByRequesterIdOrderByCreatedAtDesc(playerId, pageable)
                .map(this::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public SupportTicketResponse getMyTicket(BingoUserPrincipal principal, Long ticketId) {
        User requester = principal.getUser();
        ensureRequesterRole(requester);
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("SUPPORT_TICKET_NOT_FOUND", "Support ticket not found"));
        if (!ticket.getRequester().getId().equals(requester.getId()) && !isAdmin(requester)) {
            throw new BadRequestException("SUPPORT_TICKET_OWNERSHIP_REQUIRED", "You can only view your own ticket");
        }
        return toResponse(ticket, false);
    }

    @Transactional
    public SupportTicketResponse addMessage(BingoUserPrincipal principal, Long ticketId,
            SupportTicketMessageRequest request) {
        User requester = principal.getUser();
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("SUPPORT_TICKET_NOT_FOUND", "Support ticket not found"));
        if (!ticket.getRequester().getId().equals(requester.getId()) && !isAdmin(requester)) {
            throw new BadRequestException("SUPPORT_TICKET_OWNERSHIP_REQUIRED", "You can only reply to your own ticket");
        }
        String operationId = UUID.randomUUID().toString();
        supportMessageRepository.save(SupportMessage.create(ticket, requester, request.body().trim(), operationId));
        ticket.setStatus(SupportTicketStatus.WAITING_FOR_CUSTOMER);
        return toResponse(ticket, false);
    }

    @Transactional(readOnly = true)
    public Page<SupportTicketSummaryResponse> searchTickets(String query, SupportTicketStatus status,
            SupportTicketCategory category, SupportTicketPriority priority, Long assignedToUserId, Pageable pageable) {
        List<SupportTicketSummaryResponse> items = supportTicketRepository.findAll().stream()
                .filter(ticket -> status == null || ticket.getStatus() == status)
                .filter(ticket -> category == null || ticket.getCategory() == category)
                .filter(ticket -> priority == null || ticket.getPriority() == priority)
                .filter(ticket -> assignedToUserId == null
                        || (ticket.getAssignedTo() != null && ticket.getAssignedTo().getId().equals(assignedToUserId)))
                .filter(ticket -> query == null || matches(ticket, query))
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .map(this::toSummaryResponse)
                .toList();
        return toPage(items, pageable);
    }

    @Transactional(readOnly = true)
    public List<SupportAssigneeResponse> listAssignees() {
        return userRepository.findAdminUsers(List.of(UserRole.ADMIN, UserRole.SUPPORT), null,
                        AccountStatus.ACTIVE, Pageable.unpaged())
                .map(user -> new SupportAssigneeResponse(user.getId(), user.getDisplayName(), user.getRoles()))
                .getContent();
    }

    @Transactional(readOnly = true)
    public SupportTicketResponse getTicket(Long ticketId) {
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("SUPPORT_TICKET_NOT_FOUND", "Support ticket not found"));
        return toResponse(ticket, true);
    }

    @Transactional
    public SupportTicketResponse assignTicket(Long ticketId, BingoUserPrincipal principal,
            SupportTicketAssignmentRequest request) {
        User admin = requireAdminOrSupport(principal.getUser());
        SupportTicket ticket = supportTicketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new NotFoundException("SUPPORT_TICKET_NOT_FOUND", "Support ticket not found"));
        User assignee = userRepository.findById(request.assignedToUserId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Assignee not found"));
        if (!assignee.isActive() || (!assignee.hasRole(UserRole.ADMIN) && !assignee.hasRole(UserRole.SUPPORT))) {
            throw new BadRequestException("SUPPORT_ASSIGNEE_REQUIRED",
                    "Tickets can only be assigned to active admin or support accounts");
        }
        ticket.setAssignedTo(assignee);
        ticket.setStatus(SupportTicketStatus.IN_PROGRESS);
        supportTicketRepository.save(ticket);
        return toResponse(ticket, true);
    }

    @Transactional
    public SupportTicketResponse addAdminReply(Long ticketId, BingoUserPrincipal principal,
            SupportTicketMessageRequest request) {
        User admin = requireAdminOrSupport(principal.getUser());
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("SUPPORT_TICKET_NOT_FOUND", "Support ticket not found"));
        String operationId = UUID.randomUUID().toString();
        supportMessageRepository.save(SupportMessage.create(ticket, admin, request.body().trim(), operationId));
        ticket.setStatus(SupportTicketStatus.IN_PROGRESS);
        return toResponse(ticket, true);
    }

    @Transactional
    public SupportTicketResponse addInternalNote(Long ticketId, BingoUserPrincipal principal,
            SupportTicketMessageRequest request) {
        User admin = requireAdminOrSupport(principal.getUser());
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("SUPPORT_TICKET_NOT_FOUND", "Support ticket not found"));
        internalNoteRepository
                .save(InternalNote.create(ticket, admin, request.body().trim(), UUID.randomUUID().toString()));
        return toResponse(ticket, true);
    }

    @Transactional
    public SupportTicketResponse updateStatus(Long ticketId, BingoUserPrincipal principal,
            SupportTicketStatusUpdateRequest request) {
        User admin = requireAdminOrSupport(principal.getUser());
        SupportTicket ticket = supportTicketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new NotFoundException("SUPPORT_TICKET_NOT_FOUND", "Support ticket not found"));
        if (ticket.getStatus() == SupportTicketStatus.CLOSED && request.status() != SupportTicketStatus.CLOSED) {
            throw new ConflictException("CLOSED_TICKET_TERMINAL", "A closed ticket cannot transition to another status");
        }
        if ((request.status() == SupportTicketStatus.RESOLVED || request.status() == SupportTicketStatus.CLOSED)
                && (request.resolutionSummary() == null || request.resolutionSummary().isBlank())) {
            throw new BadRequestException("RESOLUTION_SUMMARY_REQUIRED",
                    "Resolution summary is required when closing a ticket");
        }
        ticket.setStatus(request.status());
        ticket.setResolutionSummary(request.resolutionSummary());
        if (request.status() == SupportTicketStatus.RESOLVED) {
            ticket.setResolvedAt(Instant.now());
        }
        if (request.status() == SupportTicketStatus.CLOSED) {
            ticket.setClosedAt(Instant.now());
        }
        return toResponse(ticket, true);
    }

    private boolean matches(SupportTicket ticket, String query) {
        String normalized = query.toLowerCase();
        return ticket.getTicketNumber().toLowerCase().contains(normalized)
                || ticket.getSubject().toLowerCase().contains(normalized)
                || ticket.getRequester().getDisplayName().toLowerCase().contains(normalized)
                || ticket.getRequester().getNormalizedPhone().toLowerCase().contains(normalized);
    }

    private Page<SupportTicketSummaryResponse> toPage(List<SupportTicketSummaryResponse> items, Pageable pageable) {
        int fromIndex = Math.min((int) pageable.getOffset(), items.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), items.size());
        return new PageImpl<>(items.subList(fromIndex, toIndex), pageable, items.size());
    }

    private SupportTicketResponse toResponse(SupportTicket ticket, boolean includeInternalNotes) {
        return new SupportTicketResponse(ticket.getId(), ticket.getTicketNumber(), ticket.getRequester().getId(),
                ticket.getRequester().getDisplayName(), ticket.getSubject(), ticket.getDescription(),
                ticket.getCategory(),
                ticket.getPriority(), ticket.getStatus(),
                ticket.getLinkedEntityType() == null ? null : ticket.getLinkedEntityType().name(),
                ticket.getLinkedEntityId(),
                ticket.getLinkedEntityReference(),
                ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getId(),
                ticket.getResolutionSummary(), ticket.getCreatedAt(), ticket.getUpdatedAt(),
                supportMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()).stream()
                        .map(message -> new SupportMessageResponse(message.getId(), message.getAuthor().getId(),
                                message.getBody(), message.getCreatedAt()))
                        .toList(),
                includeInternalNotes
                        ? internalNoteRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()).stream()
                                .map(note -> new InternalNoteResponse(note.getId(), note.getAuthor().getId(),
                                        note.getBody(),
                                        note.getCreatedAt()))
                                .toList()
                        : List.of());
    }

    private SupportTicketSummaryResponse toSummaryResponse(SupportTicket ticket) {
        return new SupportTicketSummaryResponse(ticket.getId(), ticket.getTicketNumber(), ticket.getRequester().getId(),
                ticket.getSubject(), ticket.getCategory(), ticket.getPriority(), ticket.getStatus(),
                ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getId(),
                ticket.getLinkedEntityType() == null ? null : ticket.getLinkedEntityType().name(),
                ticket.getLinkedEntityId(),
                ticket.getCreatedAt(), ticket.getUpdatedAt());
    }

    private void ensureRequesterRole(User user) {
        if (!(user.hasRole(UserRole.PLAYER) || user.hasRole(UserRole.AGENT) || user.hasRole(UserRole.ADMIN))) {
            throw new BadRequestException("SUPPORT_ROLE_REQUIRED",
                    "Only player and agent accounts can create support tickets");
        }
    }

    private User requireAdminOrSupport(User user) {
        if (!(user.hasRole(UserRole.ADMIN) || user.hasRole(UserRole.SUPPORT))) {
            throw new BadRequestException("SUPPORT_ROLE_REQUIRED", "This endpoint is available to support staff only");
        }
        return user;
    }

    private boolean isAdmin(User user) {
        return user.hasRole(UserRole.ADMIN);
    }

    private void validateLinkedEntityOwnership(User requester, SupportLinkedEntityType type, Long id) {
        if (type == null || id == null) {
            return;
        }
        switch (type) {
            case USER -> {
                if (!requester.getId().equals(id)) {
                    throw new BadRequestException("LINKED_ENTITY_OWNERSHIP_REQUIRED",
                            "Linked user reference must point to the current account");
                }
            }
            case WALLET -> walletRepository.findById(id)
                    .filter(wallet -> wallet.getOwner().getId().equals(requester.getId()))
                    .orElseThrow(() -> new BadRequestException("LINKED_ENTITY_OWNERSHIP_REQUIRED",
                            "Linked wallet must belong to the current account"));
            case DEPOSIT_CLAIM -> depositClaimRepository.findById(id)
                    .filter(claim -> claim.getPlayer().getId().equals(requester.getId()))
                    .orElseThrow(() -> new BadRequestException("LINKED_ENTITY_OWNERSHIP_REQUIRED",
                            "Linked deposit must belong to the current account"));
            default -> {
            }
        }
    }
}
