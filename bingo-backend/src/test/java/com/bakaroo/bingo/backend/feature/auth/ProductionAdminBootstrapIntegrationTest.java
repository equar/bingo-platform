package com.bakaroo.bingo.backend.feature.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bakaroo.bingo.backend.MySqlIntegrationTest;
import com.bakaroo.bingo.backend.feature.auth.dto.AdminLoginRequest;
import com.bakaroo.bingo.backend.feature.auth.security.ProductionAdminBootstrapService;
import com.bakaroo.bingo.backend.feature.auth.service.AdminAuthService;
import com.bakaroo.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class ProductionAdminBootstrapIntegrationTest extends MySqlIntegrationTest {
    private static final String PHONE = uniquePhone();
    private static final String EMAIL = "production-bootstrap-" + UUID.randomUUID() + "@example.test";
    private static final String PASSWORD = "isolated-test-password-2026";

    @Autowired
    private ProductionAdminBootstrapService bootstrapService;
    @Autowired
    private AdminAuthService adminAuthService;
    @Autowired
    private EthiopianPhoneNormalizer phoneNormalizer;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private static String uniquePhone() {
        int suffix = new SecureRandom().nextInt(100_000_000);
        return "+2519" + String.format(Locale.ROOT, "%08d", suffix);
    }

    @Test
    @Transactional
    void createsOneAdminThatCanUseNormalAdminLogin() {
        assertEquals(ProductionAdminBootstrapService.Result.CREATED,
                bootstrapService.bootstrap(PHONE, "Bootstrap Integration Admin", EMAIL, PASSWORD));

        var user = users.findByEmail(EMAIL).orElseThrow();
        assertEquals(phoneNormalizer.normalize(PHONE), user.getNormalizedPhone());
        assertEquals(AccountStatus.ACTIVE, user.getStatus());
        assertEquals(java.util.Set.of(UserRole.ADMIN), user.getRoles());
        assertTrue(passwordEncoder.matches(PASSWORD, user.getPasswordHash()));
        assertTrue(adminAuthService.login(new AdminLoginRequest(EMAIL, PASSWORD)).accessToken().length() > 20);
        assertEquals(ProductionAdminBootstrapService.Result.ALREADY_EXISTS,
                bootstrapService.bootstrap(PHONE, "Changed Name", EMAIL, "different-password-2026"));
    }
}
