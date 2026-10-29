package com.bakaroo.bingo.backend.feature.support.service;

import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.support.dto.SupportTicketMessageRequest;
import com.bakaroo.bingo.backend.feature.support.dto.SupportTicketAssignmentRequest;
import com.bakaroo.bingo.backend.feature.support.dto.SupportTicketCreateRequest;
import com.bakaroo.bingo.backend.feature.support.dto.SupportTicketResponse;
import com.bakaroo.bingo.backend.feature.support.dto.SupportTicketStatusUpdateRequest;
import com.bakaroo.bingo.backend.feature.support.entity.InternalNote;
import com.bakaroo.bingo.backend.feature.support.entity.SupportMessage;
import com.bakaroo.bingo.backend.feature.support.entity.SupportTicket;
import com.bakaroo.bingo.backend.feature.support.entity.SupportTicketCategory;
import com.bakaroo.bingo.backend.feature.support.entity.SupportTicketPriority;
import com.bakaroo.bingo.backend.feature.support.entity.SupportTicketStatus;
import com.bakaroo.bingo.backend.feature.support.entity.SupportLinkedEntityType;
import com.bakaroo.bingo.backend.feature.support.repository.InternalNoteRepository;
import com.bakaroo.bingo.backend.feature.support.repository.SupportMessageRepository;
import com.bakaroo.bingo.backend.feature.support.repository.SupportTicketRepository;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.DepositClaimRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportServiceTest {

    @Mock
    SupportTicketRepository supportTicketRepository;

    @Mock
    SupportMessageRepository supportMessageRepository;

    @Mock
    InternalNoteRepository internalNoteRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    DepositClaimRepository depositClaimRepository;

    @Mock
    WalletRepository walletRepository;

    ObjectMapper objectMapper = new ObjectMapper();

    SupportService service;

    User player;
    User otherPlayer;
    User admin;
    SupportTicket ticket;

    @BeforeEach
    void setUp() {
        service = new SupportService(supportTicketRepository, supportMessageRepository, internalNoteRepository,
                userRepository, depositClaimRepository, walletRepository, objectMapper);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        player = User.create("251911000001", "Player One", "pw", AccountStatus.ACTIVE, Set.of(UserRole.PLAYER), now);
        otherPlayer = User.create("251911000002", "Player Two", "pw", AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER), now);
        admin = User.create("251911000003", "Admin", "pw", AccountStatus.ACTIVE, Set.of(UserRole.ADMIN), now);
        ReflectionTestUtils.setField(player, "id", 1L);
        ReflectionTestUtils.setField(otherPlayer, "id", 2L);
        ReflectionTestUtils.setField(admin, "id", 3L);

        ticket = SupportTicket.create(player, "Wallet stuck", "Need help", SupportTicketCategory.WALLET,
                SupportTicketPriority.HIGH, null, null, null, UUID.randomUUID().toString());
        ReflectionTestUtils.setField(ticket, "id", 11L);
    }

    @Test
    void requiresResolutionSummaryWhenClosing() {
        when(supportTicketRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(ticket));

        assertThrows(BadRequestException.class,
                () -> service.updateStatus(11L, new BingoUserPrincipal(admin), new SupportTicketStatusUpdateRequest(
                        SupportTicketStatus.CLOSED, null)));
    }

    @Test
    void hidesInternalNotesFromRequesterResponses() {
        SupportMessage message = SupportMessage.create(ticket, player, "Initial message", "op-1");
        InternalNote note = InternalNote.create(ticket, admin, "Internal note", "op-2");
        ReflectionTestUtils.setField(message, "id", 21L);
        ReflectionTestUtils.setField(note, "id", 22L);
        ReflectionTestUtils.setField(message, "createdAt", Instant.parse("2026-01-01T00:01:00Z"));
        ReflectionTestUtils.setField(note, "createdAt", Instant.parse("2026-01-01T00:02:00Z"));

        when(supportTicketRepository.findById(11L)).thenReturn(Optional.of(ticket));
        when(supportMessageRepository.findByTicketIdOrderByCreatedAtAsc(11L)).thenReturn(List.of(message));

        SupportTicketResponse response = service.getMyTicket(new BingoUserPrincipal(player), 11L);

        assertEquals(1, response.messages().size());
        assertEquals(0, response.internalNotes().size());
    }

    @Test
    void rejectsRepliesFromNonOwners() {
        when(supportTicketRepository.findById(11L)).thenReturn(Optional.of(ticket));

        assertThrows(BadRequestException.class,
                () -> service.addMessage(new BingoUserPrincipal(otherPlayer), 11L,
                        new SupportTicketMessageRequest("help")));
    }

    @Test
    void createsTicketsAndListsThemForTheRequester() {
                SupportMessage initialMessage = SupportMessage.create(ticket, player, "Need help", "op-1");
                ReflectionTestUtils.setField(initialMessage, "id", 23L);
                when(supportTicketRepository.save(any())).thenAnswer(invocation -> {
                        SupportTicket saved = invocation.getArgument(0);
                        ReflectionTestUtils.setField(saved, "id", 12L);
                        return saved;
                });
                when(supportMessageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
                when(supportMessageRepository.findByTicketIdOrderByCreatedAtAsc(12L)).thenReturn(List.of(initialMessage));
        when(supportTicketRepository.findByRequesterIdOrderByCreatedAtDesc(player.getId(), PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(ticket)));

        SupportTicketResponse response = service.createTicket(new BingoUserPrincipal(player),
                new SupportTicketCreateRequest("Wallet issue", "Need help", SupportTicketCategory.WALLET,
                        SupportTicketPriority.HIGH, null, null, null));

        assertEquals("Wallet issue", response.subject());
        assertEquals(1, response.messages().size());
        assertEquals(1, service.getMyTickets(new BingoUserPrincipal(player), PageRequest.of(0, 20)).content().size());
    }

    @Test
    void supportStaffCanAssignReplyAndCloseTickets() {
        User support = User.create("251911000004", "Support", "pw", AccountStatus.ACTIVE, Set.of(UserRole.SUPPORT),
                Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(support, "id", 4L);

        when(supportTicketRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(4L)).thenReturn(Optional.of(support));
        when(supportTicketRepository.findById(11L)).thenReturn(Optional.of(ticket));
        SupportMessage firstReply = SupportMessage.create(ticket, player, "Initial", "op-3");
        ReflectionTestUtils.setField(firstReply, "id", 31L);
        SupportMessage secondReply = SupportMessage.create(ticket, support, "Working on it", "op-4");
        ReflectionTestUtils.setField(secondReply, "id", 32L);
        InternalNote note = InternalNote.create(ticket, support, "Internal only", "op-5");
        ReflectionTestUtils.setField(note, "id", 33L);

        when(supportMessageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(supportMessageRepository.findByTicketIdOrderByCreatedAtAsc(11L)).thenReturn(List.of(firstReply, secondReply));
        when(internalNoteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(internalNoteRepository.findByTicketIdOrderByCreatedAtAsc(11L)).thenReturn(List.of(note));

        SupportTicketResponse assigned = service.assignTicket(11L, new BingoUserPrincipal(support),
                new SupportTicketAssignmentRequest(4L));
        assertEquals(SupportTicketStatus.IN_PROGRESS, assigned.status());

        SupportTicketResponse replied = service.addAdminReply(11L, new BingoUserPrincipal(support),
                new SupportTicketMessageRequest("Working on it"));
        assertEquals(2, replied.messages().size());

        SupportTicketResponse noted = service.addInternalNote(11L, new BingoUserPrincipal(support),
                new SupportTicketMessageRequest("Internal only"));
        assertEquals(1, noted.internalNotes().size());

        SupportTicketResponse closed = service.updateStatus(11L, new BingoUserPrincipal(support),
                new SupportTicketStatusUpdateRequest(SupportTicketStatus.CLOSED, "Fixed"));
        assertEquals(SupportTicketStatus.CLOSED, closed.status());
    }

    @Test
    void rejectsAssignmentToPlayerAccounts() {
        when(supportTicketRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(player));

        assertThrows(BadRequestException.class,
                () -> service.assignTicket(11L, new BingoUserPrincipal(admin),
                        new SupportTicketAssignmentRequest(1L)));
    }
}
