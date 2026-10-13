package com.equar.bingo.backend.feature.agent;

import com.equar.bingo.backend.common.exception.BadRequestException;
import com.equar.bingo.backend.feature.agent.dto.AgentFloatTransferRequest;
import com.equar.bingo.backend.feature.agent.dto.AgentPayoutVerificationRequest;
import com.equar.bingo.backend.feature.agent.entity.Agent;
import com.equar.bingo.backend.feature.agent.entity.AgentFloat;
import com.equar.bingo.backend.feature.agent.entity.AgentStatus;
import com.equar.bingo.backend.feature.agent.dto.AgentProfileResponse;
import com.equar.bingo.backend.feature.agent.entity.PayoutVerificationStatus;
import com.equar.bingo.backend.feature.agent.entity.CommissionRule;
import com.equar.bingo.backend.feature.agent.entity.FloatTopUpRequestStatus;
import com.equar.bingo.backend.feature.agent.repository.AgentFloatRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentFloatTransactionRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentRepository;
import com.equar.bingo.backend.feature.agent.repository.CommissionAccrualRepository;
import com.equar.bingo.backend.feature.agent.repository.FloatTopUpRequestRepository;
import com.equar.bingo.backend.feature.agent.repository.PayoutVerificationRecordRepository;
import com.equar.bingo.backend.feature.agent.service.AgentService;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
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
import com.equar.bingo.backend.feature.game.repository.BingoRoomRepository;
import com.equar.bingo.backend.feature.game.repository.GameSessionRepository;
import com.equar.bingo.backend.feature.game.repository.PrizeEntitlementRepository;
import com.equar.bingo.backend.feature.game.repository.TicketRepository;
import com.equar.bingo.backend.feature.game.repository.WinClaimRepository;
import com.equar.bingo.backend.feature.support.repository.InternalNoteRepository;
import com.equar.bingo.backend.feature.support.repository.SupportMessageRepository;
import com.equar.bingo.backend.feature.support.repository.SupportTicketRepository;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.wallet.entity.IdempotencyRecord;
import com.equar.bingo.backend.feature.wallet.repository.DepositClaimRepository;
import com.equar.bingo.backend.feature.wallet.repository.IdempotencyRecordRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AgentServiceIntegrationTest {

    @Autowired
    AgentService agentService;

    @Autowired
    AgentRepository agentRepository;

    @Autowired
    AgentFloatRepository agentFloatRepository;

    @Autowired
    AgentFloatTransactionRepository agentFloatTransactionRepository;

    @Autowired
    FloatTopUpRequestRepository floatTopUpRequestRepository;

    @Autowired
    CommissionAccrualRepository commissionAccrualRepository;

    @Autowired
    PayoutVerificationRecordRepository payoutVerificationRecordRepository;

    @Autowired
    PrizeEntitlementRepository prizeEntitlementRepository;

    @Autowired
    WinClaimRepository winClaimRepository;

    @Autowired
    TicketRepository ticketRepository;

    @Autowired
    GameSessionRepository gameSessionRepository;

    @Autowired
    BingoRoomRepository bingoRoomRepository;

    @Autowired
    WalletRepository walletRepository;

    @Autowired
    WalletTransactionRepository walletTransactionRepository;

    @Autowired
    DepositClaimRepository depositClaimRepository;

    @Autowired
    IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EthiopianPhoneNormalizer phoneNormalizer;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    @Qualifier("agentPinEncoder")
    PasswordEncoder agentPinEncoder;

        @Autowired
        SupportTicketRepository supportTicketRepository;

        @Autowired
        SupportMessageRepository supportMessageRepository;

        @Autowired
        InternalNoteRepository internalNoteRepository;

    private User agentUser;
    private User playerUser;
    private BingoUserPrincipal agentPrincipal;

    @BeforeEach
    void setUp() {
                internalNoteRepository.deleteAllInBatch();
                supportMessageRepository.deleteAllInBatch();
                supportTicketRepository.deleteAllInBatch();
        payoutVerificationRecordRepository.deleteAll();
        commissionAccrualRepository.deleteAll();
        floatTopUpRequestRepository.deleteAll();
        agentFloatTransactionRepository.deleteAll();
        prizeEntitlementRepository.deleteAll();
        winClaimRepository.deleteAll();
        ticketRepository.deleteAll();
        gameSessionRepository.deleteAll();
        bingoRoomRepository.deleteAll();
        agentFloatRepository.deleteAll();
        agentRepository.deleteAll();
        walletTransactionRepository.deleteAll();
        depositClaimRepository.deleteAll();
        walletRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        userRepository.deleteAll();

        Instant now = Instant.now();
        agentUser = userRepository.save(User.create(phoneNormalizer.normalize("0911000301"), "Agent User",
                passwordEncoder.encode("AgentPass123!"), AccountStatus.ACTIVE, Set.of(UserRole.AGENT), now));
        agentUser.setAgentPinHash(agentPinEncoder.encode("2468"));
        agentUser = userRepository.save(agentUser);
        Agent agent = agentRepository.save(Agent.create(agentUser, AgentStatus.APPROVED, "Addis Ababa",
                "Central", "NID", "AGENT-001", null, null));
        agentFloatRepository.save(AgentFloat.create(agent, "ETB", new BigDecimal("100.00"), BigDecimal.ZERO));
        agentPrincipal = new BingoUserPrincipal(agentUser);

        playerUser = userRepository.save(User.create(phoneNormalizer.normalize("0911000302"), "Player User",
                passwordEncoder.encode("PlayerPass123!"), AccountStatus.ACTIVE, Set.of(UserRole.PLAYER), now));
    }

    @Test
    void transferRejectsNonPositiveAmountsWithoutChangingBalances() {
        AgentFloat before = agentFloatRepository
                .findByAgentId(agentRepository.findByUserId(agentUser.getId()).orElseThrow()
                        .getId())
                .orElseThrow();

        assertThrows(BadRequestException.class, () -> agentService.transferFloat(agentPrincipal,
                new AgentFloatTransferRequest(playerUser.getNormalizedPhone(), new BigDecimal("0.00"), "ETB",
                        "2468", "invalid"),
                "transfer-invalid"));

        AgentFloat after = agentFloatRepository
                .findByAgentId(agentRepository.findByUserId(agentUser.getId()).orElseThrow()
                        .getId())
                .orElseThrow();
        assertEquals(before.getAvailableBalance(), after.getAvailableBalance());
        assertEquals(before.getClearingBalance(), after.getClearingBalance());
        assertEquals(0L, agentFloatTransactionRepository.count());
        assertEquals(0L, idempotencyRecordRepository.count());
    }

    @Test
    void payoutVerificationMovesBalanceToClearingExactlyOnce() {
        PrizeEntitlement entitlement = createAgentCashEntitlement();

        var first = agentService.verifyAndDisbursePayout(agentPrincipal,
                new AgentPayoutVerificationRequest(entitlement.getPayoutCode(), "2468", "CLR-1"),
                "payout-key");
        assertEquals(PayoutVerificationStatus.DISBURSED, first.body().status());
        assertEquals(new BigDecimal("12.34"), first.body().disbursedAmount());

        AgentFloat floatState = agentFloatRepository.findByAgentId(
                agentRepository.findByUserId(agentUser.getId()).orElseThrow().getId()).orElseThrow();
        assertEquals(new BigDecimal("87.66"), floatState.getAvailableBalance());
        assertEquals(new BigDecimal("12.34"), floatState.getClearingBalance());
        assertEquals(1L, payoutVerificationRecordRepository.count());
        assertEquals(1L, agentFloatTransactionRepository.count());
        assertEquals(1L, idempotencyRecordRepository.count());

        var replay = agentService.verifyAndDisbursePayout(agentPrincipal,
                new AgentPayoutVerificationRequest(entitlement.getPayoutCode(), "2468", "CLR-1"),
                "payout-key");
        assertEquals(first.body().recordId(), replay.body().recordId());
        assertEquals(new BigDecimal("87.66"), agentFloatRepository.findByAgentId(
                agentRepository.findByUserId(agentUser.getId()).orElseThrow().getId()).orElseThrow()
                .getAvailableBalance());
        assertEquals(new BigDecimal("12.34"), agentFloatRepository.findByAgentId(
                agentRepository.findByUserId(agentUser.getId()).orElseThrow().getId()).orElseThrow()
                .getClearingBalance());
        assertEquals(1L, payoutVerificationRecordRepository.count());
        assertEquals(1L, agentFloatTransactionRepository.count());
        assertEquals(1L, idempotencyRecordRepository.count());
    }

    @Test
    void dashboardLookupRegisterAndFloatReadPathsWork() {
        var dashboard = agentService.dashboard(agentPrincipal);
                assertEquals(agentRepository.findByUserId(agentUser.getId()).orElseThrow().getId(), dashboard.agentId());
        assertEquals("APPROVED", dashboard.status());
        assertEquals(new BigDecimal("100.00"), dashboard.availableFloat());
        assertEquals(new BigDecimal("0.00"), dashboard.clearingFloat());

        assertEquals(playerUser.getId(), agentService.lookupPlayer(playerUser.getNormalizedPhone()).id());

        var registered = agentService.registerPlayer(new com.equar.bingo.backend.feature.agent.dto.AgentPlayerRegistrationRequest(
                "0911000399", "Registered Player"));
        assertEquals("Registered Player", registered.displayName());
        assertEquals(Set.of(UserRole.PLAYER), registered.roles());

        assertEquals(new BigDecimal("100.00"), agentService.getFloat(agentPrincipal).availableBalance());
        assertTrue(agentService.getFloatHistory(agentPrincipal).isEmpty());
    }

    @Test
    void transferFloatCreditsThePlayerWalletAndCreatesHistory() {
        var result = agentService.transferFloat(agentPrincipal,
                new AgentFloatTransferRequest("0911000302", new BigDecimal("10.00"), "ETB", "2468",
                        "float transfer"),
                "transfer-key");

        assertEquals(new BigDecimal("90.00"), result.body().availableBalance());
        assertEquals(1L, agentFloatTransactionRepository.count());
        assertEquals(1L, walletTransactionRepository.count());
        assertEquals(new BigDecimal("10.00"), walletRepository.findByOwnerId(playerUser.getId()).orElseThrow()
                .getAvailableBalance());
        assertEquals(1, agentService.getFloatHistory(agentPrincipal).size());
    }

    @Test
    void topUpReviewAndCommissionRulePathsWork() {
        var topUp = agentService.submitTopUpRequest(agentPrincipal,
                new com.equar.bingo.backend.feature.agent.dto.SubmitFloatTopUpRequest(new BigDecimal("15.00"),
                        "etb", "need liquidity"),
                "topup-key");
        assertEquals(FloatTopUpRequestStatus.PENDING, topUp.body().status());

        User admin = userRepository.save(User.create(phoneNormalizer.normalize("0911000401"), "Admin User",
                passwordEncoder.encode("AdminPass123!"), AccountStatus.ACTIVE, Set.of(UserRole.ADMIN), Instant.now()));
        BingoUserPrincipal adminPrincipal = new BingoUserPrincipal(admin);

        var reviewed = agentService.reviewTopUpRequest(topUp.body().id(), adminPrincipal,
                new com.equar.bingo.backend.feature.agent.dto.ReviewFloatTopUpRequest(true, "approved"));
        assertEquals(FloatTopUpRequestStatus.APPROVED, reviewed.status());
        assertEquals(new BigDecimal("115.00"), agentService.getFloat(agentPrincipal).availableBalance());

        agentService.upsertCommissionRule(adminPrincipal,
                new com.equar.bingo.backend.feature.agent.dto.CommissionRuleRequest(250,
                        Instant.now().plusSeconds(3600), true));
        assertTrue(agentService.getCommissionHistory(agentPrincipal).isEmpty());
    }

    @Test
    void adminApproveAndSuspendAgentManageAgentState() {
        User pendingUser = userRepository.save(User.create(phoneNormalizer.normalize("0911000501"), "Pending Agent",
                passwordEncoder.encode("AgentPass123!"), AccountStatus.PENDING, Set.of(UserRole.AGENT), Instant.now()));

        var approved = agentService.adminApproveAgent(pendingUser.getId(), new AgentProfileResponse(null, null, null,
                "Pending Agent", AgentStatus.APPROVED, "City", "Territory", "NID", "N-001", null, null,
                null, null));
        assertEquals(AgentStatus.APPROVED, approved.status());

        var suspended = agentService.adminSuspendAgent(approved.agentId());
        assertEquals(AgentStatus.SUSPENDED, suspended.status());
    }

    private PrizeEntitlement createAgentCashEntitlement() {
        BingoRoom room = new BingoRoom();
        room.setName("Agent Cash Room");
        room.setDescription("Fixture room");
        room.setTicketPrice(new BigDecimal("4.00"));
        room.setCurrency("ETB");
        room.setTimezone("Africa/Addis_Ababa");
        room.setScheduleRule("0 0/30 * * * *");
        room.setSessionCapacity(1);
        room.setMaxTicketsPerPlayer(1);
        room.setTicketSalesCloseOnSessionStart(true);
        room.setAllowMultipleWinnersPerPrize(true);
        room.setActive(true);
        room.setConfiguredPatternsJson("[\"LINE\"]");
        room.setConfiguredPrizesJson("[]");
        room = bingoRoomRepository.save(room);

        GameSession session = new GameSession();
        session.setRoom(room);
        session.setSessionName("Cash Session");
        session.setState(GameSessionState.OPEN);
        session.setScheduledStartAt(Instant.now().plusSeconds(3600));
        session.setTicketPriceSnapshot(new BigDecimal("4.00"));
        session.setCurrencySnapshot("ETB");
        session.setSessionCapacitySnapshot(1);
        session.setMaxTicketsPerPlayerSnapshot(1);
        session.setTicketSalesCloseOnSessionStart(true);
        session.setAllowMultipleWinnersPerPrize(true);
        session.setPatternsSnapshotJson("[\"LINE\"]");
        session.setPrizesSnapshotJson("[]");
        session = gameSessionRepository.save(session);

        Ticket ticket = new Ticket();
        ticket.setSession(session);
        ticket.setOwner(playerUser);
        ticket.setPublicTicketCode("TICKET-001");
        ticket.setOperationId(UUID.randomUUID().toString());
        ticket.setGridJson("[[1,2,3,4,5]]");
        ticket = ticketRepository.save(ticket);

        WinClaim winClaim = new WinClaim();
        winClaim.setTicket(ticket);
        winClaim.setSession(session);
        winClaim.setClaimant(playerUser);
        winClaim.setClaimedPattern(BingoPatternType.LINE);
        winClaim.setStatus(WinClaimStatus.APPROVED);
        winClaim.setServerValidationPassed(true);
        winClaim.setServerValidationCode("OK");
        winClaim.setServerValidationMessage("OK");
        winClaim.setDrawSequenceSnapshot(5);
        winClaim.setCalledNumbersSnapshotJson("[1,2,3,4,5]");
        winClaim.setEvidenceJson("{}");
        winClaim.setOperationId(UUID.randomUUID().toString());
        winClaim = winClaimRepository.save(winClaim);

        PrizeEntitlement entitlement = new PrizeEntitlement();
        entitlement.setWinClaim(winClaim);
        entitlement.setSession(session);
        entitlement.setTicket(ticket);
        entitlement.setClaimant(playerUser);
        entitlement.setPrizeType(BingoPatternType.LINE);
        entitlement.setSettlementPath(PrizeSettlementPath.AGENT_CASH);
        entitlement.setStatus(PrizeEntitlementStatus.PENDING);
        entitlement.setPrizeAmount(new BigDecimal("12.34"));
        entitlement.setCurrency("ETB");
        entitlement.setOperationId(UUID.randomUUID().toString());
        entitlement.setPayoutCode("PAYOUT-001");
        return prizeEntitlementRepository.save(entitlement);
    }
}