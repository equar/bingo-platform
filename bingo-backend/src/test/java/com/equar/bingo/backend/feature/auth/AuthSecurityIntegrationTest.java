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
import com.equar.bingo.backend.feature.support.repository.InternalNoteRepository;
import com.equar.bingo.backend.feature.support.repository.SupportMessageRepository;
import com.equar.bingo.backend.feature.support.repository.SupportTicketRepository;
import com.equar.bingo.backend.feature.wallet.repository.DepositClaimRepository;
import com.equar.bingo.backend.feature.wallet.repository.IdempotencyRecordRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                String registerJson = objectMapper.writeValueAsString(new RegisterRequest(
                                "Player One",
                                "09 11 00 00 03",
                                "123456"));

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
