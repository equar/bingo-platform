package com.bakaroo.bingo.backend.feature.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bakaroo.bingo.backend.MySqlIntegrationTest;
import com.bakaroo.bingo.backend.feature.agent.dto.AgentPayoutVerificationRequest;
import com.bakaroo.bingo.backend.feature.agent.dto.AgentTicketSaleRequest;
import com.bakaroo.bingo.backend.feature.agent.dto.CommissionRuleRequest;
import com.bakaroo.bingo.backend.feature.agent.dto.PayoutVerificationResponse;
import com.bakaroo.bingo.backend.feature.agent.dto.AgentLoginRequest;
import com.bakaroo.bingo.backend.feature.agent.entity.Agent;
import com.bakaroo.bingo.backend.feature.agent.entity.AgentFloat;
import com.bakaroo.bingo.backend.feature.agent.entity.AgentStatus;
import com.bakaroo.bingo.backend.feature.agent.entity.CommissionAccrual;
import com.bakaroo.bingo.backend.feature.agent.repository.AgentFloatRepository;
import com.bakaroo.bingo.backend.feature.agent.repository.AgentRepository;
import com.bakaroo.bingo.backend.feature.agent.repository.CommissionAccrualRepository;
import com.bakaroo.bingo.backend.feature.auth.dto.AuthResponse;
import com.bakaroo.bingo.backend.feature.auth.dto.AdminLoginRequest;
import com.bakaroo.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.bakaroo.bingo.backend.feature.game.dto.TicketPurchaseResponse;
import com.bakaroo.bingo.backend.feature.game.entity.BingoPatternType;
import com.bakaroo.bingo.backend.feature.game.entity.BingoRoom;
import com.bakaroo.bingo.backend.feature.game.entity.GameSession;
import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;
import com.bakaroo.bingo.backend.feature.game.entity.PrizeEntitlement;
import com.bakaroo.bingo.backend.feature.game.entity.PrizeEntitlementStatus;
import com.bakaroo.bingo.backend.feature.game.entity.PrizeSettlementPath;
import com.bakaroo.bingo.backend.feature.game.entity.Ticket;
import com.bakaroo.bingo.backend.feature.game.entity.WinClaim;
import com.bakaroo.bingo.backend.feature.game.entity.WinClaimStatus;
import com.bakaroo.bingo.backend.feature.game.repository.BingoRoomRepository;
import com.bakaroo.bingo.backend.feature.game.repository.GameSessionRepository;
import com.bakaroo.bingo.backend.feature.game.repository.PrizeEntitlementRepository;
import com.bakaroo.bingo.backend.feature.game.repository.TicketRepository;
import com.bakaroo.bingo.backend.feature.game.repository.WinClaimRepository;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AgentTicketSaleHttpIntegrationTest extends MySqlIntegrationTest {

    private static final String AGENT_PASSWORD = "AgentPass123!";
    private static final String ADMIN_PASSWORD = "AdminPass123!";
    private static final String AGENT_PIN = "2468";

    @LocalServerPort
    int port;

    private final RestTemplate http = new RestTemplate();

    @Autowired
    UserRepository userRepository;

    @Autowired
    AgentRepository agentRepository;

    @Autowired
    AgentFloatRepository agentFloatRepository;

    @Autowired
    CommissionAccrualRepository commissionAccrualRepository;

    @Autowired
    BingoRoomRepository bingoRoomRepository;

    @Autowired
    GameSessionRepository gameSessionRepository;

    @Autowired
    TicketRepository ticketRepository;

    @Autowired
    WinClaimRepository winClaimRepository;

    @Autowired
    PrizeEntitlementRepository prizeEntitlementRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    @Qualifier("agentPinEncoder")
    PasswordEncoder agentPinEncoder;

    @Autowired
    EthiopianPhoneNormalizer phoneNormalizer;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private User adminUser;
    private User agentUser;
    private User playerUser;
    private Agent agent;
    private GameSession session;
    private String adminEmail;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from commission_accruals");
        jdbcTemplate.update("delete from commission_rules");

        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        adminEmail = "qa-" + id + "@example.test";
        adminUser = User.create(
                phoneNormalizer.normalize(randomPhone()),
                "QA Admin " + id,
                passwordEncoder.encode(ADMIN_PASSWORD),
                AccountStatus.ACTIVE,
                Set.of(UserRole.ADMIN),
                now);
        adminUser.setEmail(adminEmail);
        adminUser = userRepository.save(adminUser);
        agentUser = User.create(
                phoneNormalizer.normalize(randomPhone()),
                "QA Agent " + id,
                passwordEncoder.encode(AGENT_PASSWORD),
                AccountStatus.ACTIVE,
                Set.of(UserRole.AGENT),
                now);
        agentUser.setAgentPinHash(agentPinEncoder.encode(AGENT_PIN));
        agentUser = userRepository.save(agentUser);
        agent = agentRepository.save(Agent.create(
                agentUser,
                AgentStatus.APPROVED,
                "Addis Ababa",
                "Central",
                "NID",
                "QA-" + id,
                null,
                null));
        agentFloatRepository.save(AgentFloat.create(agent, "ETB", new BigDecimal("14.50"), BigDecimal.ZERO));
        playerUser = userRepository.save(User.create(
                phoneNormalizer.normalize(randomPhone()),
                "QA Player " + id,
                passwordEncoder.encode("PlayerPass123!"),
                AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER),
                now));
        session = createOpenSession("QA Ticket Sale " + id);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from commission_accruals");
        jdbcTemplate.update("delete from commission_rules");
    }

    @Test
    void ticketSaleAndPayoutEndpointsAreIdempotentAndAccrueOnlyEligibleCommission() {
        AuthResponse adminAuth = login(adminUser);
        AuthResponse agentAuth = login(agentUser);

        AgentTicketSaleRequest saleRequest = new AgentTicketSaleRequest(
                session.getId(),
                playerUser.getNormalizedPhone(),
                AGENT_PIN);

        ResponseEntity<String> anonymousSale;
        try {
            anonymousSale = http.exchange(
                    url("/api/v1/agent/tickets"),
                    HttpMethod.POST,
                    new HttpEntity<>(saleRequest, headers(null, "anonymous-sale")),
                    String.class);
        } catch (HttpStatusCodeException exception) {
            anonymousSale = ResponseEntity.status(exception.getStatusCode()).build();
        }
        assertEquals(HttpStatus.UNAUTHORIZED, anonymousSale.getStatusCode());

        ResponseEntity<CommissionRuleRequest> activeRule = http.postForEntity(
                url("/api/v1/admin/agents/commission-rules"),
                new HttpEntity<>(
                        new CommissionRuleRequest(250, Instant.now().minusSeconds(5), true),
                        headers(adminAuth.accessToken(), null)),
                CommissionRuleRequest.class);
        assertEquals(HttpStatus.OK, activeRule.getStatusCode());

        ResponseEntity<TicketPurchaseResponse> firstResponse = sellTicket(
                agentAuth.accessToken(),
                saleRequest,
                "qa-sale-once");
        ResponseEntity<TicketPurchaseResponse> duplicateResponse = sellTicket(
                agentAuth.accessToken(),
                saleRequest,
                "qa-sale-once");

        assertEquals(HttpStatus.CREATED, firstResponse.getStatusCode());
        assertEquals(HttpStatus.CREATED, duplicateResponse.getStatusCode());
        assertNotNull(firstResponse.getBody());
        assertNotNull(duplicateResponse.getBody());
        assertEquals(firstResponse.getBody().ticket().id(), duplicateResponse.getBody().ticket().id());
        assertEquals(1L, ticketCount());
        assertEquals(new BigDecimal("10.25"), floatState().getAvailableBalance());

        List<CommissionAccrual> accruals = commissionAccrualRepository.findAll();
        assertEquals(1, accruals.size());
        assertEquals(new BigDecimal("4.25"), accruals.get(0).getSaleAmount());
        assertEquals(new BigDecimal("0.11"), accruals.get(0).getCommissionAmount());
        assertEquals("ACCRUED", accruals.get(0).getStatus().name());

        ResponseEntity<CommissionRuleRequest> disabledRule = http.postForEntity(
                url("/api/v1/admin/agents/commission-rules"),
                new HttpEntity<>(
                        new CommissionRuleRequest(250, Instant.now().minusSeconds(1), false),
                        headers(adminAuth.accessToken(), null)),
                CommissionRuleRequest.class);
        assertEquals(HttpStatus.OK, disabledRule.getStatusCode());

        ResponseEntity<TicketPurchaseResponse> secondSale = sellTicket(
                agentAuth.accessToken(),
                saleRequest,
                "qa-sale-after-rule-disabled");
        assertEquals(HttpStatus.CREATED, secondSale.getStatusCode());
        assertEquals(new BigDecimal("6.00"), floatState().getAvailableBalance());
        assertEquals(1, commissionAccrualCount());
        assertEquals(2L, ticketCount());

        Ticket paidTicket = ticketRepository.findById(firstResponse.getBody().ticket().id()).orElseThrow();
        PrizeEntitlement entitlement = createAgentCashEntitlement(paidTicket, new BigDecimal("5.00"));
        AgentPayoutVerificationRequest payoutRequest = new AgentPayoutVerificationRequest(
                entitlement.getPayoutCode(),
                AGENT_PIN,
                "QA-CLEARING-REFERENCE");

        ResponseEntity<PayoutVerificationResponse> payout = http.exchange(
                url("/api/v1/agent/payouts/verify"),
                HttpMethod.POST,
                new HttpEntity<>(payoutRequest, headers(agentAuth.accessToken(), "qa-payout-once")),
                PayoutVerificationResponse.class);
        ResponseEntity<PayoutVerificationResponse> payoutReplay = http.exchange(
                url("/api/v1/agent/payouts/verify"),
                HttpMethod.POST,
                new HttpEntity<>(payoutRequest, headers(agentAuth.accessToken(), "qa-payout-once")),
                PayoutVerificationResponse.class);

        assertEquals(HttpStatus.OK, payout.getStatusCode());
        assertEquals(HttpStatus.OK, payoutReplay.getStatusCode());
        assertNotNull(payout.getBody());
        assertNotNull(payoutReplay.getBody());
        assertEquals(payout.getBody().recordId(), payoutReplay.getBody().recordId());
        assertEquals(new BigDecimal("1.00"), floatState().getAvailableBalance());
        assertEquals(new BigDecimal("5.00"), floatState().getClearingBalance());

        ResponseEntity<TicketPurchaseResponse> insufficientSale = sellTicket(
                agentAuth.accessToken(),
                saleRequest,
                "qa-sale-insufficient-float");
        assertTrue(insufficientSale.getStatusCode().is4xxClientError());
        assertEquals(2L, ticketCount());
        assertEquals(1, commissionAccrualCount());
        assertEquals(new BigDecimal("1.00"), floatState().getAvailableBalance());
    }

    @Test
    void concurrentDuplicateSaleReturnsTheSameResultWithoutADuplicateDebit() throws Exception {
        AuthResponse adminAuth = login(adminUser);
        AuthResponse agentAuth = login(agentUser);
        ResponseEntity<CommissionRuleRequest> rule = http.postForEntity(
                url("/api/v1/admin/agents/commission-rules"),
                new HttpEntity<>(
                        new CommissionRuleRequest(250, Instant.now().minusSeconds(5), true),
                        headers(adminAuth.accessToken(), null)),
                CommissionRuleRequest.class);
        assertEquals(HttpStatus.OK, rule.getStatusCode());

        AgentTicketSaleRequest request = new AgentTicketSaleRequest(
                session.getId(),
                playerUser.getNormalizedPhone(),
                AGENT_PIN);
        CompletableFuture<ResponseEntity<TicketPurchaseResponse>> first = CompletableFuture.supplyAsync(
                () -> sellTicket(agentAuth.accessToken(), request, "qa-sale-concurrent"));
        CompletableFuture<ResponseEntity<TicketPurchaseResponse>> duplicate = CompletableFuture.supplyAsync(
                () -> sellTicket(agentAuth.accessToken(), request, "qa-sale-concurrent"));
        ResponseEntity<TicketPurchaseResponse> firstResponse = first.get(30, TimeUnit.SECONDS);
        ResponseEntity<TicketPurchaseResponse> duplicateResponse = duplicate.get(30, TimeUnit.SECONDS);

        assertEquals(HttpStatus.CREATED, firstResponse.getStatusCode());
        assertEquals(HttpStatus.CREATED, duplicateResponse.getStatusCode());
        assertEquals(firstResponse.getBody().ticket().id(), duplicateResponse.getBody().ticket().id());
        assertEquals(1L, ticketCount());
        assertEquals(new BigDecimal("10.25"), floatState().getAvailableBalance());
        assertEquals(1, commissionAccrualCount());
    }

    private AuthResponse login(User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String loginUrl = user == adminUser
                ? "/api/v1/admin/login"
                : "/api/v1/agent/login";
        Object request = user == adminUser
                ? new AdminLoginRequest(adminEmail, ADMIN_PASSWORD)
                : new AgentLoginRequest(user.getNormalizedPhone(), AGENT_PIN);
        ResponseEntity<AuthResponse> response = http.postForEntity(
                url(loginUrl),
                new HttpEntity<>(request, headers),
                AuthResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody();
    }

    private ResponseEntity<TicketPurchaseResponse> sellTicket(
            String token,
            AgentTicketSaleRequest request,
            String idempotencyKey) {
        try {
            return http.exchange(
                    url("/api/v1/agent/tickets"),
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers(token, idempotencyKey)),
                    TicketPurchaseResponse.class);
        } catch (HttpStatusCodeException exception) {
            return ResponseEntity.status(exception.getStatusCode()).build();
        }
    }

    private HttpHeaders headers(String token, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private AgentFloat floatState() {
        return agentFloatRepository.findByAgentId(agent.getId()).orElseThrow();
    }

    private long ticketCount() {
        return jdbcTemplate.queryForObject(
                "select count(*) from bingo_tickets where session_id = ?",
                Long.class,
                session.getId());
    }

    private int commissionAccrualCount() {
        return commissionAccrualRepository.findByAgentIdOrderByCreatedAtDesc(agent.getId()).size();
    }

    private GameSession createOpenSession(String name) {
        BingoRoom room = new BingoRoom();
        room.setName(name);
        room.setDescription("Disposable HTTP integration fixture");
        room.setTicketPrice(new BigDecimal("4.25"));
        room.setCurrency("ETB");
        room.setTimezone("Africa/Addis_Ababa");
        room.setScheduleRule("0 0/30 * * * *");
        room.setSessionCapacity(10);
        room.setMaxTicketsPerPlayer(10);
        room.setTicketSalesCloseOnSessionStart(true);
        room.setAllowMultipleWinnersPerPrize(true);
        room.setActive(true);
        room.setConfiguredPatternsJson("[\"LINE\"]");
        room.setConfiguredPrizesJson("[]");
        room = bingoRoomRepository.save(room);

        GameSession gameSession = new GameSession();
        gameSession.setRoom(room);
        gameSession.setSessionName(name + " Session");
        gameSession.setState(GameSessionState.OPEN);
        gameSession.setScheduledStartAt(Instant.now().plusSeconds(3600));
        gameSession.setTicketPriceSnapshot(new BigDecimal("4.25"));
        gameSession.setCurrencySnapshot("ETB");
        gameSession.setSessionCapacitySnapshot(10);
        gameSession.setMaxTicketsPerPlayerSnapshot(10);
        gameSession.setTicketSalesCloseOnSessionStart(true);
        gameSession.setAllowMultipleWinnersPerPrize(true);
        gameSession.setPatternsSnapshotJson("[\"LINE\"]");
        gameSession.setPrizesSnapshotJson("[]");
        return gameSessionRepository.save(gameSession);
    }

    private PrizeEntitlement createAgentCashEntitlement(Ticket ticket, BigDecimal amount) {
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
        entitlement.setPrizeAmount(amount);
        entitlement.setCurrency("ETB");
        entitlement.setOperationId(UUID.randomUUID().toString());
        entitlement.setPayoutCode("QA-" + UUID.randomUUID());
        return prizeEntitlementRepository.save(entitlement);
    }

    private String randomPhone() {
        return String.format(Locale.ROOT, "09%08d", new SecureRandom().nextInt(100_000_000));
    }
}
