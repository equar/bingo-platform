package com.equar.bingo.backend.feature.auth;

import com.equar.bingo.backend.MySqlIntegrationTest;
import com.equar.bingo.backend.feature.auth.dto.AdminLoginRequest;
import com.equar.bingo.backend.feature.auth.dto.AuthResponse;
import com.equar.bingo.backend.feature.auth.dto.ChangePasswordRequest;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.authentication.account-attempt-limit=20")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminTemporaryPasswordIntegrationTest extends MySqlIntegrationTest {

    private static final String TEMPORARY_PASSWORD = "Temporary-Admin-42!";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private String email;

    @BeforeEach
    void setUp() {
        String runId = java.util.UUID.randomUUID().toString().replace("-", "");
        long phoneSuffix = Math.floorMod(System.nanoTime(), 100_000_000L);
        email = "temporary-admin-" + runId + "@example.invalid";
        User user = User.create(
                String.format("2519%08d", phoneSuffix),
                "Temporary Admin",
                passwordEncoder.encode(TEMPORARY_PASSWORD),
                AccountStatus.ACTIVE,
                Set.of(UserRole.ADMIN),
                Instant.now());
        user.setEmail(email);
        user.requirePasswordChangeUntil(Instant.now().plus(Duration.ofHours(24)));
        userRepository.save(user);
    }

    @Test
    void temporaryPasswordAllowsOnlyPasswordChangeAndCannotBeReused() throws Exception {
        AuthResponse login = login(TEMPORARY_PASSWORD);
        assertTrue(login.user().mustChangePassword());

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        mockMvc.perform(put("/api/v1/admin/me/password")
                        .header("Authorization", "Bearer " + login.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest(TEMPORARY_PASSWORD, TEMPORARY_PASSWORD))))
                .andExpect(status().isConflict());

        String newPassword = "New-Admin-Password-42!";
        mockMvc.perform(put("/api/v1/admin/me/password")
                        .header("Authorization", "Bearer " + login.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest(TEMPORARY_PASSWORD, newPassword))))
                .andExpect(status().isOk());

        assertFalse(userRepository.findByEmail(email).orElseThrow().isMustChangePassword());
        mockMvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminLoginRequest(email, TEMPORARY_PASSWORD))))
                .andExpect(status().isUnauthorized());

        AuthResponse subsequentLogin = login(newPassword);
        assertFalse(subsequentLogin.user().mustChangePassword());
    }

    @Test
    void expiredTemporaryPasswordCannotBeUsed() throws Exception {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.requirePasswordChangeUntil(Instant.now().minusSeconds(1));
        userRepository.save(user);

        mockMvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminLoginRequest(email, TEMPORARY_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    private AuthResponse login(String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminLoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(response, AuthResponse.class);
    }
}
