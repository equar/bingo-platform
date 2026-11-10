package com.bakaroo.bingo.backend.feature.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bakaroo.bingo.backend.MySqlIntegrationTest;
import com.bakaroo.bingo.backend.feature.auth.dto.RegisterRequest;
import com.bakaroo.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.bakaroo.bingo.backend.feature.auth.service.JwtService;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.bakaroo.bingo.backend.feature.wallet.entity.SignupRewardPolicy;
import com.bakaroo.bingo.backend.feature.wallet.repository.SignupRewardGrantRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.SignupRewardPolicyRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignupRewardPostingIntegrationTest extends MySqlIntegrationTest {
    private static final String ADMIN_PHONE = "0911000191";
    private static final String PLAYER_PHONE = "0911000192";
    private static final String REGISTER_PHONE = "0911000193";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired SignupRewardPolicyRepository policyRepository;
    @Autowired SignupRewardGrantRepository grantRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired WalletTransactionRepository transactionRepository;
    @Autowired EthiopianPhoneNormalizer phoneNormalizer;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbcTemplate;

    private User admin;
    private User player;
    private SignupRewardPolicy activePolicy;
    private long initialPolicyCount;
    private long initialGrantCount;
    private long initialWalletCount;
    private long initialTransactionCount;

    @BeforeEach
    void setUp() {
        cleanupFixtures();
        Instant now = Instant.now();
        admin = userRepository.save(User.create(phoneNormalizer.normalize(ADMIN_PHONE), "M03D Guard Admin",
                passwordEncoder.encode("AdminPass123!"), AccountStatus.ACTIVE, Set.of(UserRole.ADMIN), now));
        player = userRepository.save(User.create(phoneNormalizer.normalize(PLAYER_PHONE), "M03D Guard Player",
                passwordEncoder.encode("123456"), AccountStatus.ACTIVE, Set.of(UserRole.PLAYER), now));
        activePolicy = policyRepository.save(SignupRewardPolicy.create(true, new BigDecimal("25.00"), "ETB",
                now.minusSeconds(60), now.plusSeconds(3600), admin, "Isolated positive policy guard fixture", now));
        initialPolicyCount = policyRepository.count();
        initialGrantCount = grantRepository.count();
        initialWalletCount = walletRepository.count();
        initialTransactionCount = transactionRepository.count();
    }

    @AfterEach
    void tearDown() {
        cleanupFixtures();
    }

    @Test
    void positiveRewardConfigurationAndAuthorizationAreEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/admin/wallet/signup-rewards/settings"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/wallet/signup-rewards/settings")
                .header("Authorization", "Bearer " + jwtService.generateToken(player)))
                .andExpect(status().isForbidden());

        String adminToken = jwtService.generateToken(admin);
        mockMvc.perform(get("/api/v1/admin/wallet/signup-rewards/settings")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(activePolicy.getId()))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.rewardAmount").value(25.00))
                .andExpect(jsonPath("$.currency").value("ETB"));
        mockMvc.perform(get("/api/v1/admin/wallet/signup-rewards/settings/history")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(activePolicy.getId()));

        String invalidDatePolicy = """
                {"enabled":true,"rewardAmount":25.00,"currency":"ETB",
                 "effectiveFrom":"%s","effectiveUntil":"%s","reason":"invalid date range probe"}
                """.formatted(Instant.now().plusSeconds(3600), Instant.now().plusSeconds(1800));
        mockMvc.perform(post("/api/v1/admin/wallet/signup-rewards/settings")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(invalidDatePolicy))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNUP_REWARD_DATES"));

        String positivePolicy = """
                {"enabled":true,"rewardAmount":25.00,"currency":"ETB",
                 "effectiveFrom":"%s","effectiveUntil":"%s","reason":"approved signup reward"}
                """.formatted(Instant.now().plusSeconds(60), Instant.now().plusSeconds(3600));
        mockMvc.perform(post("/api/v1/admin/wallet/signup-rewards/settings")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(positivePolicy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.rewardAmount").value(25.00));

        assertEquals(initialPolicyCount + 1, policyRepository.count());
        assertTrue(policyRepository.findFirstByOrderByCreatedAtDesc().orElseThrow().getId()
                .compareTo(activePolicy.getId()) > 0);
        assertTrue(jdbcTemplate.queryForObject(
                "select count(*) from audit_log_entries where actor_user_id = ? and action = 'SAVE_SETTINGS'",
                Long.class, admin.getId()) > 0);
    }

    @Test
    void registrationWithPositivePolicyCreditsNewWallet() throws Exception {
        String registrationBody = objectMapper.writeValueAsString(
                new RegisterRequest("M03D Rewarded Registration", REGISTER_PHONE, "123456"));
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registrationBody))
                .andExpect(status().isCreated());

        User registeredPlayer = userRepository.findByNormalizedPhone(phoneNormalizer.normalize(REGISTER_PHONE))
                .orElseThrow();
        var wallet = walletRepository.findByOwnerId(registeredPlayer.getId()).orElseThrow();
        assertEquals(new BigDecimal("25.00"), wallet.getPromotionalBalance());
        assertEquals(initialPolicyCount, policyRepository.count());
        assertEquals(initialGrantCount + 1, grantRepository.count());
        assertEquals(initialWalletCount + 1, walletRepository.count());
        assertEquals(initialTransactionCount + 1, transactionRepository.count());
    }

    private void cleanupFixtures() {
        String adminNormalized = phoneNormalizer.normalize(ADMIN_PHONE);
        String playerNormalized = phoneNormalizer.normalize(PLAYER_PHONE);
        String registerNormalized = phoneNormalizer.normalize(REGISTER_PHONE);
        jdbcTemplate.update("delete from signup_reward_grants where player_id in (select id from users where normalized_phone in (?, ?, ?))",
                adminNormalized, playerNormalized, registerNormalized);
        jdbcTemplate.update("delete from wallet_transactions where wallet_id in (select id from wallets where owner_id in (select id from users where normalized_phone in (?, ?, ?)))",
                adminNormalized, playerNormalized, registerNormalized);
        jdbcTemplate.update("delete from wallets where owner_id in (select id from users where normalized_phone in (?, ?, ?))",
                adminNormalized, playerNormalized, registerNormalized);
        jdbcTemplate.update("delete from signup_reward_policies where changed_by_user_id in (select id from users where normalized_phone = ?)",
                adminNormalized);
        jdbcTemplate.update("delete from audit_log_entries where actor_user_id in (select id from users where normalized_phone in (?, ?, ?))",
                adminNormalized, playerNormalized, registerNormalized);
        jdbcTemplate.update("delete from user_roles where user_id in (select id from users where normalized_phone in (?, ?, ?))",
                adminNormalized, playerNormalized, registerNormalized);
        jdbcTemplate.update("delete from users where normalized_phone in (?, ?, ?)",
                adminNormalized, playerNormalized, registerNormalized);
    }
}
