package com.bakaroo.bingo.backend.feature.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.bakaroo.bingo.backend.feature.wallet.entity.Wallet;
import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransaction;
import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransactionType;
import com.bakaroo.bingo.backend.feature.wallet.repository.SignupRewardGrantRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.SignupRewardPolicyRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import com.bakaroo.bingo.backend.feature.wallet.service.SignupRewardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.enabled=false")
// No class-level @Transactional: HTTP requests and executor tasks commit in
// independent transactions. Fixed synthetic phones are cleaned before/after;
// interrupted runs leave rows for the next guarded run's scoped cleanup.
class SignupRewardPostingIntegrationTest extends MySqlIntegrationTest {
    static {
        requireVersion40SchemaBeforeContextStartup();
    }

    private static final String ADMIN_PHONE = "0911000191";
    private static final String PLAYER_PHONE = "0911000192";
    private static final String REGISTER_PHONE = "0911000193";
    private static final String SECOND_REGISTER_PHONE = "0911000194";

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
    @Autowired SignupRewardService signupRewardService;
    @Autowired PlatformTransactionManager transactionManager;

    private static void requireVersion40SchemaBeforeContextStartup() {
        var database = MySqlIntegrationTest.requireIsolatedTestDatabase();
        try (var connection = DriverManager.getConnection(database.url(), database.username(), database.password());
                var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        select (select version from flyway_schema_history where success = 1
                                order by installed_rank desc limit 1),
                               (select count(*) from flyway_schema_history where success = 0)
                        from flyway_schema_history
                        limit 1
                        """)) {
            if (!result.next() || !"40".equals(result.getString(1)) || result.getInt(2) != 0) {
                throw new IllegalStateException(
                        "Refusing reward integration tests: isolated Maven schema must be at successful Flyway V40 with no failed migrations.");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Refusing reward integration tests: could not verify isolated Maven schema version.", exception);
        }
    }

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
        assertTrue(policyRepository.findFirstByOrderByCreatedAtDescIdDesc().orElseThrow().getId()
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

    @Test
    void signupGrantUniquenessIsPerCampaignAndRegistrationRetrySafe() {
        signupRewardService.grantForNewPlayer(player);
        signupRewardService.grantForNewPlayer(player);
        assertEquals(1, grantsFor(activePolicy, player));

        Instant secondCampaignCreatedAt = Instant.now().plusSeconds(1);
        SignupRewardPolicy secondCampaign = policyRepository.save(SignupRewardPolicy.create("Second signup reward",
                SignupRewardPolicy.TriggerType.SIGNUP, true, new BigDecimal("5.00"), "ETB",
                player.getCreatedAt().minusSeconds(1), null, admin, "per-campaign cardinality", secondCampaignCreatedAt));
        signupRewardService.grantForNewPlayer(player);
        signupRewardService.grantForNewPlayer(player);

        assertEquals(1, grantsFor(activePolicy, player));
        assertEquals(1, grantsFor(secondCampaign, player));
        assertEquals(2, transactionRepository.findByOwnerId(player.getId(), PageRequest.of(0, 50)).getContent().stream()
                .filter(entry -> entry.getTransactionType() == WalletTransactionType.SIGNUP_REWARD_CREDIT)
                .count());
    }

    @Test
    void campaignApiRejectsBusinessDateBackdatingAndHistoricalCampaignCanBeDisabled() throws Exception {
        String adminToken = jwtService.generateToken(admin);
        ZoneId businessZone = ZoneId.of("Africa/Addis_Ababa");
        Instant yesterdayStart = LocalDate.now(businessZone).minusDays(1).atStartOfDay(businessZone).toInstant();
        mockMvc.perform(post("/api/v1/admin/wallet/signup-rewards/campaigns")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "name", "Backdated campaign",
                        "triggerType", "SIGNUP",
                        "enabled", true,
                        "rewardAmount", new BigDecimal("5.00"),
                        "currency", "ETB",
                        "effectiveFrom", yesterdayStart.toString()))))
                .andExpect(status().isBadRequest());

        Instant historicalStart = yesterdayStart.minusSeconds(10L * 24 * 60 * 60);
        SignupRewardPolicy historical = policyRepository.save(SignupRewardPolicy.create("Historical campaign",
                SignupRewardPolicy.TriggerType.SIGNUP, true, new BigDecimal("5.00"), "ETB",
                historicalStart, yesterdayStart.minusSeconds(1), admin, "preserved legacy record", historicalStart));
        mockMvc.perform(get("/api/v1/admin/wallet/signup-rewards/campaigns")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + historical.getId() + ")]").isNotEmpty());
        mockMvc.perform(put("/api/v1/admin/wallet/signup-rewards/settings/" + historical.getId() + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        assertFalse(policyRepository.findById(historical.getId()).orElseThrow().isEnabled());
    }

    @Test
    void migratedGrantUniquenessIsCampaignEventScoped() {
        Integer campaignEventUniqueColumns = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.statistics
                where table_schema = database() and table_name = 'signup_reward_grants'
                  and index_name = 'uk_signup_reward_grants_campaign_event' and non_unique = 0
                """, Integer.class);
        Integer obsoleteGlobalSignupUnique = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.statistics
                where table_schema = database() and table_name = 'signup_reward_grants'
                  and index_name = 'uk_signup_reward_grants_signup_player'
                """, Integer.class);
        Integer eventKeyLength = jdbcTemplate.queryForObject("""
                select character_maximum_length from information_schema.columns
                where table_schema = database() and table_name = 'signup_reward_grants'
                  and column_name = 'trigger_reference_key'
                """, Integer.class);

        assertEquals(2, campaignEventUniqueColumns);
        assertEquals(0, obsoleteGlobalSignupUnique);
        assertEquals(80, eventKeyLength);
    }

    @Test
    void referralCodeRegistrationAwardsReferrerOnceAndKeepsReferredIdentityPrivate() throws Exception {
        Instant now = Instant.now();
        SignupRewardPolicy referralPolicy = policyRepository.save(SignupRewardPolicy.create(
                "Referral reward", SignupRewardPolicy.TriggerType.REFERRAL, true, new BigDecimal("15.00"), "ETB",
                now.minusSeconds(60), now.plusSeconds(3600), admin, "registration trigger", now));
        RegisterRequest request = new RegisterRequest("M03D Referred Player", REGISTER_PHONE, "123456",
                player.getReferralCode().toLowerCase(Locale.ROOT));
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        User referred = userRepository.findByNormalizedPhone(phoneNormalizer.normalize(REGISTER_PHONE)).orElseThrow();
        assertEquals(player.getId(), referred.getReferredBy().getId());
        assertTrue(referred.getReferralCode().matches("B[0-9A-F]{20}"));
        assertTrue(referred.getReferralCode().length() <= 24);
        var referralGrant = grantRepository.findAll().stream()
                .filter(grant -> grant.getPolicy().getId().equals(referralPolicy.getId()))
                .filter(grant -> grant.getPlayer().getId().equals(player.getId()))
                .findFirst().orElseThrow();
        assertEquals("REFERRAL-" + referred.getId(), referralGrant.getTriggerReferenceKey());
        assertEquals(new BigDecimal("15.00"), referralGrant.getRewardAmount());

        var entries = transactionRepository.findByReferenceTypeAndReferenceIdOrderByCreatedAtAscIdAsc(
                "PROMOTIONAL_REWARD", referralGrant.getId());
        assertEquals(1, entries.size());
        assertEquals(referralGrant.getOperationId(), entries.getFirst().getOperationId());
        assertEquals(referralGrant.getRewardAmount(), entries.getFirst().getAmount());
        assertFalse(entries.getFirst().getNote().contains(referred.getDisplayName()));
        assertFalse(entries.getFirst().getNote().contains(REGISTER_PHONE));
        assertFalse(entries.getFirst().getNote().contains(referred.getNormalizedPhone()));

        String referrerHistory = mockMvc.perform(get("/api/v1/wallet/transactions")
                .header("Authorization", "Bearer " + jwtService.generateToken(player)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(referrerHistory.contains(referred.getDisplayName()));
        assertFalse(referrerHistory.contains(REGISTER_PHONE));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        RegisterRequest secondRequest = new RegisterRequest("M03D Second Referred Player", SECOND_REGISTER_PHONE,
                "123456", player.getReferralCode());
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(secondRequest))).andExpect(status().isCreated());
        var referralGrants = grantRepository.findAll().stream()
                .filter(grant -> grant.getPolicy().getId().equals(referralPolicy.getId()))
                .filter(grant -> grant.getPlayer().getId().equals(player.getId())).toList();
        assertEquals(2, referralGrants.size(), "one campaign can reward each distinct referred registration");
        assertEquals(2, referralGrants.stream().map(grant -> grant.getTriggerReferenceKey()).distinct().count());
        assertEquals(initialGrantCount + 4, grantRepository.count(),
                "two registrations get one signup and one referral grant each; retry adds no duplicate");
    }

    @Test
    void concurrentRegistrationRetriesAwardOnlyOneReferral() throws Exception {
        Instant now = Instant.now();
        SignupRewardPolicy referralPolicy = policyRepository.save(SignupRewardPolicy.create(
                "Referral reward", SignupRewardPolicy.TriggerType.REFERRAL, true, new BigDecimal("15.00"), "ETB",
                now.minusSeconds(60), now.plusSeconds(3600), admin, null, now));
        String body = objectMapper.writeValueAsString(new RegisterRequest(
                "M03D Concurrent Referral", REGISTER_PHONE, "123456", player.getReferralCode()));
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body)).andReturn().getResponse().getStatus();
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body)).andReturn().getResponse().getStatus();
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(Set.of(201, 409), Set.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)));
        } finally {
            executor.shutdownNow();
        }

        User referred = userRepository.findByNormalizedPhone(phoneNormalizer.normalize(REGISTER_PHONE)).orElseThrow();
        assertEquals(player.getId(), referred.getReferredBy().getId());
        assertEquals(1, grantsFor(referralPolicy, player));
    }

    @Test
    void invalidAndSelfReferralCodesAreRejectedBeforeRegistration() throws Exception {
        long userCount = userRepository.count();
        long grantCount = grantRepository.count();
        RegisterRequest invalid = new RegisterRequest("M03D Invalid Referral", REGISTER_PHONE, "123456", "missing-code");
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        RegisterRequest self = new RegisterRequest("M03D Self Referral", PLAYER_PHONE, "123456",
                player.getReferralCode());
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(self)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_ALREADY_REGISTERED"));
        assertEquals(userCount, userRepository.count());
        assertEquals(grantCount, grantRepository.count());
    }

    @Test
    void walletCreditThresholdUsesCampaignWindowEtbDepositCreditsAndPostsOneExactGrant() {
        Instant now = Instant.now();
        SignupRewardPolicy campaign = policyRepository.save(SignupRewardPolicy.create(
                "Wallet credit reward", SignupRewardPolicy.TriggerType.WALLET_CREDIT, true,
                new BigDecimal("7.50"), new BigDecimal("50.00"), "ETB", now.minusSeconds(3600), now.plusSeconds(3600), admin,
                "campaign-window verified deposits", now));
        Wallet wallet = walletRepository.save(Wallet.create(player, "ETB"));
        Instant beforeCampaign = campaign.getEffectiveFrom().minusSeconds(1);
        wallet = recordCredit(wallet, WalletTransactionType.DEPOSIT_CREDIT, new BigDecimal("100.00"), beforeCampaign);
        wallet = recordCredit(wallet, WalletTransactionType.PROMOTIONAL_REWARD_CREDIT,
                new BigDecimal("500.00"), now);
        wallet = recordCredit(wallet, WalletTransactionType.DEPOSIT_CREDIT, new BigDecimal("49.99"),
                campaign.getEffectiveFrom());

        signupRewardService.grantWalletCreditReward(player);
        assertEquals(0, grantsFor(campaign, player));

        wallet = recordCredit(wallet, WalletTransactionType.DEPOSIT_CREDIT, new BigDecimal("0.01"),
                campaign.getEffectiveUntil());
        signupRewardService.grantWalletCreditReward(player);
        assertEquals(0, grantsFor(campaign, player), "a deposit at the exclusive end instant is not eligible");

        wallet = recordCredit(wallet, WalletTransactionType.DEPOSIT_CREDIT, new BigDecimal("0.01"),
                campaign.getEffectiveUntil().minusSeconds(1));
        signupRewardService.grantWalletCreditReward(player);
        signupRewardService.grantWalletCreditReward(player);

        var grant = grantRepository.findAll().stream()
                .filter(row -> row.getPolicy().getId().equals(campaign.getId()))
                .filter(row -> row.getPlayer().getId().equals(player.getId()))
                .findFirst().orElseThrow();
        assertEquals("WALLET-CREDIT-" + player.getId(), grant.getTriggerReferenceKey());
        assertEquals(1, grantsFor(campaign, player));
        assertEquals(new BigDecimal("507.50"), walletRepository.findByOwnerId(player.getId()).orElseThrow()
                .getPromotionalBalance());
        var ledger = transactionRepository.findByReferenceTypeAndReferenceIdOrderByCreatedAtAscIdAsc(
                "PROMOTIONAL_REWARD", grant.getId());
        assertEquals(1, ledger.size());
        assertEquals(grant.getOperationId(), ledger.getFirst().getOperationId());
        assertEquals(new BigDecimal("7.50"), ledger.getFirst().getAmount());

        Wallet afterFirstAward = walletRepository.findByOwnerId(player.getId()).orElseThrow();
        recordCredit(afterFirstAward, WalletTransactionType.DEPOSIT_CREDIT,
                new BigDecimal("10.00"), now.plusSeconds(1));
        signupRewardService.grantWalletCreditReward(player);
        assertEquals(1, grantsFor(campaign, player),
                "a later qualifying deposit must not award this campaign to the same player again");
        assertEquals(new BigDecimal("507.50"), walletRepository.findByOwnerId(player.getId()).orElseThrow()
                .getPromotionalBalance());

        User aboveThresholdPlayer = userRepository.save(User.create(
                phoneNormalizer.normalize(REGISTER_PHONE), "M03D Above Threshold", passwordEncoder.encode("123456"),
                AccountStatus.ACTIVE, Set.of(UserRole.PLAYER), Instant.now()));
        Wallet aboveThresholdWallet = walletRepository.save(Wallet.create(aboveThresholdPlayer, "ETB"));
        recordCredit(aboveThresholdWallet, WalletTransactionType.DEPOSIT_CREDIT,
                new BigDecimal("50.01"), campaign.getEffectiveFrom());
        signupRewardService.grantWalletCreditReward(aboveThresholdPlayer);
        assertEquals(1, grantsFor(campaign, aboveThresholdPlayer));
        assertEquals(new BigDecimal("7.50"), walletRepository.findByOwnerId(aboveThresholdPlayer.getId())
                .orElseThrow().getPromotionalBalance());
    }

    @Test
    void concurrentThresholdRechecksCreateOnlyOneGrantAndCredit() throws Exception {
        Instant now = Instant.now();
        SignupRewardPolicy campaign = policyRepository.save(SignupRewardPolicy.create(
                "Wallet credit reward", SignupRewardPolicy.TriggerType.WALLET_CREDIT, true,
                new BigDecimal("7.50"), new BigDecimal("50.00"), "ETB", now.minusSeconds(60), now.plusSeconds(3600), admin, null, now));
        Wallet wallet = walletRepository.save(Wallet.create(player, "ETB"));
        wallet = recordCredit(wallet, WalletTransactionType.DEPOSIT_CREDIT, new BigDecimal("50.00"), now);

        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                signupRewardService.grantWalletCreditReward(player);
                return null;
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                signupRewardService.grantWalletCreditReward(player);
                return null;
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, grantsFor(campaign, player));
        assertEquals(new BigDecimal("7.50"), walletRepository.findByOwnerId(player.getId()).orElseThrow()
                .getPromotionalBalance());
        var grant = grantRepository.findAll().stream()
                .filter(row -> row.getPolicy().getId().equals(campaign.getId()))
                .filter(row -> row.getPlayer().getId().equals(player.getId()))
                .findFirst().orElseThrow();
        assertEquals(1, transactionRepository.findByReferenceTypeAndReferenceIdOrderByCreatedAtAscIdAsc(
                "PROMOTIONAL_REWARD", grant.getId()).size());
    }

    @Test
    void rolledBackRewardProcessingLeavesNoPartialGrantAndCanBeRetried() {
        Instant now = Instant.now();
        SignupRewardPolicy campaign = policyRepository.save(SignupRewardPolicy.create(
                "Wallet credit reward", SignupRewardPolicy.TriggerType.WALLET_CREDIT, true,
                new BigDecimal("7.50"), new BigDecimal("50.00"), "ETB", now.minusSeconds(60), now.plusSeconds(3600), admin, null, now));
        Wallet wallet = walletRepository.save(Wallet.create(player, "ETB"));
        wallet = recordCredit(wallet, WalletTransactionType.DEPOSIT_CREDIT, new BigDecimal("50.00"), now);
        long transactionCount = transactionRepository.count();

        var tx = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
            signupRewardService.grantWalletCreditReward(player);
            throw new IllegalStateException("simulate caller rollback");
        }));

        assertEquals(0, grantsFor(campaign, player));
        assertEquals(BigDecimal.ZERO.setScale(2), walletRepository.findByOwnerId(player.getId()).orElseThrow()
                .getPromotionalBalance());
        assertEquals(transactionCount, transactionRepository.count());

        signupRewardService.grantWalletCreditReward(player);
        assertEquals(1, grantsFor(campaign, player));
        assertEquals(new BigDecimal("7.50"), walletRepository.findByOwnerId(player.getId()).orElseThrow()
                .getPromotionalBalance());
    }

    private int grantsFor(SignupRewardPolicy campaign, User beneficiary) {
        return (int) grantRepository.findAll().stream()
                .filter(grant -> grant.getPolicy().getId().equals(campaign.getId()))
                .filter(grant -> grant.getPlayer().getId().equals(beneficiary.getId()))
                .count();
    }

    private Wallet recordCredit(Wallet wallet, WalletTransactionType type, BigDecimal amount, Instant createdAt) {
        BigDecimal availableBefore = wallet.getAvailableBalance();
        BigDecimal availableAfter = availableBefore;
        BigDecimal promotionalBefore = wallet.getPromotionalBalance();
        BigDecimal promotionalAfter = promotionalBefore;
        if (type == WalletTransactionType.DEPOSIT_CREDIT) {
            availableAfter = availableBefore.add(amount);
            wallet.setAvailableBalance(availableAfter);
        } else if (type == WalletTransactionType.PROMOTIONAL_REWARD_CREDIT) {
            promotionalAfter = promotionalBefore.add(amount);
            wallet.setPromotionalBalance(promotionalAfter);
        }
        Wallet savedWallet = walletRepository.save(wallet);
        transactionRepository.save(WalletTransaction.create(savedWallet, UUID.randomUUID().toString(), type, amount,
                availableBefore, availableAfter, promotionalBefore, promotionalAfter, "ETB", "QA_FIXTURE", null,
                "approved posted test fixture", null, createdAt));
        return savedWallet;
    }

    private void cleanupFixtures() {
        String adminNormalized = phoneNormalizer.normalize(ADMIN_PHONE);
        String playerNormalized = phoneNormalizer.normalize(PLAYER_PHONE);
        String registerNormalized = phoneNormalizer.normalize(REGISTER_PHONE);
        String secondRegisterNormalized = phoneNormalizer.normalize(SECOND_REGISTER_PHONE);
        jdbcTemplate.update("delete from signup_reward_grants where player_id in (select id from users where normalized_phone in (?, ?, ?, ?))",
                adminNormalized, playerNormalized, registerNormalized, secondRegisterNormalized);
        jdbcTemplate.update("delete from wallet_transactions where wallet_id in (select id from wallets where owner_id in (select id from users where normalized_phone in (?, ?, ?, ?)))",
                adminNormalized, playerNormalized, registerNormalized, secondRegisterNormalized);
        jdbcTemplate.update("delete from wallets where owner_id in (select id from users where normalized_phone in (?, ?, ?, ?))",
                adminNormalized, playerNormalized, registerNormalized, secondRegisterNormalized);
        jdbcTemplate.update("delete from signup_reward_policies where changed_by_user_id in (select id from users where normalized_phone = ?)",
                adminNormalized);
        jdbcTemplate.update("delete from audit_log_entries where actor_user_id in (select id from users where normalized_phone in (?, ?, ?, ?))",
                adminNormalized, playerNormalized, registerNormalized, secondRegisterNormalized);
        jdbcTemplate.update("delete from user_roles where user_id in (select id from users where normalized_phone in (?, ?, ?, ?))",
                adminNormalized, playerNormalized, registerNormalized, secondRegisterNormalized);
        jdbcTemplate.update("delete from users where normalized_phone in (?, ?, ?, ?)",
                adminNormalized, playerNormalized, registerNormalized, secondRegisterNormalized);
    }
}
