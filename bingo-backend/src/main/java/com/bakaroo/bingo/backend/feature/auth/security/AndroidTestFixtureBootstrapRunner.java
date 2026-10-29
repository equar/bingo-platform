package com.bakaroo.bingo.backend.feature.auth.security;

import com.bakaroo.bingo.backend.feature.agent.dto.AgentApplicationRequest;
import com.bakaroo.bingo.backend.feature.agent.entity.Agent;
import com.bakaroo.bingo.backend.feature.agent.entity.AgentStatus;
import com.bakaroo.bingo.backend.feature.agent.repository.AgentRepository;
import com.bakaroo.bingo.backend.feature.agent.service.AgentService;
import com.bakaroo.bingo.backend.feature.auth.dto.RegisterRequest;
import com.bakaroo.bingo.backend.feature.auth.service.AuthService;
import com.bakaroo.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in, create-only Android-test fixtures. Never resets credentials or mutates existing accounts. */
@Component
@Profile("android-test & !prod & !qa")
@Order(Ordered.LOWEST_PRECEDENCE)
public class AndroidTestFixtureBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AndroidTestFixtureBootstrapRunner.class);
    private static final Pattern TEST_JDBC_URL = Pattern.compile(
            "^jdbc:mysql://127\\.0\\.0\\.1:3306/bingo_qa_test\\?([^#]+)$",
            Pattern.CASE_INSENSITIVE);
    private static final String EXPECTED_DATABASE = "bingo_qa_test";

    private final DataSource dataSource;
    private final Environment environment;
    private final UserRepository users;
    private final AgentRepository agents;
    private final PasswordEncoder passwordEncoder;
    private final PasswordEncoder agentPinEncoder;
    private final EthiopianPhoneNormalizer phoneNormalizer;
    private final AgentService agentService;
    private final AuthService authService;
    private final boolean enabled;
    private final String confirmation;
    private final String playerPhone;
    private final String playerPin;
    private final String adminPhone;
    private final String adminEmail;
    private final String adminPassword;
    private final String approvalCandidatePhone;
    private final String approvalCandidatePin;
    private final String pendingAgentPhone;
    private final String pendingAgentPin;

    public AndroidTestFixtureBootstrapRunner(
            DataSource dataSource,
            Environment environment,
            UserRepository users,
            AgentRepository agents,
            PasswordEncoder passwordEncoder,
            @Qualifier("agentPinEncoder") PasswordEncoder agentPinEncoder,
            EthiopianPhoneNormalizer phoneNormalizer,
            AgentService agentService,
            AuthService authService,
            @Value("${app.qa.android-test-fixtures-bootstrap-enabled:false}") boolean enabled,
            @Value("${BINGO_ANDROID_TEST_FIXTURES_CONFIRMATION:}") String confirmation,
            @Value("${BINGO_QA_PLAYER_001_PHONE:}") String playerPhone,
            @Value("${BINGO_QA_PLAYER_001_PIN:}") String playerPin,
            @Value("${BINGO_ANDROID_TEST_ADMIN_PHONE:}") String adminPhone,
            @Value("${BINGO_E2E_ADMIN_EMAIL:}") String adminEmail,
            @Value("${BINGO_E2E_ADMIN_PASSWORD:}") String adminPassword,
            @Value("${BINGO_QA_APPROVED_AGENT_PHONE:}") String approvalCandidatePhone,
            @Value("${BINGO_QA_APPROVED_AGENT_PIN:}") String approvalCandidatePin,
            @Value("${BINGO_QA_PENDING_AGENT_PHONE:}") String pendingAgentPhone,
            @Value("${BINGO_QA_PENDING_AGENT_PIN:}") String pendingAgentPin) {
        this.dataSource = dataSource;
        this.environment = environment;
        this.users = users;
        this.agents = agents;
        this.passwordEncoder = passwordEncoder;
        this.agentPinEncoder = agentPinEncoder;
        this.phoneNormalizer = phoneNormalizer;
        this.agentService = agentService;
        this.authService = authService;
        this.enabled = enabled;
        this.confirmation = confirmation;
        this.playerPhone = playerPhone;
        this.playerPin = playerPin;
        this.adminPhone = adminPhone;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.approvalCandidatePhone = approvalCandidatePhone;
        this.approvalCandidatePin = approvalCandidatePin;
        this.pendingAgentPhone = pendingAgentPhone;
        this.pendingAgentPin = pendingAgentPin;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) {
            return;
        }

        verifyRuntimeTarget();
        validateSettings();

        List<FixtureIdentity> fixtures = new ArrayList<>();
        fixtures.add(ensurePlayer());
        fixtures.add(ensureAdmin());
        fixtures.add(ensureAgent(approvalCandidatePhone, approvalCandidatePin,
                "QA Android Test Approval Candidate", "APPROVAL-CANDIDATE", true));
        fixtures.add(ensureAgent(pendingAgentPhone, pendingAgentPin,
                "QA Android Test Pending Agent", "PENDING", false));

        log.info("ANDROID_TEST_FIXTURES_READY {}", fixtures);
    }

    private void verifyRuntimeTarget() throws Exception {
        if (!environment.acceptsProfiles(org.springframework.core.env.Profiles.of("android-test"))) {
            throw new IllegalStateException("Fixture bootstrap requires the android-test profile.");
        }
        require("bingo_qa_test".equals(confirmation),
                "Fixture bootstrap confirmation must identify bingo_qa_test.");
        requireDisabled("app.game.draw-scheduler-enabled");
        requireDisabled("app.wallet.signup-reward-test-posting-enabled");
        requireDisabled("app.wallet.deposit-simulation-enabled");
        requireDisabled("app.wallet.adjustment-simulation-enabled");
        requireDisabled("app.agent.float-simulation-enabled");
        requireDisabled("app.qa.synthetic-financial-tests-enabled");

        try (Connection connection = dataSource.getConnection()) {
            String jdbcUrl = connection.getMetaData().getURL();
            var urlMatch = TEST_JDBC_URL.matcher(jdbcUrl);
            require(urlMatch.matches()
                            && urlMatch.group(1).matches("(?i).*(?:^|&)createDatabaseIfNotExist=false(?:&|$).*"),
                    "Fixture bootstrap datasource must be local bingo_qa_test with database creation disabled.");
            require(EXPECTED_DATABASE.equals(connection.getCatalog()),
                    "Fixture bootstrap connection catalog is not bingo_qa_test.");

            try (Statement statement = connection.createStatement();
                    ResultSet identity = statement.executeQuery(
                            "SELECT DATABASE(), CURRENT_USER(), @@port")) {
                require(identity.next()
                                && EXPECTED_DATABASE.equals(identity.getString(1))
                                && identity.getString(2) != null
                                && isLocalMysqlAccount(identity.getString(2))
                                && identity.getInt(3) == 3306,
                        "Fixture bootstrap live MySQL identity is not the approved local test database.");
            }

            verifySchemaScopedGrants(connection);
        }
    }

    private void verifySchemaScopedGrants(Connection connection) throws Exception {
        boolean foundGrant = false;
        try (Statement statement = connection.createStatement();
                ResultSet grants = statement.executeQuery("SHOW GRANTS FOR CURRENT_USER()")) {
            while (grants.next()) {
                foundGrant = true;
                String grant = grants.getString(1).replace("`", "").toUpperCase(Locale.ROOT);
                if (grant.startsWith("GRANT USAGE ON *.*")) {
                    continue;
                }
                require(grant.matches("(?s)^GRANT .* ON BINGO_QA_TEST\\.\\* .*")
                                && !grant.contains("GRANT OPTION"),
                        "Fixture bootstrap MySQL account has a global or cross-schema grant.");
            }
        }
        require(foundGrant, "Fixture bootstrap could not verify MySQL grants.");
    }

    private boolean isLocalMysqlAccount(String currentUser) {
        String[] parts = currentUser.split("@", 2);
        return parts.length == 2 && Set.of("localhost", "127.0.0.1").contains(parts[1]);
    }

    private void requireDisabled(String property) {
        require(!environment.getProperty(property, Boolean.class, true),
                "Fixture bootstrap requires disabled safety property: " + property);
    }

    private void validateSettings() {
        require(playerPin != null && playerPin.matches("\\d{6}"), "Player PIN configuration is invalid.");
        require(adminPassword != null && adminPassword.length() >= 20,
                "Admin password configuration is invalid.");
        require(adminEmail != null && adminEmail.matches("(?i)^[^@\\s]+@example\\.test$"),
                "Admin email must use the example.test domain.");
        requirePin(approvalCandidatePin);
        requirePin(pendingAgentPin);

        Set<String> normalizedPhones = Set.of(
                normalize(playerPhone),
                normalize(adminPhone),
                normalize(approvalCandidatePhone),
                normalize(pendingAgentPhone));
        require(normalizedPhones.size() == 4, "Android-test fixture phone numbers must be distinct.");
        require(adminEmail.equals(adminEmail.trim().toLowerCase(Locale.ROOT)),
                "Android-test Admin email must be normalized.");
    }

    private FixtureIdentity ensurePlayer() {
        String phone = normalize(playerPhone);
        User existing = users.findByNormalizedPhone(phone).orElse(null);
        if (existing != null) {
            require(existing.getRoles().equals(Set.of(UserRole.PLAYER))
                            && existing.getStatus() == AccountStatus.ACTIVE
                            && passwordEncoder.matches(playerPin, existing.getPasswordHash()),
                    "Existing Player identity conflicts with the configured fixture; no account was changed.");
            return new FixtureIdentity("player", existing.getId(), null, "ACTIVE", "reused");
        }

        authService.register(new RegisterRequest("QA Android Test Player", playerPhone, playerPin));
        User created = users.findByNormalizedPhone(phone)
                .orElseThrow(() -> new IllegalStateException("Player registration did not persist its account."));
        return new FixtureIdentity("player", created.getId(), null, "ACTIVE", "created");
    }

    private FixtureIdentity ensureAdmin() {
        String phone = normalize(adminPhone);
        User byPhone = users.findByNormalizedPhone(phone).orElse(null);
        User byEmail = users.findByEmail(adminEmail).orElse(null);
        require(byPhone == null || byEmail == null || byPhone.getId().equals(byEmail.getId()),
                "Existing Admin phone and email identify different accounts; no account was changed.");
        User existing = byPhone != null ? byPhone : byEmail;
        if (existing != null) {
            require(existing.getNormalizedPhone().equals(phone)
                            && adminEmail.equalsIgnoreCase(existing.getEmail())
                            && existing.getRoles().equals(Set.of(UserRole.ADMIN))
                            && existing.getStatus() == AccountStatus.ACTIVE
                            && existing.getAccountActivationTokenHash() == null
                            && passwordEncoder.matches(adminPassword, existing.getPasswordHash()),
                    "Existing Admin identity conflicts with the configured fixture; no account was changed.");
            return new FixtureIdentity("admin", existing.getId(), null, "ACTIVE", "reused");
        }

        User created = User.create(phone, "QA Android Test Admin", passwordEncoder.encode(adminPassword),
                AccountStatus.ACTIVE, Set.of(UserRole.ADMIN), Instant.now());
        created.setEmail(adminEmail);
        created = users.save(created);
        return new FixtureIdentity("admin", created.getId(), null, "ACTIVE", "created");
    }

    private FixtureIdentity ensureAgent(
            String rawPhone,
            String pin,
            String displayName,
            String documentSuffix,
            boolean mayAlreadyBeApproved) {
        String phone = normalize(rawPhone);
        User existing = users.findByNormalizedPhone(phone).orElse(null);
        if (existing != null) {
            Agent agent = agents.findByUserId(existing.getId()).orElse(null);
            boolean pending = existing.getStatus() == AccountStatus.PENDING
                    && agent != null && agent.getStatus() == AgentStatus.PENDING;
            boolean approved = mayAlreadyBeApproved
                    && existing.getStatus() == AccountStatus.ACTIVE
                    && agent != null && agent.getStatus() == AgentStatus.APPROVED;
            require(existing.getRoles().equals(Set.of(UserRole.AGENT))
                            && (pending || approved)
                            && existing.getAgentPinHash() != null
                            && agentPinEncoder.matches(pin, existing.getAgentPinHash()),
                    "Existing Agent identity conflicts with the configured fixture; no account was changed.");
            return new FixtureIdentity(
                    mayAlreadyBeApproved ? "approval-candidate" : "pending-agent",
                    existing.getId(),
                    agent.getId(),
                    agent.getStatus().name(),
                    "reused");
        }

        String documentNumber = "ANDROID-TEST-" + documentSuffix + "-" + phone.substring(phone.length() - 6);
        require(!agents.existsByIdDocumentTypeIgnoreCaseAndIdDocumentNumberIgnoreCase(
                        "QA_TEST", documentNumber),
                "Android-test synthetic Agent identity document is already in use.");
        agentService.registerApplication(new AgentApplicationRequest(
                displayName,
                rawPhone,
                pin,
                "QA Test City",
                "QA Test Territory",
                "QA_TEST",
                documentNumber,
                null,
                null));
        User created = users.findByNormalizedPhone(phone)
                .orElseThrow(() -> new IllegalStateException("Agent registration did not persist its account."));
        Agent agent = agents.findByUserId(created.getId())
                .orElseThrow(() -> new IllegalStateException("Agent registration did not persist its application."));
        require(created.getStatus() == AccountStatus.PENDING && agent.getStatus() == AgentStatus.PENDING,
                "Normal Agent registration did not create a pending application.");
        return new FixtureIdentity(
                mayAlreadyBeApproved ? "approval-candidate" : "pending-agent",
                created.getId(),
                agent.getId(),
                agent.getStatus().name(),
                "created");
    }

    private String normalize(String phone) {
        return phoneNormalizer.normalize(phone);
    }

    private static void requirePin(String pin) {
        require(pin != null && pin.matches("\\d{4,8}"), "Agent PIN configuration is invalid.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record FixtureIdentity(String purpose, Long userId, Long agentId, String status, String action) {
    }
}
