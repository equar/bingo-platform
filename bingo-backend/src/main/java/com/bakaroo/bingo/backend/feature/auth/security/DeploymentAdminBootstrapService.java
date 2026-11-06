package com.bakaroo.bingo.backend.feature.auth.security;

import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Each missing email is created in its own transaction; existing identities are never modified. */
@Service
public class DeploymentAdminBootstrapService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final String CONSONANTS = "bcdfghjkmnprstvwz";
    private static final String VOWELS = "aeiou";
    private final SecureRandom random = new SecureRandom();
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JavaMailSender mail;
    private final String from;
    private final String activationUrl;

    public DeploymentAdminBootstrapService(UserRepository users, PasswordEncoder encoder, JavaMailSender mail,
            @Value("${app.admin-account-email.from:${spring.mail.username:}}") String from,
            @Value("${app.admin-account-email.activation-url:http://localhost:5173/activate-account}") String activationUrl) {
        this.users = users;
        this.encoder = encoder;
        this.mail = mail;
        this.from = from;
        this.activationUrl = activationUrl;
    }

    @Transactional
    public boolean bootstrap(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            return false;
        }
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("Configure APP_EMAIL_FROM before enabling deployment admin bootstrap.");
        }
        String password = generatePassword();
        Instant now = Instant.now();
        User admin = User.create(null, email.substring(0, email.indexOf('@')), encoder.encode(password),
                AccountStatus.ACTIVE, Set.of(UserRole.ADMIN), now);
        admin.setEmail(email);
        admin.requirePasswordChangeUntil(now.plus(Duration.ofHours(24)));
        users.saveAndFlush(admin);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Your Bingo administrator account");
        String loginUrl = activationUrl.replaceFirst("/activate-account/?$", "");
        message.setText(String.join("\n", "Hello,", "", "Your Bingo administrator account is ready.",
                "Sign in: " + loginUrl, "Email: " + email, "Temporary password: " + password, "",
                "Sign in within 24 hours. You must choose a new password at first login.",
                "If this password expires, ask another administrator to send a password reset link."));
        try {
            // Synchronous delivery: failure rolls back this account so the next startup can retry.
            // Provider acceptance cannot be made atomic with the database commit; an exceptional commit
            // failure after acceptance can result in another email on the next attempt.
            mail.send(message);
        } catch (RuntimeException exception) {
            // Mail exceptions can contain the message, including its temporary password.
            throw new IllegalStateException("Deployment admin email delivery failed; account creation rolled back. Check email provider configuration.");
        }
        return true;
    }

    static String normalizeEmail(String value) {
        String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 255 || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Deployment admin bootstrap requires valid email addresses.");
        }
        return email;
    }

    String generatePassword() {
        StringBuilder password = new StringBuilder();
        for (int group = 0; group < 6; group++) {
            if (group > 0) password.append('-');
            for (int syllable = 0; syllable < 2; syllable++) {
                char consonant = CONSONANTS.charAt(random.nextInt(CONSONANTS.length()));
                password.append(syllable == 0 ? Character.toUpperCase(consonant) : consonant);
                password.append(VOWELS.charAt(random.nextInt(VOWELS.length())));
            }
        }
        password.append('-');
        for (int digit = 0; digit < 4; digit++) password.append(random.nextInt(8) + 2);
        return password.toString();
    }
}
