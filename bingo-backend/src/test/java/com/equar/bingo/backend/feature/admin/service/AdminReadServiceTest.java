package com.equar.bingo.backend.feature.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.equar.bingo.backend.feature.agent.entity.Agent;
import com.equar.bingo.backend.feature.agent.entity.AgentStatus;
import com.equar.bingo.backend.feature.agent.entity.AgentFloat;
import com.equar.bingo.backend.feature.agent.entity.FloatTopUpRequest;
import com.equar.bingo.backend.feature.agent.entity.FloatTopUpRequestStatus;
import com.equar.bingo.backend.feature.agent.entity.PayoutVerificationRecord;
import com.equar.bingo.backend.feature.agent.repository.AgentFloatRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentFloatTransactionRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentRepository;
import com.equar.bingo.backend.feature.agent.repository.FloatTopUpRequestRepository;
import com.equar.bingo.backend.feature.agent.repository.PayoutVerificationRecordRepository;
import com.equar.bingo.backend.feature.audit.entity.AuditLogEntry;
import com.equar.bingo.backend.feature.audit.repository.AuditLogRepository;
import com.equar.bingo.backend.feature.config.BusinessProperties;
import com.equar.bingo.backend.feature.game.entity.BingoPatternType;
import com.equar.bingo.backend.feature.game.entity.BingoRoom;
import com.equar.bingo.backend.feature.game.entity.GameSession;
import com.equar.bingo.backend.feature.game.entity.GameSessionState;
import com.equar.bingo.backend.feature.game.entity.PrizeEntitlement;
import com.equar.bingo.backend.feature.game.entity.PrizeEntitlementStatus;
import com.equar.bingo.backend.feature.game.entity.PrizeSettlementPath;
import com.equar.bingo.backend.feature.game.entity.Ticket;
import com.equar.bingo.backend.feature.game.entity.WinClaim;
import com.equar.bingo.backend.feature.game.entity.WinClaimStatus;
import com.equar.bingo.backend.feature.game.repository.DrawEventRepository;
import com.equar.bingo.backend.feature.game.repository.GameSessionRepository;
import com.equar.bingo.backend.feature.game.repository.PrizeEntitlementRepository;
import com.equar.bingo.backend.feature.game.repository.TicketRepository;
import com.equar.bingo.backend.feature.game.repository.WinClaimRepository;
import com.equar.bingo.backend.feature.support.entity.SupportTicket;
import com.equar.bingo.backend.feature.support.entity.SupportTicketCategory;
import com.equar.bingo.backend.feature.support.entity.SupportTicketPriority;
import com.equar.bingo.backend.feature.support.entity.SupportTicketStatus;
import com.equar.bingo.backend.feature.support.repository.SupportTicketRepository;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.wallet.entity.DepositClaim;
import com.equar.bingo.backend.feature.wallet.entity.DepositClaimStatus;
import com.equar.bingo.backend.feature.wallet.entity.Wallet;
import com.equar.bingo.backend.feature.wallet.repository.DepositClaimRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletRepository;
import java.math.BigDecimal;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminReadServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    WalletRepository walletRepository;
    @Mock
    DepositClaimRepository depositClaimRepository;
    @Mock
    TicketRepository ticketRepository;
    @Mock
    DrawEventRepository drawEventRepository;
    @Mock
    GameSessionRepository gameSessionRepository;
    @Mock
    AgentRepository agentRepository;
    @Mock
    AgentFloatRepository agentFloatRepository;
    @Mock
    AgentFloatTransactionRepository agentFloatTransactionRepository;
    @Mock
    FloatTopUpRequestRepository floatTopUpRequestRepository;
    @Mock
    PayoutVerificationRecordRepository payoutVerificationRecordRepository;
    @Mock
    WinClaimRepository winClaimRepository;
    @Mock
    PrizeEntitlementRepository prizeEntitlementRepository;
    @Mock
    SupportTicketRepository supportTicketRepository;
    @Mock
    AuditLogRepository auditLogRepository;

    BusinessProperties businessProperties = new BusinessProperties("Africa/Addis_Ababa");

    AdminReadService service;

    User player;
    User adminUser;
    Agent agent;
    Wallet wallet;
    AgentFloat agentFloat;
    GameSession session;
    SupportTicket supportTicket;

    @BeforeEach
    void setUp() {
        service = new AdminReadService(userRepository, walletRepository, depositClaimRepository, ticketRepository,
                drawEventRepository, gameSessionRepository, agentRepository, agentFloatRepository,
                agentFloatTransactionRepository, floatTopUpRequestRepository, payoutVerificationRecordRepository,
                winClaimRepository, prizeEntitlementRepository, supportTicketRepository, auditLogRepository,
                businessProperties);

        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        player = User.create("251911000001", "Player One", "pw", AccountStatus.ACTIVE, Set.of(UserRole.PLAYER), now);
        ReflectionTestUtils.setField(player, "id", 11L);
        adminUser = User.create("251911000002", "Admin One", "pw", AccountStatus.ACTIVE, Set.of(UserRole.ADMIN), now);
        ReflectionTestUtils.setField(adminUser, "id", 12L);

        agent = Agent.create(adminUser, AgentStatus.APPROVED, "Addis", "Central", "NID", "A-1", null, null);
        ReflectionTestUtils.setField(agent, "id", 21L);

        wallet = Wallet.create(player, "ETB");
        ReflectionTestUtils.setField(wallet, "id", 31L);
        wallet.setAvailableBalance(new BigDecimal("50.00"));

        agentFloat = AgentFloat.create(agent, "ETB", new BigDecimal("70.00"), new BigDecimal("10.00"));
        ReflectionTestUtils.setField(agentFloat, "id", 41L);

        BingoRoom room = new BingoRoom();
        ReflectionTestUtils.setField(room, "id", 51L);
        room.setName("Main Room");
        room.setTicketPrice(new BigDecimal("5.00"));
        room.setCurrency("ETB");
        room.setTimezone("Africa/Addis_Ababa");
        session = new GameSession();
        ReflectionTestUtils.setField(session, "id", 61L);
        session.setRoom(room);
        session.setSessionName("Morning Session");
        session.setState(GameSessionState.STARTED);
        session.setScheduledStartAt(now.plusSeconds(3600));
        session.setStartedAt(now.plusSeconds(3600));
        session.setTicketPriceSnapshot(new BigDecimal("5.00"));
        session.setCurrencySnapshot("ETB");

        supportTicket = SupportTicket.create(player, "Need help", "Body", SupportTicketCategory.WALLET,
                SupportTicketPriority.HIGH, null, null, null, UUID.randomUUID().toString());
        ReflectionTestUtils.setField(supportTicket, "id", 71L);
        supportTicket.setStatus(SupportTicketStatus.OPEN);
        supportTicket.setAssignedTo(adminUser);
    }

    @Test
    void dashboardMetricsAndPlayerLookupsAreProjected() {
        when(walletRepository.findAll()).thenReturn(List.of(wallet));
        when(agentFloatRepository.findAll()).thenReturn(List.of(agentFloat));
        when(userRepository.findAll()).thenReturn(List.of(player, adminUser));
        when(agentRepository.countByStatus(AgentStatus.APPROVED)).thenReturn(1L);
        when(supportTicketRepository.findAll()).thenReturn(List.of(supportTicket));
        when(depositClaimRepository.findByStatus(DepositClaimStatus.PENDING, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of()));
        when(floatTopUpRequestRepository.findByStatusOrderByCreatedAtDesc(FloatTopUpRequestStatus.PENDING))
                .thenReturn(List.of());
        when(winClaimRepository.findAll()).thenReturn(List.of());
        when(gameSessionRepository.findByStateInAndStartedAtIsNotNullAndEndedAtIsNullOrderByScheduledStartAtAsc(
                List.of(GameSessionState.STARTED, GameSessionState.DRAWING))).thenReturn(List.of(session));

        var metrics = service.dashboardMetrics();
        assertThat(metrics.businessTimezone()).isEqualTo("Africa/Addis_Ababa");
        assertThat(metrics.activePlayers()).isEqualTo(1L);
        assertThat(metrics.activeAgents()).isEqualTo(1L);
        assertThat(metrics.openSupportTickets()).isEqualTo(1L);
        assertThat(metrics.playerWalletBalanceTotal()).isEqualTo(new BigDecimal("50.00"));

        Pageable playersPage = PageRequest.of(0, 20);
        when(userRepository.findAdminUsers(eq(List.of(UserRole.PLAYER)), isNull(), isNull(), eq(playersPage)))
                .thenReturn(new PageImpl<>(List.of(player)));
        when(userRepository.findAdminUsers(eq(List.of(UserRole.PLAYER)), eq("player"), isNull(), eq(playersPage)))
                .thenReturn(new PageImpl<>(List.of(player)));
        when(userRepository.findAdminUsers(eq(List.of(UserRole.PLAYER)), isNull(), eq(AccountStatus.ACTIVE),
                eq(playersPage)))
                .thenReturn(new PageImpl<>(List.of(player)));
        when(walletRepository.countByOwnerId(player.getId())).thenReturn(1L);
        when(depositClaimRepository.findByPlayerId(player.getId(), Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of()));
        when(supportTicketRepository.findByRequesterIdOrderByCreatedAtDesc(player.getId(), Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(supportTicket)));
        assertThat(service.searchPlayers(null, null, playersPage)).hasSize(1);
        assertThat(service.searchPlayers(" Player ", null, playersPage)).hasSize(1);
        assertThat(service.searchPlayers(null, AccountStatus.ACTIVE, playersPage)).hasSize(1);

        when(userRepository.findById(anyLong())).thenReturn(Optional.of(player));
        when(ticketRepository.countByOwnerId(player.getId())).thenReturn(2L);
        when(winClaimRepository.findAll()).thenReturn(List.of(makeWinClaim(player)));
        assertThat(service.playerDetail(player.getId()).ticketCount()).isEqualTo(2L);
    }

    @Test
    void sessionAgentAuditAndReviewQueueViewsAreProjected() {
        when(gameSessionRepository.searchAdminSessions(isNull(), isNull(), isNull(), isNull(), isNull(),
                any(Instant.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(session)));
        when(ticketRepository.countBySessionId(session.getId())).thenReturn(1L);
        when(drawEventRepository.countBySessionId(session.getId())).thenReturn(2L);
        when(winClaimRepository.findBySessionIdOrderByCreatedAtDesc(session.getId()))
                .thenReturn(List.of(makeWinClaim(player)));
        when(gameSessionRepository.findByStateInAndStartedAtIsNotNullAndEndedAtIsNullOrderByScheduledStartAtAsc(
                List.of(GameSessionState.STARTED, GameSessionState.DRAWING))).thenReturn(List.of(session));
        when(supportTicketRepository.findAll()).thenReturn(List.of(supportTicket));

        assertThat(service.sessions(null, null, null, null, PageRequest.of(0, 20))).hasSize(1);
        assertThat(service.liveSessions()).hasSize(1);

        when(agentRepository.findAll(PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(agent)));
        when(agentRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
        when(ticketRepository.findAll()).thenReturn(List.of(makeTicket(player, session)));
        when(agentFloatTransactionRepository.findByAgentFloatAgentIdOrderByCreatedAtDesc(agent.getId()))
                .thenReturn(List.of());
        when(floatTopUpRequestRepository.findByAgentIdOrderByCreatedAtDesc(agent.getId())).thenReturn(List.of());
        when(payoutVerificationRecordRepository.findByAgentIdOrderByCreatedAtDesc(agent.getId())).thenReturn(List.of());
        assertThat(service.agents(PageRequest.of(0, 20))).hasSize(1);
        assertThat(service.agentDetail(agent.getId()).agentId()).isEqualTo(agent.getId());

        AuditLogEntry entry = AuditLogEntry.create(adminUser, "LOGIN", "USER", player.getId(), "reason",
                UUID.randomUUID().toString(), "{}");
        ReflectionTestUtils.setField(entry, "id", 81L);
        when(auditLogRepository.findAll(PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(entry)));
        assertThat(service.auditLogs(PageRequest.of(0, 20))).hasSize(1);
        when(auditLogRepository.findById(81L)).thenReturn(Optional.of(entry));
        assertThat(service.auditLogDetail(81L))
                .satisfies(detail -> {
                    assertThat(detail.actorUserId()).isEqualTo(adminUser.getId());
                    assertThat(detail.actorDisplayName()).isEqualTo(adminUser.getDisplayName());
                    assertThat(detail.metadataJson()).isEqualTo("{}");
                });

        when(supportTicketRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(supportTicket)));
        assertThat(service.reviewQueue(PageRequest.of(0, 20))).hasSize(1);
    }

    private WinClaim makeWinClaim(User claimant) {
        Ticket ticket = new Ticket();
        ReflectionTestUtils.setField(ticket, "id", 91L);
        ticket.setOwner(claimant);
        ticket.setSession(session);

        WinClaim claim = new WinClaim();
        ReflectionTestUtils.setField(claim, "id", 92L);
        claim.setTicket(ticket);
        claim.setSession(session);
        claim.setClaimant(claimant);
        claim.setClaimedPattern(BingoPatternType.LINE);
        claim.setStatus(WinClaimStatus.PENDING_REVIEW);
        claim.setDrawSequenceSnapshot(3);
        claim.setOperationId(UUID.randomUUID().toString());
        return claim;
    }

    private Ticket makeTicket(User owner, GameSession owningSession) {
        Ticket ticket = new Ticket();
        ReflectionTestUtils.setField(ticket, "id", 101L);
        ticket.setOwner(owner);
        ticket.setSession(owningSession);
        ticket.setPublicTicketCode("T-101");
        return ticket;
    }
}
