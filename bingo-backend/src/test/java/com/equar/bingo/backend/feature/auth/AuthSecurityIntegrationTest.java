package com.equar.bingo.backend.feature.auth;

import com.equar.bingo.backend.feature.auth.dto.LoginRequest;
import com.equar.bingo.backend.feature.auth.dto.RegisterRequest;
import com.equar.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityIntegrationTest {

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

    @BeforeEach
    void setUp() {
        walletTransactionRepository.deleteAllInBatch();
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
        userRepository.save(User.create(
                phoneNormalizer.normalize("0911000001"),
                "Admin User",
                passwordEncoder.encode("AdminPass123!"),
                AccountStatus.ACTIVE,
                Set.of(UserRole.ADMIN),
                now));

        userRepository.save(User.create(
                phoneNormalizer.normalize("0911000002"),
                "Agent User",
                passwordEncoder.encode("AgentPass123!"),
                AccountStatus.ACTIVE,
                Set.of(UserRole.AGENT),
                now));
    }

    @Test
    void registerLoginAndProtectionWork() throws Exception {
        String registerJson = objectMapper.writeValueAsString(new RegisterRequest(
                "Player One",
                "09 11 00 00 03",
                "PlayerPass123!"));

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.phoneNumber").value("251911000003"))
                .andExpect(jsonPath("$.user.roles[0]").value("PLAYER"));

        String loginJson = objectMapper.writeValueAsString(new LoginRequest(
                "0911000003",
                "PlayerPass123!"));

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
                "AdminPass123!"));

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
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }
}