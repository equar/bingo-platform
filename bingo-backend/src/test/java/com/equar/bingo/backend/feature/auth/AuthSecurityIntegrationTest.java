package com.equar.bingo.backend.feature.auth;

import com.equar.bingo.backend.MySqlIntegrationTest;
import com.equar.bingo.backend.feature.auth.dto.LoginRequest;
import com.equar.bingo.backend.feature.auth.dto.RegisterRequest;
import com.equar.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.equar.bingo.backend.feature.auth.service.JwtService;
import com.equar.bingo.backend.feature.game.repository.BingoRoomRepository;
import com.equar.bingo.backend.feature.game.repository.DrawEventRepository;
import com.equar.bingo.backend.feature.game.repository.GameSessionRepository;
import com.equar.bingo.backend.feature.game.repository.PrizeEntitlementRepository;
import com.equar.bingo.backend.feature.game.repository.TicketRepository;
import com.equar.bingo.backend.feature.game.repository.WinClaimRepository;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentFloatRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentFloatTransactionRepository;
import com.equar.bingo.backend.feature.agent.repository.PayoutVerificationRecordRepository;
import com.equar.bingo.backend.feature.agent.entity.Agent;
import com.equar.bingo.backend.feature.agent.entity.AgentFloat;
import com.equar.bingo.backend.feature.agent.entity.AgentStatus;
import com.equar.bingo.backend.feature.agent.dto.CommissionRuleRequest;
import com.equar.bingo.backend.feature.agent.dto.AgentApplicationRequest;
import com.equar.bingo.backend.feature.agent.dto.AgentLoginRequest;
import com.equar.bingo.backend.feature.agent.repository.CommissionRuleRepository;
import com.equar.bingo.backend.feature.support.repository.InternalNoteRepository;
import com.equar.bingo.backend.feature.support.repository.SupportMessageRepository;
import com.equar.bingo.backend.feature.support.repository.SupportTicketRepository;
import com.equar.bingo.backend.feature.wallet.repository.DepositClaimRepository;
import com.equar.bingo.backend.feature.wallet.repository.IdempotencyRecordRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import javax.crypto.SecretKey;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
                "app.security.authentication.account-attempt-limit=3",
                "app.security.authentication.network-attempt-limit=100",
                "app.security.authentication.window=PT5S",
                "app.security.authentication.lockout=PT1S"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityIntegrationTest extends MySqlIntegrationTest {

        @Autowired
        MockMvc mockMvc;

        @Autowired
        ObjectMapper objectMapper;

        @Autowired
        UserRepository userRepository;

        @Autowired
        PasswordEncoder passwordEncoder;

        @Autowired
        EthiopianPhoneNormalizer phoneNormalizer;

        @Autowired
        JwtService jwtService;

        @Value("${app.security.jwt.secret}")
        String jwtSecret;

        @Autowired
        WalletTransactionRepository walletTransactionRepository;

        @Autowired
        DepositClaimRepository depositClaimRepository;

        @Autowired
        WalletRepository walletRepository;

        @Autowired
        IdempotencyRecordRepository idempotencyRecordRepository;
        @Autowired
        AgentFloatRepository agentFloatRepository;
        @Autowired
        AgentRepository agentRepository;

        @Autowired
        CommissionRuleRepository commissionRuleRepository;

        @Autowired
        SupportTicketRepository supportTicketRepository;

        @Autowired
        SupportMessageRepository supportMessageRepository;

        @Autowired
        InternalNoteRepository internalNoteRepository;

        @Autowired
        AgentFloatTransactionRepository agentFloatTransactionRepository;

        @Autowired
        PayoutVerificationRecordRepository payoutVerificationRecordRepository;

        @Autowired
        PrizeEntitlementRepository prizeEntitlementRepository;

        @Autowired
        WinClaimRepository winClaimRepository;

        @Autowired
        TicketRepository ticketRepository;

        @Autowired
        DrawEventRepository drawEventRepository;

        @Autowired
        GameSessionRepository gameSessionRepository;

        @Autowired
        BingoRoomRepository bingoRoomRepository;

        @Autowired
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

        @Autowired
        com.equar.bingo.backend.feature.auth.service.AuthenticationAttemptThrottleService throttleService;

        @BeforeEach
        void setUp() {
                jdbcTemplate.update("delete from authentication_attempt_buckets");
                jdbcTemplate.update("delete from audit_log_entries");
                jdbcTemplate.update("delete from float_accounting_journal_lines");
                jdbcTemplate.update("delete from float_accounting_journals");
                jdbcTemplate.update("delete from float_top_up_verification_attempts");
                jdbcTemplate.update("delete from float_bank_statement_entries");
                jdbcTemplate.update("delete from agent_float_top_up_requests");
                jdbcTemplate.update("delete from float_receiving_bank_accounts");
                internalNoteRepository.deleteAllInBatch();
                supportMessageRepository.deleteAllInBatch();
                supportTicketRepository.deleteAllInBatch();
                walletTransactionRepository.deleteAllInBatch();
                jdbcTemplate.update("delete from signup_reward_grants");
                jdbcTemplate.update("delete from wallet_transfers");
                jdbcTemplate.update("delete from signup_reward_policies");
                jdbcTemplate.update("delete from wallet_transfer_policies");
                depositClaimRepository.deleteAllInBatch();
                walletRepository.deleteAllInBatch();
                idempotencyRecordRepository.deleteAllInBatch();
                payoutVerificationRecordRepository.deleteAllInBatch();
                jdbcTemplate.update("delete from commission_accruals");
                commissionRuleRepository.deleteAllInBatch();
                agentFloatTransactionRepository.deleteAllInBatch();
                agentFloatRepository.deleteAllInBatch();
                agentRepository.deleteAllInBatch();
                prizeEntitlementRepository.deleteAllInBatch();
                winClaimRepository.deleteAllInBatch();
                drawEventRepository.deleteAllInBatch();
                ticketRepository.deleteAllInBatch();
                gameSessionRepository.deleteAllInBatch();
                bingoRoomRepository.deleteAllInBatch();
                userRepository.deleteAllInBatch();

                Instant now = Instant.now();
                User admin = User.create(
                                phoneNormalizer.normalize("0911000001"),
                                "Admin User",
                                passwordEncoder.encode("123456"),
                                AccountStatus.ACTIVE,
                                Set.of(UserRole.ADMIN),
                                now);
                admin.setEmail("qa-admin@example.test");
                userRepository.save(admin);

                userRepository.save(User.create(
                                phoneNormalizer.normalize("0911000002"),
                                "Agent User",
                                passwordEncoder.encode("AgentPass123!"),
                                AccountStatus.ACTIVE,
                                Set.of(UserRole.AGENT),
                                now));

                userRepository.save(User.create(
                                phoneNormalizer.normalize("0911000004"),
                                "Player User",
                                passwordEncoder.encode("123456"),
                                AccountStatus.ACTIVE,
                                Set.of(UserRole.PLAYER),
                                now));
        }

        @Test
        void registerLoginAndProtectionWork() throws Exception {
                String registerJson = """
                                {"displayName":"Player One","phoneNumber":"09 11 00 00 03","password":"123456","roles":["ADMIN"],"status":"ACTIVE"}
                                """;

                mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerJson))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.accessToken").exists())
                                .andExpect(jsonPath("$.user.phoneNumber").value("251911000003"))
                                .andExpect(jsonPath("$.user.roles[0]").value("PLAYER"));

                String loginJson = objectMapper.writeValueAsString(new LoginRequest(
                                "0911000003",
                                "123456"));

                String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginJson))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken").exists())
                                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                String playerToken = objectMapper.readTree(loginResponse).get("accessToken").asText();

                mockMvc.perform(get("/api/v1/me")
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.user.phoneNumber").value("251911000003"));

                mockMvc.perform(get("/api/v1/admin/users")
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.correlationId").exists());
        }

        @Test
        void floatRequestSubmissionAndAdminQueueAreRoleProtected() throws Exception {
                String playerToken = issueTokenFor("0911000004");
                String agentToken = issueTokenFor("0911000002");
                String adminToken = issueTokenFor("0911000001");
                User agentUser = userRepository.findByNormalizedPhone(phoneNormalizer.normalize("0911000002"))
                                .orElseThrow();
                Agent agent = agentRepository.save(Agent.create(agentUser, AgentStatus.APPROVED, "QA City",
                                "QA Territory", "TEST", "M02B-FLOAT-READ-001", null, null));
                agentFloatRepository.save(AgentFloat.create(agent, "ETB", new java.math.BigDecimal("17.00"),
                                java.math.BigDecimal.ZERO));
                String submission = """
                                {"amount":12.34,"currency":"ETB","note":"QA-M02B-SECURITY"}
                                """;

                mockMvc.perform(post("/api/v1/agent/float-top-up-requests")
                                .header("Authorization", "Bearer " + playerToken)
                                .header("Idempotency-Key", "m02b-player-top-up-denied")
                                .contentType(MediaType.APPLICATION_JSON).content(submission))
                                .andExpect(status().isForbidden());
                String[] invalidAmounts = { "0", "-1", "100000000000000000.00" };
                for (int index = 0; index < invalidAmounts.length; index++) {
                        mockMvc.perform(post("/api/v1/agent/float-top-up-requests")
                                        .header("Authorization", "Bearer " + agentToken)
                                        .header("Idempotency-Key", "m02b-invalid-amount-" + index)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"amount\":" + invalidAmounts[index]
                                                        + ",\"currency\":\"ETB\",\"note\":\"QA\"}"))
                                        .andExpect(status().isBadRequest());
                }
                mockMvc.perform(get("/api/v1/admin/agents/float-top-up-requests")
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isForbidden());
                mockMvc.perform(get("/api/v1/admin/agents/float-top-up-requests")
                                .header("Authorization", "Bearer " + agentToken))
                                .andExpect(status().isForbidden());
                mockMvc.perform(get("/api/v1/admin/agents/float-top-up-requests")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk());
                mockMvc.perform(get("/api/v1/admin/agents/{agentId}/float", agent.getId())
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isForbidden());
                mockMvc.perform(get("/api/v1/admin/agents/{agentId}/float/history", agent.getId())
                                .header("Authorization", "Bearer " + agentToken))
                                .andExpect(status().isForbidden());
                mockMvc.perform(get("/api/v1/admin/agents/{agentId}/float", agent.getId())
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.availableBalance").value(17.00));
                assertEquals(0, jdbcTemplate.queryForObject("select count(*) from agent_float_top_up_requests",
                                Integer.class));
        }

        @Test
        void adminDashboardCountsOnlyActivePlayersAndAgentReadRoutesLoadLazyProfiles() throws Exception {
                Instant now = Instant.now();
                User pendingPlayer = User.create(phoneNormalizer.normalize("0911000005"), "Pending Player",
                                passwordEncoder.encode("123456"), AccountStatus.PENDING,
                                Set.of(UserRole.PLAYER), now);
                userRepository.save(pendingPlayer);

                User agentUser = userRepository.findByNormalizedPhone(phoneNormalizer.normalize("0911000002"))
                                .orElseThrow();
                Agent agent = agentRepository.save(Agent.create(agentUser, AgentStatus.APPROVED, "QA City",
                                "QA Territory", "TEST", "QA-DOC-02", null, null));

                String adminToken = issueTokenFor("0911000001");
                mockMvc.perform(get("/api/v1/admin/dashboard/metrics")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.activePlayers").value(1));

                mockMvc.perform(get("/api/v1/admin/agents")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(1)))
                                .andExpect(jsonPath("$.content[0].agentId").value(agent.getId()))
                                .andExpect(jsonPath("$.content[0].displayName").value("Agent User"));

                mockMvc.perform(get("/api/v1/admin/agents/{agentId}", agent.getId())
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.agentId").value(agent.getId()))
                                .andExpect(jsonPath("$.displayName").value("Agent User"));
        }

        @Test
        void commissionRulesAreVersionedAndCurrentRuleCanBeReadAfterSaving() throws Exception {
                String adminToken = issueTokenFor("0911000001");
                String inactiveRule = objectMapper.writeValueAsString(new CommissionRuleRequest(0,
                                Instant.parse("2000-01-01T00:00:00Z"), false));

                mockMvc.perform(get("/api/v1/admin/agents/commission-rules/current")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.versionNumber").value(0));

                mockMvc.perform(post("/api/v1/admin/agents/commission-rules")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(inactiveRule))
                                .andExpect(status().isOk());
                mockMvc.perform(get("/api/v1/admin/agents/commission-rules/current")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.versionNumber").value(1))
                                .andExpect(jsonPath("$.commissionRateBps").value(0))
                                .andExpect(jsonPath("$.active").value(false));

                mockMvc.perform(post("/api/v1/admin/agents/commission-rules")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(inactiveRule))
                                .andExpect(status().isOk());
                mockMvc.perform(get("/api/v1/admin/agents/commission-rules/current")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.versionNumber").value(2));
                assertEquals(2, commissionRuleRepository.count());
                mockMvc.perform(get("/api/v1/admin/agents/commission-rules/current")
                                .header("Authorization", "Bearer " + issueTokenFor("0911000004")))
                                .andExpect(status().isForbidden());
        }

        @Test
        void agentApplicationApprovalAndSuspensionEnforceLifecycleAndAudit() throws Exception {
                String phone = "0911000601";
                AgentApplicationRequest application = new AgentApplicationRequest("M02A Agent One", phone,
                                "M02A-AgentPass-001", "860001", "Addis Ababa", "Bole", "NID",
                                "M02A-NID-001", null, null);
                String requestJson = objectMapper.writeValueAsString(application);
                String responseJson = mockMvc.perform(post("/api/v1/agent/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status").value("PENDING"))
                                .andExpect(jsonPath("$.idDocumentType").value("NID"))
                                .andReturn().getResponse().getContentAsString();
                var applicationResult = objectMapper.readTree(responseJson);
                long agentId = applicationResult.get("agentId").asLong();
                long userId = applicationResult.get("userId").asLong();
                User pendingUser = userRepository.findById(userId).orElseThrow();
                assertEquals(AccountStatus.PENDING, pendingUser.getStatus());
                org.junit.jupiter.api.Assertions.assertTrue(pendingUser.hasRole(UserRole.AGENT));
                assertEquals(0, jdbcTemplate.queryForObject("select count(*) from agent_floats where agent_id = ?",
                                Integer.class, agentId));
                assertEquals(0, jdbcTemplate.queryForObject("select count(*) from wallets where owner_id = ?",
                                Integer.class, userId));

                mockMvc.perform(post("/api/v1/agent/register").contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson))
                                .andExpect(status().isConflict());
                AgentApplicationRequest duplicateDocument = new AgentApplicationRequest("Duplicate ID", "0911000602",
                                "M02A-AgentPass-002", "860002", "Addis Ababa", "Bole", "nid",
                                "m02a-nid-001", null, null);
                mockMvc.perform(post("/api/v1/agent/register").contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(duplicateDocument)))
                                .andExpect(status().isConflict());
                AgentApplicationRequest invalidPhone = new AgentApplicationRequest("Bad Phone", "12345",
                                "M02A-AgentPass-003", "860003", "Addis Ababa", "Bole", "NID",
                                "M02A-NID-003", null, null);
                mockMvc.perform(post("/api/v1/agent/register").contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidPhone)))
                                .andExpect(status().isBadRequest());
                AgentApplicationRequest invalidPin = new AgentApplicationRequest("Bad PIN", "0911000604",
                                "M02A-AgentPass-004", "12", "Addis Ababa", "Bole", "NID",
                                "M02A-NID-004", null, null);
                mockMvc.perform(post("/api/v1/agent/register").contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidPin)))
                                .andExpect(status().isBadRequest());
                assertEquals(1, jdbcTemplate.queryForObject("select count(*) from users where id = ?", Integer.class,
                                userId));

                String pendingToken = jwtService.generateToken(pendingUser);
                mockMvc.perform(get("/api/v1/agent/dashboard").header("Authorization", "Bearer " + pendingToken))
                                .andExpect(status().isUnauthorized());
                mockMvc.perform(post("/api/v1/agent/login").contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new AgentLoginRequest(phone,
                                                application.password(), application.pin()))))
                                .andExpect(status().isUnauthorized());

                String playerToken = issueTokenFor("0911000004");
                String agentRoleToken = issueTokenFor("0911000002");
                mockMvc.perform(post("/api/v1/admin/agents/{agentId}/approve", agentId)
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isForbidden());
                mockMvc.perform(post("/api/v1/admin/agents/{agentId}/approve", agentId)
                                .header("Authorization", "Bearer " + agentRoleToken))
                                .andExpect(status().isForbidden());

                String adminToken = issueTokenFor("0911000001");
                String attemptedClientStatus = "{\"status\":\"SUSPENDED\",\"city\":\"Client City\"}";
                mockMvc.perform(post("/api/v1/admin/agents/{agentId}/approve", agentId)
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(attemptedClientStatus))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("APPROVED"));
                assertEquals(AgentStatus.APPROVED, agentRepository.findById(agentId).orElseThrow().getStatus());
                assertEquals("Addis Ababa", agentRepository.findById(agentId).orElseThrow().getCity());
                assertEquals(AccountStatus.ACTIVE, userRepository.findById(userId).orElseThrow().getStatus());
                assertEquals(1, jdbcTemplate.queryForObject(
                                "select count(*) from audit_log_entries where action = 'APPROVE_AGENT_APPLICATION' and target_id = ?",
                                Integer.class, agentId));
                assertEquals(1, jdbcTemplate.queryForObject("select count(*) from agent_floats where agent_id = ?",
                                Integer.class, agentId));

                mockMvc.perform(post("/api/v1/admin/agents/{agentId}/approve", agentId)
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isConflict());
                mockMvc.perform(put("/api/v1/admin/users/{userId}", userId)
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"displayName\":\"M02A Agent One\",\"email\":\"agent@example.test\",\"phoneNumber\":\"0911000601\",\"status\":\"SUSPENDED\",\"roles\":[\"SUPPORT\"]}"))
                                .andExpect(status().isConflict());

                String loginJson = objectMapper.writeValueAsString(new AgentLoginRequest(phone,
                                application.password(), application.pin()));
                String loginBody = mockMvc.perform(post("/api/v1/agent/login")
                                .contentType(MediaType.APPLICATION_JSON).content(loginJson))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();
                String approvedToken = objectMapper.readTree(loginBody).get("accessToken").asText();
                mockMvc.perform(get("/api/v1/agent/dashboard").header("Authorization", "Bearer " + approvedToken))
                                .andExpect(status().isOk());
                mockMvc.perform(get("/api/v1/agent/float").header("Authorization", "Bearer " + approvedToken))
                                .andExpect(status().isOk());
                assertEquals(0, agentFloatRepository.findByAgentId(agentId).orElseThrow()
                                .getAvailableBalance().compareTo(java.math.BigDecimal.ZERO));

                mockMvc.perform(post("/api/v1/admin/agents/{agentId}/suspend", agentId)
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("SUSPENDED"));
                assertEquals(AccountStatus.SUSPENDED, userRepository.findById(userId).orElseThrow().getStatus());
                mockMvc.perform(get("/api/v1/agent/dashboard").header("Authorization", "Bearer " + approvedToken))
                                .andExpect(status().isUnauthorized());
                mockMvc.perform(get("/api/v1/agent/float").header("Authorization", "Bearer " + approvedToken))
                                .andExpect(status().isUnauthorized());
                mockMvc.perform(post("/api/v1/agent/float/transfers")
                                .header("Authorization", "Bearer " + approvedToken)
                                .header("Idempotency-Key", "m02a-suspended-float-transfer")
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                                .andExpect(status().isUnauthorized());
                mockMvc.perform(post("/api/v1/agent/login").contentType(MediaType.APPLICATION_JSON)
                                .content(loginJson))
                                .andExpect(status().isUnauthorized());
                assertEquals(1, jdbcTemplate.queryForObject(
                                "select count(*) from audit_log_entries where action = 'ADMIN_SUSPEND_AGENT' and target_id = ?",
                                Integer.class, agentId));
                assertEquals(0, agentFloatRepository.findByAgentId(agentId).orElseThrow()
                                .getAvailableBalance().compareTo(java.math.BigDecimal.ZERO));
        }

        @Test
        void adminCanChangePlayerStatusAndRestrictedAccountsCannotLoginOrReuseJwt() throws Exception {
                User player = userRepository.findByNormalizedPhone(phoneNormalizer.normalize("0911000004"))
                                .orElseThrow();
                String adminToken = issueTokenFor("0911000001");
                String playerToken = issueTokenFor("0911000004");
                int walletCountBefore = jdbcTemplate.queryForObject(
                                "select count(*) from wallets where owner_id = ?", Integer.class, player.getId());

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/v1/admin/players/" + player.getId() + "/status")
                                .header("Authorization", "Bearer " + playerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"PENDING\",\"reason\":\"QA status enforcement\"}"))
                                .andExpect(status().isForbidden());

                for (String restrictedStatus : new String[] { "PENDING", "DISABLED" }) {
                        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                        "/api/v1/admin/players/" + player.getId() + "/status")
                                        .header("Authorization", "Bearer " + adminToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"status\":\"" + restrictedStatus
                                                        + "\",\"reason\":\"QA status enforcement\"}"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.status").value(restrictedStatus));

                        mockMvc.perform(post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(new LoginRequest("0911000004", "123456"))))
                                        .andExpect(status().isUnauthorized());
                        mockMvc.perform(get("/api/v1/wallet/balance")
                                        .header("Authorization", "Bearer " + playerToken))
                                        .andExpect(status().isUnauthorized());

                        assertEquals(walletCountBefore, jdbcTemplate.queryForObject(
                                        "select count(*) from wallets where owner_id = ?", Integer.class, player.getId()));
                }

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/v1/admin/players/" + player.getId() + "/status")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"ACTIVE\",\"reason\":\"Restore integration fixture\"}"))
                                .andExpect(status().isOk());
        }

        @Test
        void adminEndpointIsProtectedByRole() throws Exception {
                String adminLoginJson = objectMapper.writeValueAsString(new LoginRequest(
                                "0911000001",
                                "123456"));

                String adminToken = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(adminLoginJson))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString()).get("accessToken").asText();

                mockMvc.perform(get("/api/v1/admin/users")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(1)))
                                .andExpect(jsonPath("$.page").value(0))
                                .andExpect(jsonPath("$.size").value(20));
        }

        @Test
        void missingMalformedWrongSignatureExpiredAndTamperedTokensAreRejected() throws Exception {
                mockMvc.perform(get("/api/v1/me"))
                                .andExpect(status().isUnauthorized());
                mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer"))
                                .andExpect(status().isUnauthorized());
                mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-jwt"))
                                .andExpect(status().isUnauthorized());

                User player = userRepository.findByNormalizedPhone(phoneNormalizer.normalize("0911000004"))
                                .orElseThrow();
                Instant now = Instant.now();
                SecretKey validKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                SecretKey otherKey = Keys.hmacShaKeyFor(
                                "different-secret-key-different-secret-key".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                String wrongSignature = Jwts.builder()
                                .subject(player.getNormalizedPhone())
                                .issuedAt(Date.from(now))
                                .expiration(Date.from(now.plusSeconds(60)))
                                .signWith(otherKey, Jwts.SIG.HS256)
                                .compact();
                String expired = Jwts.builder()
                                .subject(player.getNormalizedPhone())
                                .issuedAt(Date.from(now.minusSeconds(120)))
                                .expiration(Date.from(now.minusSeconds(60)))
                                .claim("uid", player.getId())
                                .claim("roles", java.util.List.of("PLAYER"))
                                .signWith(validKey, Jwts.SIG.HS256)
                                .compact();
                String[] validParts = jwtService.generateToken(player).split("\\.");
                String tamperedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                                ("{\"sub\":\"" + player.getNormalizedPhone()
                                                + "\",\"uid\":" + player.getId()
                                                + ",\"roles\":[\"ADMIN\"],\"iat\":" + now.getEpochSecond()
                                                + ",\"exp\":" + now.plusSeconds(60).getEpochSecond() + "}")
                                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                String tamperedClaims = validParts[0] + "." + tamperedPayload + "." + validParts[2];

                for (String token : java.util.List.of(wrongSignature, expired, tamperedClaims)) {
                        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                                        .andExpect(status().isUnauthorized());
                }
        }

        @Test
        void playerAndAgentTokensCannotCrossRoleBoundaries() throws Exception {
                String playerToken = issueTokenFor("0911000004");
                String agentToken = issueTokenFor("0911000002");
                String adminToken = issueTokenFor("0911000001");

                mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isForbidden());
                mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + agentToken))
                                .andExpect(status().isForbidden());
                mockMvc.perform(get("/api/v1/agent/players").header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isForbidden());
                mockMvc.perform(get("/api/v1/agent/players").header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isForbidden());
        }

        @Test
        void failedAuthenticationDoesNotChangeAccountOrWalletState() throws Exception {
                long usersBefore = userRepository.count();
                long walletsBefore = walletRepository.count();
                long transactionsBefore = walletTransactionRepository.count();
                String invalidLogin = objectMapper.writeValueAsString(new LoginRequest("0911000004", "999999"));

                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidLogin))
                                .andExpect(status().isUnauthorized());

                assertEquals(usersBefore, userRepository.count());
                assertEquals(walletsBefore, walletRepository.count());
                assertEquals(transactionsBefore, walletTransactionRepository.count());
        }

        @Test
        void authenticationAttemptsAreRateLimitedAndRecoverAfterLockout() throws Exception {
                String invalidPlayerLogin = objectMapper.writeValueAsString(new LoginRequest("0911000004", "999999"));
                for (int attempt = 0; attempt < 3; attempt++) {
                        mockMvc.perform(post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(invalidPlayerLogin))
                                        .andExpect(status().isUnauthorized());
                }
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidPlayerLogin))
                                .andExpect(status().isTooManyRequests())
                                .andExpect(jsonPath("$.message").value(
                                                "Too many authentication attempts. Try again later."));
                String loginAudit = jdbcTemplate.queryForObject("""
                                select metadata_json
                                from audit_log_entries
                                where action = 'LOGIN'
                                order by id desc
                                limit 1
                                """, String.class);
                org.junit.jupiter.api.Assertions.assertFalse(loginAudit.contains("999999"));

                Thread.sleep(1_100);
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new LoginRequest("0911000004", "123456"))))
                                .andExpect(status().isOk());
        }

        @Test
        void successfulLoginDoesNotConsumeOrClearAccountOrNetworkFailureCounters() throws Exception {
                String normalizedPhone = phoneNormalizer.normalize("0911000004");
                throttleService.recordAccountFailure("player", normalizedPhone);
                throttleService.recordNetworkFailure("127.0.0.1");
                String validLogin = objectMapper.writeValueAsString(new LoginRequest("0911000004", "123456"));
                mockMvc.perform(post("/api/v1/auth/login")
                                .with(request -> {
                                        request.setRemoteAddr("127.0.0.1");
                                        return request;
                                })
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validLogin))
                                .andExpect(status().isOk());

                assertEquals(2, jdbcTemplate.queryForObject(
                                "select count(*) from authentication_attempt_buckets",
                                Integer.class));
                assertEquals(2, jdbcTemplate.queryForObject(
                                "select sum(attempt_count) from authentication_attempt_buckets",
                                Integer.class));
        }

        @Test
        void adminLoginAttemptsAreRateLimitedWithoutEnumeratingAccounts() throws Exception {
                String invalidPassword = objectMapper.writeValueAsString(
                                new com.equar.bingo.backend.feature.auth.dto.AdminLoginRequest(
                                                "qa-admin@example.test", "WrongPass123!"));
                for (int attempt = 0; attempt < 3; attempt++) {
                        mockMvc.perform(post("/api/v1/admin/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(invalidPassword))
                                        .andExpect(status().isUnauthorized());
                }
                mockMvc.perform(post("/api/v1/admin/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidPassword))
                                .andExpect(status().isTooManyRequests());
                String loginAudit = jdbcTemplate.queryForObject("""
                                select metadata_json
                                from audit_log_entries
                                where action = 'LOGIN'
                                order by id desc
                                limit 1
                                """, String.class);
                org.junit.jupiter.api.Assertions.assertFalse(loginAudit.contains("WrongPass123!"));

                String missingAccount = objectMapper.writeValueAsString(
                                new com.equar.bingo.backend.feature.auth.dto.AdminLoginRequest(
                                                "missing@example.test", "WrongPass123!"));
                mockMvc.perform(post("/api/v1/admin/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(missingAccount))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.message").value("Invalid credentials"));
        }

        @Test
        void actuatorHealthIsMinimalAndSensitiveEndpointsRequireAdmin() throws Exception {
                String health = mockMvc.perform(get("/actuator/health"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("UP"))
                                .andExpect(jsonPath("$.components").doesNotExist())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                org.junit.jupiter.api.Assertions.assertFalse(health.contains("jdbc"));
                org.junit.jupiter.api.Assertions.assertFalse(health.contains("bingo_test"));

                mockMvc.perform(get("/actuator/env"))
                                .andExpect(status().isUnauthorized());
                mockMvc.perform(get("/actuator/beans"))
                                .andExpect(status().isUnauthorized());
                mockMvc.perform(get("/actuator/env").with(
                                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                                .user("player").roles("PLAYER")))
                                .andExpect(status().isForbidden());
                mockMvc.perform(get("/actuator/beans").with(
                                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                                .user("player").roles("PLAYER")))
                                .andExpect(status().isForbidden());
                mockMvc.perform(get("/actuator/env").with(
                                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                                .user("agent").roles("AGENT")))
                                .andExpect(status().isForbidden());
                mockMvc.perform(get("/actuator/beans").with(
                                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                                .user("agent").roles("AGENT")))
                                .andExpect(status().isForbidden());

                String environment = mockMvc.perform(get("/actuator/env").with(
                                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                                .user("admin").roles("ADMIN")))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                org.junit.jupiter.api.Assertions.assertFalse(environment.contains("test-secret-key-test-secret"));

                mockMvc.perform(get("/actuator/beans").with(
                                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                                .user("admin").roles("ADMIN")))
                                .andExpect(status().isOk());
        }

        @Test
        void actuatorAuthorizationUsesIssuedRoleBearerTokens() throws Exception {
                String playerToken = issueTokenFor("0911000004");
                String agentToken = issueTokenFor("0911000002");
                String adminToken = issueTokenFor("0911000001");

                mockMvc.perform(get("/actuator/env")
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isForbidden());
                mockMvc.perform(get("/actuator/env")
                                .header("Authorization", "Bearer " + agentToken))
                                .andExpect(status().isForbidden());
                mockMvc.perform(get("/actuator/env")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk());
        }

        private String issueTokenFor(String phone) {
                User user = userRepository.findByNormalizedPhone(phoneNormalizer.normalize(phone))
                                .orElseThrow();
                return jwtService.generateToken(user);
        }
}
