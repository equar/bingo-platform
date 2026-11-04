package com.bakaroo.bingo.backend.feature.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.bakaroo.bingo.backend.MySqlIntegrationTest;
import com.bakaroo.bingo.backend.feature.auth.service.JwtService;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Account-level language preference behavior for the self-service
 * {@code /api/v1/me/preferences} endpoint. Users are created with unique phone
 * numbers so existing rows in the shared disposable schema are preserved.
 */
@SpringBootTest(properties = { "CORS_ALLOWED_ORIGINS=http://localhost:5174" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserPreferenceIntegrationTest extends MySqlIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtService jwtService;

    User userWithoutPreference;
    User otherUser;
    String token;
    String otherToken;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        userWithoutPreference = userRepository.save(User.create(
                uniquePhone(), "Preference User", "hash", AccountStatus.ACTIVE, Set.of(UserRole.ADMIN), now));
        otherUser = userRepository.save(User.create(
                uniquePhone(), "Other User", "hash", AccountStatus.ACTIVE, Set.of(UserRole.ADMIN), now));
        token = jwtService.generateToken(userWithoutPreference);
        otherToken = jwtService.generateToken(otherUser);
    }

    private static String uniquePhone() {
        return "2519" + (10000000L + ThreadLocalRandom.current().nextLong(90000000L));
    }

    @Test
    void existingAccountsWithoutPreferenceResolveToEnglish() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.preferredLanguage").value("en"));
    }

    @Test
    void savingAmharicPersistsAndSwitchingBackToEnglishWorks() throws Exception {
        mockMvc.perform(put("/api/v1/me/preferences")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferredLanguage\":\"am\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.preferredLanguage").value("am"));

        // Persisted across a fresh authenticated read, as after refresh or re-login.
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.preferredLanguage").value("am"));
        assertThat(userRepository.findById(userWithoutPreference.getId()).orElseThrow()
                .getPreferredLanguage()).isEqualTo("am");

        mockMvc.perform(put("/api/v1/me/preferences")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferredLanguage\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.preferredLanguage").value("en"));
        assertThat(userRepository.findById(userWithoutPreference.getId()).orElseThrow()
                .getPreferredLanguage()).isEqualTo("en");
    }

    @Test
    void rejectsUnsupportedLanguageValues() throws Exception {
        mockMvc.perform(put("/api/v1/me/preferences")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferredLanguage\":\"fr\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/me/preferences")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferredLanguage\":\"\"}"))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findById(userWithoutPreference.getId()).orElseThrow()
                .getPreferredLanguage()).isNull();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/v1/me/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferredLanguage\":\"am\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersCanOnlyUpdateTheirOwnPreference() throws Exception {
        // The endpoint derives the account from the token; saving with one
        // account never touches another account's stored preference.
        mockMvc.perform(put("/api/v1/me/preferences")
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferredLanguage\":\"am\"}"))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(userWithoutPreference.getId()).orElseThrow()
                .getPreferredLanguage()).isNull();
        assertThat(userRepository.findById(otherUser.getId()).orElseThrow()
                .getPreferredLanguage()).isEqualTo("am");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.preferredLanguage").value("en"));
    }
}
