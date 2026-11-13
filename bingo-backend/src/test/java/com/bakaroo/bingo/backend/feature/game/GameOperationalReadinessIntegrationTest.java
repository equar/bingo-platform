package com.bakaroo.bingo.backend.feature.game;

import com.bakaroo.bingo.backend.MySqlIntegrationTest;
import com.bakaroo.bingo.backend.common.exception.*;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.game.dto.*;
import com.bakaroo.bingo.backend.feature.game.entity.*;
import com.bakaroo.bingo.backend.feature.game.repository.*;
import com.bakaroo.bingo.backend.feature.game.service.*;
import com.bakaroo.bingo.backend.feature.user.entity.*;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.bakaroo.bingo.backend.feature.wallet.entity.*;
import com.bakaroo.bingo.backend.feature.wallet.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.game.policy.payouts-enabled=true")
@ActiveProfiles("test")
@Transactional
class GameOperationalReadinessIntegrationTest extends MySqlIntegrationTest {
    @Autowired BingoGameService games;
    @Autowired com.bakaroo.bingo.backend.feature.game.websocket.GameLiveDrawService liveDraws;
    @Autowired GameWinnerService winners;
    @Autowired UserRepository users;
    @Autowired WalletRepository wallets;
    @Autowired WalletTransactionRepository transactions;
    @Autowired TicketRepository tickets;
    @Autowired GameSessionRepository sessions;
    @Autowired WinClaimRepository claims;
    @Autowired PrizeEntitlementRepository prizes;
    @Autowired DrawEventRepository draws;
    @Autowired ObjectMapper json;
    @Autowired com.bakaroo.bingo.backend.feature.agent.service.AgentService agentService;
    @Autowired com.bakaroo.bingo.backend.feature.agent.repository.AgentRepository agents;
    @Autowired com.bakaroo.bingo.backend.feature.agent.repository.AgentFloatRepository floats;
    @Autowired com.bakaroo.bingo.backend.feature.agent.repository.CommissionRuleRepository commissionRules;
    @Autowired com.bakaroo.bingo.backend.feature.agent.repository.CommissionAccrualRepository commissions;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("agentPinEncoder")
    org.springframework.security.crypto.password.PasswordEncoder pinEncoder;
    private User admin;
    private User one;
    private User two;
    private Long room;
    private Long session;

    @BeforeEach void setup() {
        admin = user(UserRole.ADMIN);
        one = user(UserRole.PLAYER);
        two = user(UserRole.PLAYER);
        room = games.createRoom(new BingoRoomRequest("Test " + UUID.randomUUID(), "Fixture", new BigDecimal("100.00"),
                "ETB", "Africa/Addis_Ababa", "0 0 * * * *", 10, 10, true, true, true,
                List.of(BingoPatternType.LINE), List.of(new BingoPrizeConfigRequest(BingoPatternType.LINE,
                new BigDecimal("10.01"), PrizeSettlementPath.PLAYER_WALLET)), null)).id();
        session = games.createSession(room, request(null, null)).id();
    }

    private User user(UserRole role) {
        String phone = "2519" + String.format("%08d", new java.security.SecureRandom().nextInt(100000000));
        User u = users.save(User.create(phone, "Readiness " + UUID.randomUUID(), "unused", AccountStatus.ACTIVE, Set.of(role), Instant.now()));
        if (role == UserRole.PLAYER) {
            Wallet w = Wallet.create(u, "ETB"); w.setAvailableBalance(new BigDecimal("1000.00")); wallets.save(w);
        }
        return u;
    }
    private GameSessionRequest request(List<BingoPatternType> patterns, List<BingoPrizeConfigRequest> config) {
        return new GameSessionRequest("Readiness", Instant.now().plusSeconds(3600), GameSessionState.OPEN,
                null, null, null, null, true, true, patterns, config);
    }
    private Long buy(User player) { return games.purchaseTicket(session, new BingoUserPrincipal(player), UUID.randomUUID().toString()).body().ticket().id(); }
    private void state(GameSessionState state) { games.updateSessionState(session, new GameSessionStateUpdateRequest(state)); }
    private void calls(int through) {
        for (int i = 1; i <= through; i++) {
            DrawEvent event = new DrawEvent(); event.setSession(sessions.findById(session).orElseThrow());
            event.setSequenceNo(i); event.setNumberDrawn(i); event.setDrawnAt(Instant.now());
            event.setOperationId(UUID.randomUUID().toString()); draws.save(event);
        }
        draws.flush();
    }
    private WinClaimResponse claim(Long ticket, User player) {
        return games.submitWinClaim(ticket, new BingoUserPrincipal(player), new WinClaimRequest(BingoPatternType.LINE), UUID.randomUUID().toString()).body();
    }
    private void approve(Long claim) { games.reviewWinClaim(claim, new BingoUserPrincipal(admin), new WinClaimReviewRequest("Verified"), true); }

    @Test void automaticLineOnlyGameStopsAtFirstWinningCallAndAllowsClosedClaim() throws Exception {
        Long first = buy(one), second = buy(two);
        Ticket a = tickets.findById(first).orElseThrow(), b = tickets.findById(second).orElseThrow();
        a.setGridJson(grid(1)); b.setGridJson(grid(6)); tickets.saveAndFlush(a); tickets.saveAndFlush(b);
        state(GameSessionState.STARTED);
        Instant anchor = Instant.now();
        for (int n = 1; n <= 75; n++) {
            liveDraws.advanceSessionDrawIfDue(session, anchor.plusSeconds(n * 11L));
            if (sessions.findById(session).orElseThrow().getState() == GameSessionState.CLOSED) break;
        }
        GameSession game = sessions.findById(session).orElseThrow();
        assertEquals(GameSessionState.CLOSED, game.getState());
        long count = draws.countBySessionId(session);
        assertTrue(count < 75);
        assertEquals(winners.winners(session,BingoPatternType.LINE).sequence(),count);
        assertTrue(liveDraws.advanceSessionDrawIfDue(session,anchor.plusSeconds(1000)).isEmpty());
        assertEquals(count,draws.countBySessionId(session));
        Long winner = winners.winners(session,BingoPatternType.LINE).ticketIds().getFirst();
        assertTrue(claim(winner,winner.equals(first) ? one : two).serverValidationPassed());
        assertNotNull(game.getEndedAt());
    }

    @Test void claimEligibilityIsEmptyUntilAnEarliestWinAndLosingClaimsAreRejected() throws Exception {
        long before = claims.count();
        Long first = buy(one), later = buy(two);
        Ticket a = tickets.findById(first).orElseThrow(), b = tickets.findById(later).orElseThrow();
        a.setGridJson(grid(1)); b.setGridJson(grid(6)); tickets.saveAndFlush(a); tickets.saveAndFlush(b);
        var page = org.springframework.data.domain.PageRequest.of(0, 10);
        assertTrue(games.getMyTickets(session, new BingoUserPrincipal(one), page).getContent().getFirst().eligibleClaimPatterns().isEmpty());
        state(GameSessionState.STARTED);
        assertTrue(games.getMyTickets(session, new BingoUserPrincipal(one), page).getContent().getFirst().eligibleClaimPatterns().isEmpty());
        assertEquals("CLAIM_DOES_NOT_MATCH_DRAWN_NUMBERS", assertThrows(ConflictException.class, () -> claim(first,one)).getCode());
        assertEquals(before,claims.count());
        calls(5);
        assertEquals(List.of(BingoPatternType.LINE), games.getMyTickets(session, new BingoUserPrincipal(one), page).getContent().getFirst().eligibleClaimPatterns());
        assertTrue(games.getMyTickets(session, new BingoUserPrincipal(two), page).getContent().getFirst().eligibleClaimPatterns().isEmpty());
        assertEquals("CLAIM_DOES_NOT_MATCH_DRAWN_NUMBERS", assertThrows(ConflictException.class, () -> claim(later,two)).getCode());
        assertEquals(before,claims.count());
        assertTrue(claim(first,one).serverValidationPassed());
    }

    @Test void unfundedAndPromotionalOnlyGamesCannotStart() {
        assertEquals("SESSION_UNDERFUNDED", assertThrows(ConflictException.class, () -> state(GameSessionState.STARTED)).getCode());
        Wallet wallet = wallets.findByOwnerId(one.getId()).orElseThrow();
        wallet.setAvailableBalance(BigDecimal.ZERO); wallet.setPromotionalBalance(new BigDecimal("1000.00")); wallets.save(wallet);
        buy(one);
        assertEquals("SESSION_UNDERFUNDED", assertThrows(ConflictException.class, () -> state(GameSessionState.STARTED)).getCode());
    }

    @Test void tiesSplitFixedPoolAndWalletCreditIsExactlyOnce() throws Exception {
        Long first = buy(one), second = buy(two);
        // Identical cards ensure the same earliest-completion call without depending on randomness.
        Ticket a = tickets.findById(first).orElseThrow(), b = tickets.findById(second).orElseThrow();
        b.setGridJson(a.getGridJson()); tickets.saveAndFlush(b);
        state(GameSessionState.STARTED); calls(75);
        var page = org.springframework.data.domain.PageRequest.of(0,10);
        var displayA = games.getMyTickets(session,new BingoUserPrincipal(one),page).getContent().getFirst();
        var displayB = games.getMyTickets(session,new BingoUserPrincipal(two),page).getContent().getFirst();
        assertEquals(new BigDecimal("5.01"),displayA.winningPrizeAmounts().get(BingoPatternType.LINE));
        assertEquals(new BigDecimal("5.00"),displayB.winningPrizeAmounts().get(BingoPatternType.LINE));
        assertEquals("ETB",displayA.prizeCurrency());
        WinClaimResponse ca = claim(first, one), cb = claim(second, two);
        assertTrue(ca.serverValidationPassed()); assertTrue(cb.serverValidationPassed());
        // Review the higher ID first. It must receive the same deterministic share.
        approve(cb.id()); approve(ca.id());
        var ea = prizes.findAll().stream().filter(p -> p.getWinClaim().getId().equals(ca.id())).findFirst().orElseThrow();
        var eb = prizes.findAll().stream().filter(p -> p.getWinClaim().getId().equals(cb.id())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("5.01"), ea.getPrizeAmount()); assertEquals(new BigDecimal("5.00"), eb.getPrizeAmount());
        assertEquals("TICKET_PRIZE_ALREADY_AWARDED", assertThrows(ConflictException.class, () -> claim(first, one)).getCode());
        var request = new PrizeEntitlementSettlementRequest("READY-" + ea.getId(), "Verified wallet credit");
        games.settleEntitlement(ea.getId(), new BingoUserPrincipal(admin), request);
        games.settleEntitlement(ea.getId(), new BingoUserPrincipal(admin), request);
        assertEquals(new BigDecimal("905.01"), wallets.findByOwnerId(one.getId()).orElseThrow().getAvailableBalance());
        assertEquals(1, transactions.findByReferenceTypeAndReferenceIdOrderByCreatedAtAscIdAsc("BINGO_PRIZE", ea.getId()).size());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentSameReferenceSettlementsCreditTheWalletOnce() throws Exception {
        Long ticket = buy(one);
        state(GameSessionState.STARTED);
        calls(75);
        approve(claim(ticket, one).id());
        var entitlement = prizes.findAll().getFirst();
        BigDecimal balanceBeforeSettlement = wallets.findByOwnerId(one.getId()).orElseThrow().getAvailableBalance();
        var request = new PrizeEntitlementSettlementRequest("CONCURRENT-" + entitlement.getId(), "Concurrent payout");
        var principal = new BingoUserPrincipal(admin);

        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<PrizeEntitlementResponse> first = workers.submit(() -> settleAttempt(
                    entitlement.getId(), principal, request, ready, start));
            Future<PrizeEntitlementResponse> second = workers.submit(() -> settleAttempt(
                    entitlement.getId(), principal, request, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            var firstResult = first.get(15, TimeUnit.SECONDS);
            var secondResult = second.get(15, TimeUnit.SECONDS);
            assertEquals("SETTLED", firstResult.status().name());
            assertEquals("SETTLED", secondResult.status().name());
            assertEquals(request.settlementReference(), firstResult.settlementReference());
            assertEquals(request.settlementReference(), secondResult.settlementReference());
        } finally {
            workers.shutdownNow();
        }

        assertEquals(balanceBeforeSettlement.add(entitlement.getPrizeAmount()),
                wallets.findByOwnerId(one.getId()).orElseThrow().getAvailableBalance());
        assertEquals(1, transactions.findByReferenceTypeAndReferenceIdOrderByCreatedAtAscIdAsc(
                "BINGO_PRIZE", entitlement.getId()).size());
    }

    private PrizeEntitlementResponse settleAttempt(Long entitlementId, BingoUserPrincipal principal,
            PrizeEntitlementSettlementRequest request, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to start concurrent prize settlements");
        }
        return games.settleEntitlement(entitlementId, principal, request);
    }

    @Test void settledSessionsCannotAcceptClaimsAndEndTimeIsPreserved() {
        Long ticket = buy(one); state(GameSessionState.STARTED); calls(75); state(GameSessionState.CLOSED);
        Instant ended = Instant.now().minusSeconds(86401);
        GameSession game = sessions.findById(session).orElseThrow(); game.setEndedAt(ended); sessions.saveAndFlush(game);
        state(GameSessionState.SETTLED);
        assertEquals(ended, sessions.findById(session).orElseThrow().getEndedAt());
        assertEquals("SESSION_NOT_CLAIMABLE", assertThrows(ConflictException.class, () -> claim(ticket, one)).getCode());
    }

    @Test void pendingClaimsBlockFinalSettlementAndClosedClaimsExpire() {
        Long ticket = buy(one); state(GameSessionState.STARTED); calls(75); claim(ticket, one); state(GameSessionState.CLOSED);
        assertEquals("CLAIM_WINDOW_OPEN", assertThrows(ConflictException.class, () -> state(GameSessionState.SETTLED)).getCode());
        GameSession game = sessions.findById(session).orElseThrow(); game.setEndedAt(Instant.now().minusSeconds(86401)); sessions.saveAndFlush(game);
        assertEquals("SESSION_HAS_UNRESOLVED_OBLIGATIONS", assertThrows(ConflictException.class, () -> state(GameSessionState.SETTLED)).getCode());
        assertEquals("CLAIM_WINDOW_EXPIRED", assertThrows(ConflictException.class, () -> claim(ticket, one)).getCode());
    }

    @Test void cancellationRefundsOriginalBucketsAndCannotBeRepeated() {
        Wallet w = wallets.findByOwnerId(one.getId()).orElseThrow(); w.setPromotionalBalance(new BigDecimal("25.00")); wallets.save(w);
        buy(one); state(GameSessionState.CANCELLED);
        Wallet after = wallets.findByOwnerId(one.getId()).orElseThrow();
        assertEquals(new BigDecimal("1000.00"), after.getAvailableBalance()); assertEquals(new BigDecimal("25.00"), after.getPromotionalBalance());
        assertThrows(ConflictException.class, () -> state(GameSessionState.CANCELLED));
    }

    @Test void sessionOverridesMustMatchPatternsAndLateSalesAreRejected() {
        assertThrows(BadRequestException.class, () -> games.createSession(room, request(List.of(), List.of())));
        assertThrows(BadRequestException.class, () -> games.createSession(room, request(List.of(BingoPatternType.FULL_HOUSE), null)));
        buy(one); state(GameSessionState.STARTED);
        assertThrows(ConflictException.class, () -> buy(two));
    }
    private String grid(int offset) throws Exception {
        List<List<Integer>> rows = new ArrayList<>();
        for (int row = 0; row < 5; row++) {
            List<Integer> values = new ArrayList<>();
            for (int column = 0; column < 5; column++) values.add(row == 2 && column == 2 ? 0 : offset + row + 15 * column);
            rows.add(values);
        }
        return json.writeValueAsString(rows);
    }

    @Test void laterCompletionCannotClaimAnEarlierPatternsPool() throws Exception {
        Long first = buy(one), later = buy(two);
        Ticket a = tickets.findById(first).orElseThrow(), b = tickets.findById(later).orElseThrow();
        a.setGridJson(grid(1)); b.setGridJson(grid(6)); tickets.saveAndFlush(a); tickets.saveAndFlush(b);
        state(GameSessionState.STARTED); calls(75);
        assertTrue(claim(first, one).serverValidationPassed());
        long before = claims.count();
        assertEquals("NOT_EARLIEST_WINNER", assertThrows(ConflictException.class, () -> claim(later, two)).getCode());
        assertEquals(before, claims.count());
    }

    @Test void aNewDrawDoesNotPermitASecondAwardForTheSameTicketAndPattern() throws Exception {
        Long ticket = buy(one); Ticket card = tickets.findById(ticket).orElseThrow(); card.setGridJson(grid(1)); tickets.saveAndFlush(card);
        state(GameSessionState.STARTED); calls(5); approve(claim(ticket, one).id());
        DrawEvent next = new DrawEvent(); next.setSession(sessions.findById(session).orElseThrow()); next.setSequenceNo(6);
        next.setNumberDrawn(6); next.setOperationId(UUID.randomUUID().toString()); next.setDrawnAt(Instant.now()); draws.saveAndFlush(next);
        assertEquals("TICKET_PRIZE_ALREADY_AWARDED", assertThrows(ConflictException.class, () -> claim(ticket, one)).getCode());
    }

    @Test void configuredMarginAndCostReserveAreEnforcedAndSnapshotted() {
        buy(one);
        GameSession game = sessions.findById(session).orElseThrow();
        assertEquals(1000, game.getMinimumHouseMarginBps());
        game.setMinimumHouseMarginBps(9000); game.setSessionCostReserve(new BigDecimal("0.01")); sessions.saveAndFlush(game);
        assertEquals("SESSION_UNDERFUNDED", assertThrows(ConflictException.class, () -> state(GameSessionState.STARTED)).getCode());
        assertEquals(new BigDecimal("90.00"), games.getSession(session).funding().requiredHouseRetention());
    }

    private User sellingAgent() {
        User agent = user(UserRole.AGENT); agent.setAgentPinHash(pinEncoder.encode("2468")); users.saveAndFlush(agent);
        var profile = agents.save(com.bakaroo.bingo.backend.feature.agent.entity.Agent.create(agent,
                com.bakaroo.bingo.backend.feature.agent.entity.AgentStatus.APPROVED, "Addis Ababa", "Test", "NID", UUID.randomUUID().toString(), null, null));
        floats.save(com.bakaroo.bingo.backend.feature.agent.entity.AgentFloat.create(profile, "ETB", new BigDecimal("1000.00"), BigDecimal.ZERO));
        return agent;
    }

    @Test void agentCashUsesTheSameWinnerFundingAndExactlyOnceControls() {
        session = games.createSession(room, request(null, List.of(new BingoPrizeConfigRequest(BingoPatternType.LINE,
                new BigDecimal("10.01"), PrizeSettlementPath.AGENT_CASH)))).id();
        User agent = sellingAgent(); Long ticket = buy(one); state(GameSessionState.STARTED); calls(75); approve(claim(ticket, one).id());
        var entitlement = prizes.findAll().stream().filter(p -> p.getSession().getId().equals(session)).findFirst().orElseThrow();
        assertEquals(entitlement.getPayoutCode(), games.getMyWinClaims(new BingoUserPrincipal(one), org.springframework.data.domain.Pageable.unpaged()).getContent().getFirst().payoutCode());
        assertNull(games.getAdminWinClaim(entitlement.getWinClaim().getId()).payoutCode());
        assertTrue(games.getMyWinClaims(new BingoUserPrincipal(two), org.springframework.data.domain.Pageable.unpaged()).isEmpty());
        var request = new com.bakaroo.bingo.backend.feature.agent.dto.AgentPayoutVerificationRequest(entitlement.getPayoutCode(), "2468", "CASH-" + entitlement.getId());
        String key = UUID.randomUUID().toString();
        var first = agentService.verifyAndDisbursePayout(new BingoUserPrincipal(agent), request, key);
        var replay = agentService.verifyAndDisbursePayout(new BingoUserPrincipal(agent), request, key);
        assertEquals(first.body().recordId(), replay.body().recordId());
        var balance = floats.findByAgentId(agents.findByUserId(agent.getId()).orElseThrow().getId()).orElseThrow();
        assertEquals(new BigDecimal("989.99"), balance.getAvailableBalance()); assertEquals(new BigDecimal("10.01"), balance.getClearingBalance());
        assertEquals(agent.getId(), prizes.findById(entitlement.getId()).orElseThrow().getSettledBy().getId());
        assertNull(games.getMyWinClaims(new BingoUserPrincipal(one), org.springframework.data.domain.Pageable.unpaged()).getContent().getFirst().payoutCode());
    }

    @Test void agentSalesCountCashAndCommissionAndCancellationRefundsThePlayer() {
        User agent = sellingAgent();
        int version = commissionRules.findAll().stream().mapToInt(com.bakaroo.bingo.backend.feature.agent.entity.CommissionRule::getVersionNumber).max().orElse(0) + 1;
        commissionRules.save(com.bakaroo.bingo.backend.feature.agent.entity.CommissionRule.create(version, 500, true, Instant.now().minusSeconds(60)));
        var purchase = agentService.sellTicket(new BingoUserPrincipal(agent),
                new com.bakaroo.bingo.backend.feature.agent.dto.AgentTicketSaleRequest(session, one.getNormalizedPhone(), "2468"), UUID.randomUUID().toString());
        var budget = games.getSession(session).funding();
        assertEquals(new BigDecimal("100.00"), budget.cashBackedRevenue()); assertEquals(new BigDecimal("5.00"), budget.commissionLiability());
        state(GameSessionState.CANCELLED);
        assertEquals(new BigDecimal("1100.00"), wallets.findByOwnerId(one.getId()).orElseThrow().getAvailableBalance());
        assertEquals(com.bakaroo.bingo.backend.feature.agent.entity.CommissionAccrualStatus.VOID,
                commissions.findByTicketId(purchase.body().ticket().id()).orElseThrow().getStatus());
    }

    @Test void ticketPurchaseCannotDebitAWalletInAnotherCurrency() {
        Long otherRoom = games.createRoom(new BingoRoomRequest("Currency " + UUID.randomUUID(), "Fixture", new BigDecimal("100.00"),
                "USD", "Africa/Addis_Ababa", "0 0 * * * *", 10, 10, true, true, true,
                List.of(BingoPatternType.LINE), List.of(new BingoPrizeConfigRequest(BingoPatternType.LINE,
                new BigDecimal("10.00"), PrizeSettlementPath.PLAYER_WALLET)), null)).id();
        session = games.createSession(otherRoom, request(null, null)).id();
        assertEquals("CURRENCY_MISMATCH", assertThrows(ConflictException.class, () -> buy(one)).getCode());
        assertEquals(new BigDecimal("1000.00"), wallets.findByOwnerId(one.getId()).orElseThrow().getAvailableBalance());
        assertEquals(0, tickets.countBySessionId(session));
    }

}
