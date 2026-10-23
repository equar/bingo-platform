package com.equar.bingo.backend.feature.auth.security;

import com.equar.bingo.backend.feature.agent.dto.AgentProfileResponse;
import com.equar.bingo.backend.feature.agent.entity.Agent;
import com.equar.bingo.backend.feature.agent.entity.AgentStatus;
import com.equar.bingo.backend.feature.agent.repository.AgentRepository;
import com.equar.bingo.backend.feature.agent.service.AgentService;
import com.equar.bingo.backend.feature.auth.dto.RegisterRequest;
import com.equar.bingo.backend.feature.auth.service.AuthService;
import com.equar.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import java.sql.Connection;
import java.time.Instant;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** One-shot local QA bootstrap. Disabled by default and refuses any non-empty/non-QA database. */
@Component
@Profile("qa")
public class QaAccountBootstrapRunner implements ApplicationRunner {
    private final DataSource dataSource;
    private final UserRepository users;
    private final AgentRepository agents;
    private final PasswordEncoder passwordEncoder;
    private final PasswordEncoder agentPinEncoder;
    private final EthiopianPhoneNormalizer phoneNormalizer;
    private final AgentService agentService;
    private final AuthService authService;
    private final boolean enabled;
    private final String confirmation;
    private final String adminPhone;
    private final String adminPassword;
    private final String approvedAgentPhone;
    private final String approvedAgentPassword;
    private final String approvedAgentPin;
    private final String pendingAgentPhone;
    private final String pendingAgentPassword;
    private final String pendingAgentPin;
    private final String player001Phone;
    private final String player001Pin;
    private final String player002Phone;
    private final String player002Pin;

    public QaAccountBootstrapRunner(DataSource dataSource, UserRepository users, AgentRepository agents,
            PasswordEncoder passwordEncoder,
            @Qualifier("agentPinEncoder") PasswordEncoder agentPinEncoder,
            EthiopianPhoneNormalizer phoneNormalizer, AgentService agentService, AuthService authService,
            @Value("${app.qa.bootstrap.enabled:false}") boolean enabled,
            @Value("${BINGO_QA_BOOTSTRAP_CONFIRMATION:}") String confirmation,
            @Value("${BINGO_QA_ADMIN_PHONE:}") String adminPhone,
            @Value("${BINGO_QA_ADMIN_PASSWORD:}") String adminPassword,
            @Value("${BINGO_QA_APPROVED_AGENT_PHONE:}") String approvedAgentPhone,
            @Value("${BINGO_QA_APPROVED_AGENT_PASSWORD:}") String approvedAgentPassword,
            @Value("${BINGO_QA_APPROVED_AGENT_PIN:}") String approvedAgentPin,
            @Value("${BINGO_QA_PENDING_AGENT_PHONE:}") String pendingAgentPhone,
            @Value("${BINGO_QA_PENDING_AGENT_PASSWORD:}") String pendingAgentPassword,
            @Value("${BINGO_QA_PENDING_AGENT_PIN:}") String pendingAgentPin,
            @Value("${BINGO_QA_PLAYER_001_PHONE:}") String player001Phone,
            @Value("${BINGO_QA_PLAYER_001_PIN:}") String player001Pin,
            @Value("${BINGO_QA_PLAYER_002_PHONE:}") String player002Phone,
            @Value("${BINGO_QA_PLAYER_002_PIN:}") String player002Pin) {
        this.dataSource = dataSource;
        this.users = users;
        this.agents = agents;
        this.passwordEncoder = passwordEncoder;
        this.agentPinEncoder = agentPinEncoder;
        this.phoneNormalizer = phoneNormalizer;
        this.agentService = agentService;
        this.authService = authService;
        this.enabled = enabled;
        this.confirmation = confirmation;
        this.adminPhone = adminPhone;
        this.adminPassword = adminPassword;
        this.approvedAgentPhone = approvedAgentPhone;
        this.approvedAgentPassword = approvedAgentPassword;
        this.approvedAgentPin = approvedAgentPin;
        this.pendingAgentPhone = pendingAgentPhone;
        this.pendingAgentPassword = pendingAgentPassword;
        this.pendingAgentPin = pendingAgentPin;
        this.player001Phone = player001Phone;
        this.player001Pin = player001Pin;
        this.player002Phone = player002Phone;
        this.player002Pin = player002Pin;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) return;
        require(confirmation.equals("bingo_qa"), "BINGO_QA_BOOTSTRAP_CONFIRMATION must equal bingo_qa");
        try (Connection connection = dataSource.getConnection()) {
            require("bingo_qa".equals(connection.getCatalog()), "Refusing bootstrap outside bingo_qa");
        }
        require(users.count() == 0 && agents.count() == 0,
                "Refusing bootstrap: bingo_qa already contains users or agents; no rows were changed");
        String adminNormalized = phoneNormalizer.normalize(adminPhone);
        String approvedNormalized = phoneNormalizer.normalize(approvedAgentPhone);
        String pendingNormalized = phoneNormalizer.normalize(pendingAgentPhone);
        String player001Normalized = phoneNormalizer.normalize(player001Phone);
        String player002Normalized = phoneNormalizer.normalize(player002Phone);
        require(Set.of(adminNormalized, approvedNormalized, pendingNormalized, player001Normalized, player002Normalized)
                .size() == 5, "QA bootstrap phone numbers must all be distinct");
        require(adminPassword.length() >= 16 && approvedAgentPassword.length() >= 16
                && pendingAgentPassword.length() >= 16, "QA account passwords must be at least 16 characters");
        requirePin(approvedAgentPin);
        requirePin(pendingAgentPin);
        requirePin(player001Pin);
        requirePin(player002Pin);

        Instant now = Instant.now();
        User admin = users.save(User.create(adminNormalized, "QA Admin", passwordEncoder.encode(adminPassword),
                AccountStatus.ACTIVE, Set.of(UserRole.ADMIN), now));
        admin.setEmail("qa-admin@example.test");
        users.save(admin);

        User approvedAgent = createAgentUser(approvedNormalized, "QA Approved Agent", approvedAgentPassword,
                approvedAgentPin, now);
        agentService.adminApproveAgent(approvedAgent.getId(), profile(approvedAgent, AgentStatus.APPROVED,
                "QA District A", "QA Territory Approved", "QA", "QA-APPROVED-001"));

        User pendingAgent = createAgentUser(pendingNormalized, "QA Pending Agent", pendingAgentPassword,
                pendingAgentPin, now);
        agents.save(Agent.create(pendingAgent, AgentStatus.PENDING, "QA District B", "QA Territory Pending",
                "QA", "QA-PENDING-001", null, null));

        authService.register(new RegisterRequest("QA Player 001", player001Phone, player001Pin));
        authService.register(new RegisterRequest("QA Player 002", player002Phone, player002Pin));
    }

    private User createAgentUser(String phone, String name, String password, String pin, Instant now) {
        User user = User.create(phone, name, passwordEncoder.encode(password), AccountStatus.PENDING,
                Set.of(UserRole.AGENT), now);
        user.setAgentPinHash(agentPinEncoder.encode(pin));
        return users.save(user);
    }

    private AgentProfileResponse profile(User user, AgentStatus status, String city, String territory,
            String documentType, String documentNumber) {
        return new AgentProfileResponse(null, user.getId(), user.getNormalizedPhone(), user.getDisplayName(), status,
                city, territory, documentType, documentNumber, null, null, null, null);
    }

    private static void requirePin(String pin) {
        require(pin != null && pin.matches("\\d{6}"), "All QA player/agent PINs must be six digits");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
