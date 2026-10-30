package com.bakaroo.bingo.backend.feature.auth.security;

import com.bakaroo.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates only the explicitly requested initial production administrator. */
@Service
public class ProductionAdminBootstrapService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final EthiopianPhoneNormalizer phoneNormalizer;

    public ProductionAdminBootstrapService(UserRepository users, PasswordEncoder passwordEncoder,
            EthiopianPhoneNormalizer phoneNormalizer) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.phoneNormalizer = phoneNormalizer;
    }

    @Transactional
    public Result bootstrap(String rawPhone, String rawName, String rawEmail, String password) {
        String normalizedPhone = normalizePhone(rawPhone);
        String displayName = requireName(rawName);
        String email = normalizeEmail(rawEmail);
        requirePassword(password);

        if (users.findByNormalizedPhone(normalizedPhone).isPresent()
                || users.findByEmailIgnoreCase(email).isPresent()) {
            return Result.ALREADY_EXISTS;
        }

        User admin = User.create(normalizedPhone, displayName, passwordEncoder.encode(password),
                AccountStatus.ACTIVE, Set.of(UserRole.ADMIN), Instant.now());
        admin.setEmail(email);
        try {
            users.saveAndFlush(admin);
        } catch (DataIntegrityViolationException exception) {
            // Unique phone/email constraints settle concurrent bootstrap attempts. Do not expose
            // the persistence exception because providers may include bound credential values.
            throw new IllegalStateException(
                    "Production admin bootstrap could not claim the requested phone or email; "
                            + "verify the existing account before retrying.");
        }
        return Result.CREATED;
    }

    private String normalizePhone(String rawPhone) {
        requireConfigured("ADMIN_BOOTSTRAP_PHONE", rawPhone);
        try {
            return phoneNormalizer.normalize(rawPhone);
        } catch (BadRequestException exception) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_PHONE must be a valid Ethiopian mobile number.");
        }
    }

    private static String requireName(String rawName) {
        requireConfigured("ADMIN_BOOTSTRAP_NAME", rawName);
        String name = rawName.trim();
        if (name.length() > 120) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_NAME must be at most 120 characters.");
        }
        return name;
    }

    private static String normalizeEmail(String rawEmail) {
        requireConfigured("ADMIN_BOOTSTRAP_EMAIL", rawEmail);
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 255 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_EMAIL must be a valid email address of at most 255 characters.");
        }
        return email;
    }

    private static void requirePassword(String password) {
        requireConfigured("ADMIN_BOOTSTRAP_PASSWORD", password);
        int byteLength = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.length() < 16 || byteLength > 72 || password.indexOf('\n') >= 0 || password.indexOf('\r') >= 0) {
            throw new IllegalStateException(
                    "ADMIN_BOOTSTRAP_PASSWORD must be at least 16 characters, at most 72 UTF-8 bytes, and single-line.");
        }
    }

    private static void requireConfigured(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when ADMIN_BOOTSTRAP_ENABLED=true.");
        }
    }

    public enum Result {
        CREATED,
        ALREADY_EXISTS
    }
}
