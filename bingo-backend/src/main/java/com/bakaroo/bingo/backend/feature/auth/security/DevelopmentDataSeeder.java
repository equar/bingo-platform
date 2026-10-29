package com.bakaroo.bingo.backend.feature.auth.security;

import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.bakaroo.bingo.backend.feature.wallet.service.WalletService;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DevelopmentDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordEncoder agentPinEncoder;
    private final WalletService walletService;
    private final boolean enabled;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminPhone;
    private final String adminName;
    private final String agentPhone;
    private final String agentPassword;
    private final String agentPin;
    private final String playerPhone;
    private final String playerPin;

    public DevelopmentDataSeeder(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Qualifier("agentPinEncoder") PasswordEncoder agentPinEncoder,
            WalletService walletService,
            @Value("${app.dev.seed-enabled:false}") boolean enabled,
            @Value("${DEV_ADMIN_EMAIL:}") String adminEmail,
            @Value("${DEV_ADMIN_PASSWORD:}") String adminPassword,
            @Value("${DEV_ADMIN_PHONE:}") String adminPhone,
            @Value("${DEV_ADMIN_NAME:}") String adminName,
            @Value("${DEV_AGENT_PHONE:}") String agentPhone,
            @Value("${DEV_AGENT_PASSWORD:}") String agentPassword,
            @Value("${DEV_AGENT_PIN:}") String agentPin,
            @Value("${DEV_PLAYER_PHONE:}") String playerPhone,
            @Value("${DEV_PLAYER_PIN:}") String playerPin) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.agentPinEncoder = agentPinEncoder;
        this.walletService = walletService;
        this.enabled = enabled;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminPhone = adminPhone;
        this.adminName = adminName;
        this.agentPhone = agentPhone;
        this.agentPassword = agentPassword;
        this.agentPin = agentPin;
        this.playerPhone = playerPhone;
        this.playerPin = playerPin;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        requireConfigured("DEV_ADMIN_EMAIL", adminEmail);
        requireConfigured("DEV_ADMIN_PASSWORD", adminPassword);
        requireConfigured("DEV_ADMIN_PHONE", adminPhone);
        requireConfigured("DEV_ADMIN_NAME", adminName);
        requireConfigured("DEV_AGENT_PHONE", agentPhone);
        requireConfigured("DEV_AGENT_PASSWORD", agentPassword);
        requireConfigured("DEV_AGENT_PIN", agentPin);
        requireConfigured("DEV_PLAYER_PHONE", playerPhone);
        requireConfigured("DEV_PLAYER_PIN", playerPin);

        Instant now = Instant.now();
        User admin = upsertAdmin(now);
        User agent = upsertAgent(now);
        User player = upsertPlayer(now);

        agent.setAgentPinHash(agentPinEncoder.encode(agentPin));
        userRepository.save(agent);

        walletService.createWalletIfMissing(player);
    }

    private User upsertAdmin(Instant now) {
        return userRepository.findByEmail(adminEmail)
                .or(() -> userRepository.findByNormalizedPhone(adminPhone))
                .map(existing -> {
                    existing.setNormalizedPhone(adminPhone);
                    existing.setEmail(adminEmail);
                    existing.setDisplayName(adminName);
                    existing.setPasswordHash(passwordEncoder.encode(adminPassword));
                    existing.setStatus(AccountStatus.ACTIVE);
                    existing.setRoles(new LinkedHashSet<>(Set.of(UserRole.ADMIN)));
                    return userRepository.save(existing);
                })
                .orElseGet(() -> {
                    User admin = User.create(
                            adminPhone,
                            adminName,
                            passwordEncoder.encode(adminPassword),
                            AccountStatus.ACTIVE,
                            Set.of(UserRole.ADMIN),
                            now);
                    admin.setEmail(adminEmail);
                    return userRepository.save(admin);
                });
    }

    private User upsertAgent(Instant now) {
        return userRepository.findByNormalizedPhone(agentPhone)
                .map(existing -> {
                    existing.setDisplayName("Dev Agent");
                    existing.setPasswordHash(passwordEncoder.encode(agentPassword));
                    existing.setStatus(AccountStatus.ACTIVE);
                    existing.setRoles(new LinkedHashSet<>(Set.of(UserRole.AGENT)));
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(User.create(
                        agentPhone,
                        "Dev Agent",
                        passwordEncoder.encode(agentPassword),
                        AccountStatus.ACTIVE,
                        Set.of(UserRole.AGENT),
                        now)));
    }

    private User upsertPlayer(Instant now) {
        return userRepository.findByNormalizedPhone(playerPhone)
                .map(existing -> {
                    existing.setDisplayName("Dev Player");
                    existing.setPasswordHash(passwordEncoder.encode(playerPin));
                    existing.setStatus(AccountStatus.ACTIVE);
                    existing.setRoles(new LinkedHashSet<>(Set.of(UserRole.PLAYER)));
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(User.create(
                        playerPhone,
                        "Dev Player",
                        passwordEncoder.encode(playerPin),
                        AccountStatus.ACTIVE,
                        Set.of(UserRole.PLAYER),
                        now)));
    }

    private void requireConfigured(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set when development seeding is enabled");
        }
    }
}