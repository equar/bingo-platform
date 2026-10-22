package com.equar.bingo.backend.feature.auth;

import com.equar.bingo.backend.MySqlIntegrationTest;
import com.equar.bingo.backend.feature.auth.dto.AdminLoginRequest;
import com.equar.bingo.backend.feature.auth.dto.ChangePasswordRequest;
import com.equar.bingo.backend.feature.auth.service.JwtService;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.authentication.account-attempt-limit=20",
        "spring.mail.properties[mail.smtp.auth]=false",
        "spring.mail.properties[mail.smtp.starttls.enable]=false",
        "spring.mail.properties[mail.smtp.starttls.required]=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAccountMailpitIntegrationTest extends MySqlIntegrationTest {

    private static final String RUN_ID = java.util.UUID.randomUUID().toString().replace("-", "");
    private static final long PHONE_SUFFIX = Math.floorMod(System.nanoTime(), 100_000_000L);
    private static final String OPERATOR_EMAIL = "qa-mailpit-operator-" + RUN_ID + "@example.invalid";
    private static final String NEW_ADMIN_EMAIL = "qa-mailpit-admin-" + RUN_ID + "@example.invalid";
    private static final String OPERATOR_PHONE = String.format("2519%08d", PHONE_SUFFIX);
    private static final String NEW_ADMIN_PHONE = String.format("09%08d", (PHONE_SUFFIX + 1) % 100_000_000L);

    @DynamicPropertySource
    static void registerMailpitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", AdminAccountMailpitIntegrationTest::mailpitHost);
        registry.add("spring.mail.port", AdminAccountMailpitIntegrationTest::mailpitSmtpPort);
    }

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

    private String operatorToken;

    @BeforeEach
    void setUp() {
        userRepository.findByEmail(OPERATOR_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(NEW_ADMIN_EMAIL).ifPresent(userRepository::delete);

        User operator = User.create(
                OPERATOR_PHONE,
                "QA Mailpit Operator",
                passwordEncoder.encode("Operator-Only-Password-42!"),
                AccountStatus.ACTIVE,
                Set.of(UserRole.ADMIN),
                Instant.now());
        operator.setEmail(OPERATOR_EMAIL);
        operatorToken = jwtService.generateToken(userRepository.save(operator));
    }

    @Test
    void createdAdminReceivesTemporaryPasswordAndCanCompleteFirstLogin() throws Exception {
        String createRequest = """
                {
                  "displayName": "QA Mailpit Admin",
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
        assertNotNull(storedUser.getTemporaryPasswordExpiresAt());

        String emailText = awaitMailText(NEW_ADMIN_EMAIL);
        String temporaryPassword = emailText.lines()
                .filter(line -> line.startsWith("Password: "))
                .map(line -> line.substring("Password: ".length()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Onboarding email did not include the generated password"));

        String loginResponse = mockMvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminLoginRequest(
                                NEW_ADMIN_EMAIL,
                                temporaryPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String temporaryToken = objectMapper.readTree(loginResponse).path("accessToken").asText();
        String newPassword = "QA-New-Admin-Password-42!";

        mockMvc.perform(put("/api/v1/admin/me/password")
                        .header("Authorization", "Bearer " + temporaryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest(temporaryPassword, newPassword))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdminLoginRequest(NEW_ADMIN_EMAIL, temporaryPassword))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminLoginRequest(NEW_ADMIN_EMAIL, newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false));
    }

    private String awaitMailText(String recipient) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String apiBaseUrl = "http://" + mailpitHost() + ":" + mailpitHttpPort() + "/api/v1";
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();

        while (System.nanoTime() < deadline) {
            HttpResponse<String> listResponse = client.send(
                    HttpRequest.newBuilder(URI.create(apiBaseUrl + "/messages")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (listResponse.statusCode() != 200) {
                throw new AssertionError("Mailpit returned HTTP " + listResponse.statusCode());
            }

            JsonNode messages = objectMapper.readTree(listResponse.body()).path("messages");
            for (JsonNode message : messages) {
                boolean addressedToRecipient = false;
                for (JsonNode address : message.path("To")) {
                    if (recipient.equalsIgnoreCase(address.path("Address").asText())) {
                        addressedToRecipient = true;
                        break;
                    }
                }
                if (addressedToRecipient) {
                    String id = message.path("ID").asText();
                    HttpResponse<String> detailResponse = client.send(
                            HttpRequest.newBuilder(URI.create(apiBaseUrl + "/message/" + id)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                    if (detailResponse.statusCode() != 200) {
                        throw new AssertionError("Mailpit message lookup returned HTTP " + detailResponse.statusCode());
                    }
                    return objectMapper.readTree(detailResponse.body()).path("Text").asText();
                }
            }

            Thread.sleep(250);
        }

        throw new AssertionError("Mailpit did not receive an onboarding message for " + recipient);
    }

    private static String mailpitHost() {
        return System.getenv().getOrDefault("BINGO_TEST_MAILPIT_HOST", "127.0.0.1");
    }

    private static int mailpitSmtpPort() {
        return Integer.parseInt(System.getenv().getOrDefault("BINGO_TEST_MAILPIT_SMTP_PORT", "1025"));
    }

    private static int mailpitHttpPort() {
        return Integer.parseInt(System.getenv().getOrDefault("BINGO_TEST_MAILPIT_HTTP_PORT", "8025"));
    }
}
