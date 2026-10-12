package com.equar.bingo.backend.feature.support.service;

import com.equar.bingo.backend.common.exception.BadRequestException;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.support.dto.SupportTicketMessageRequest;
import com.equar.bingo.backend.feature.support.dto.SupportTicketResponse;
import com.equar.bingo.backend.feature.support.dto.SupportTicketStatusUpdateRequest;
import com.equar.bingo.backend.feature.support.entity.InternalNote;
import com.equar.bingo.backend.feature.support.entity.SupportMessage;
import com.equar.bingo.backend.feature.support.entity.SupportTicket;
import com.equar.bingo.backend.feature.support.entity.SupportTicketCategory;
import com.equar.bingo.backend.feature.support.entity.SupportTicketPriority;
import com.equar.bingo.backend.feature.support.entity.SupportTicketStatus;
import com.equar.bingo.backend.feature.support.repository.InternalNoteRepository;
import com.equar.bingo.backend.feature.support.repository.SupportMessageRepository;
import com.equar.bingo.backend.feature.support.repository.SupportTicketRepository;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.wallet.repository.DepositClaimRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletRepository;
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
        when(internalNoteRepository.findByTicketIdOrderByCreatedAtAsc(11L)).thenReturn(List.of(note));

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
}