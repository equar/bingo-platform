package com.equar.bingo.backend.feature.auth;

import com.equar.bingo.backend.MySqlIntegrationTest;
import com.equar.bingo.backend.feature.auth.dto.AdminAccountActivationRequest;
import com.equar.bingo.backend.feature.auth.dto.AdminLoginRequest;
import com.equar.bingo.backend.feature.auth.service.JwtService;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.authentication.account-attempt-limit=20")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAccountActivationIntegrationTest extends MySqlIntegrationTest {

    private static final String RUN_ID = java.util.UUID.randomUUID().toString().replace("-", "");
    private static final long PHONE_SUFFIX = Math.floorMod(System.nanoTime(), 100_000_000L);
    private static final String OPERATOR_EMAIL = "qa-activation-operator-" + RUN_ID + "@example.invalid";
    private static final String NEW_ADMIN_EMAIL = "qa-activation-admin-" + RUN_ID + "@example.invalid";
    private static final String OPERATOR_PHONE = String.format("2519%08d", PHONE_SUFFIX);
    private static final String NEW_ADMIN_PHONE = String.format("09%08d", (PHONE_SUFFIX + 1) % 100_000_000L);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    JavaMailSender mailSender;

    private String operatorToken;
    private SimpleMailMessage activationEmail;

    @BeforeEach
    void setUp() {
        userRepository.findByEmail(OPERATOR_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(NEW_ADMIN_EMAIL).ifPresent(userRepository::delete);
        activationEmail = null;
        doAnswer(invocation -> {
            activationEmail = invocation.getArgument(0);
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        User operator = User.create(
                OPERATOR_PHONE,
                "QA Activation Operator",
                passwordEncoder.encode("Operator-Only-Password-42!"),
                AccountStatus.ACTIVE,
                Set.of(UserRole.ADMIN),
                Instant.now());
        operator.setEmail(OPERATOR_EMAIL);
        operatorToken = jwtService.generateToken(userRepository.save(operator));
    }

    @Test
    void createdAdminActivatesWithSingleUseLinkWithoutEmailingPassword() throws Exception {
        String createRequest = """
                {
                  "displayName": "QA Activation Admin",
                  "email": "%s",
                  "phoneNumber": "%s",
                  "status": "ACTIVE",
                  "roles": ["ADMIN"]
                }
                """.formatted(NEW_ADMIN_EMAIL, NEW_ADMIN_PHONE);

        String createdResponse = mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(NEW_ADMIN_EMAIL))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode createdUser = objectMapper.readTree(createdResponse);
        User storedUser = userRepository.findById(createdUser.path("id").asLong()).orElseThrow();
        assertTrue(storedUser.isMustChangePassword());
        assertNotNull(storedUser.getAccountActivationTokenHash());
        assertNotNull(storedUser.getAccountActivationExpiresAt());

        assertNotNull(activationEmail, "Admin creation should send the activation email");
        assertNotNull(activationEmail.getTo());
        assertTrue(Set.of(activationEmail.getTo()).contains(NEW_ADMIN_EMAIL));
        String emailText = activationEmail.getText();
        assertFalse(emailText.contains("Password:"));
        String activationLink = emailText.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("http://localhost:5173/activate-account#token="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Onboarding email did not include an activation link"));
        String activationToken = URI.create(activationLink).getFragment().substring("token=".length());
        String newPassword = "QA-New-Admin-Password-42!";

        mockMvc.perform(post("/api/v1/admin/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdminAccountActivationRequest(activationToken, newPassword))))
                .andExpect(status().isOk());

        User activatedUser = userRepository.findByEmail(NEW_ADMIN_EMAIL).orElseThrow();
        assertFalse(activatedUser.isMustChangePassword());
        assertNull(activatedUser.getAccountActivationTokenHash());
        assertNull(activatedUser.getAccountActivationExpiresAt());

        mockMvc.perform(post("/api/v1/admin/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdminAccountActivationRequest(activationToken, newPassword))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminLoginRequest(NEW_ADMIN_EMAIL, newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false));
    }
}
