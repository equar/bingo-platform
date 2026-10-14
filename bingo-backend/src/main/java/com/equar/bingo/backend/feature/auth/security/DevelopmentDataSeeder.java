package com.equar.bingo.backend.feature.auth.security;

import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.wallet.service.WalletService;
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

    private static final String DEV_ADMIN_EMAIL = "kirshipay+admin@gmail.com";
    private static final String DEV_ADMIN_PASSWORD = "admin@123";
    private static final String DEV_ADMIN_PHONE = "251911000001";
    private static final String DEV_ADMIN_NAME = "Dev Admin";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordEncoder agentPinEncoder;
    private final WalletService walletService;
    private final boolean enabled;

    public DevelopmentDataSeeder(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Qualifier("agentPinEncoder") PasswordEncoder agentPinEncoder,
            WalletService walletService,
            @Value("${app.dev.seed-enabled:true}") boolean enabled) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.agentPinEncoder = agentPinEncoder;
        this.walletService = walletService;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            return;
        }

        Instant now = Instant.now();
        User admin = upsertAdmin(now);
        User agent = upsertAgent(now);
        User player = upsertPlayer(now);

        agent.setAgentPinHash(agentPinEncoder.encode("2468"));
        userRepository.save(agent);

        walletService.createWalletIfMissing(player);
    }

    private User upsertAdmin(Instant now) {
        return userRepository.findByEmail(DEV_ADMIN_EMAIL)
                .or(() -> userRepository.findByNormalizedPhone(DEV_ADMIN_PHONE))
                .map(existing -> {
                    existing.setNormalizedPhone(DEV_ADMIN_PHONE);
                    existing.setEmail(DEV_ADMIN_EMAIL);
                    existing.setDisplayName(DEV_ADMIN_NAME);
                    existing.setPasswordHash(passwordEncoder.encode(DEV_ADMIN_PASSWORD));
                    existing.setStatus(AccountStatus.ACTIVE);
                    existing.setRoles(new LinkedHashSet<>(Set.of(UserRole.ADMIN)));
                    return userRepository.save(existing);
                })
                .orElseGet(() -> {
                    User admin = User.create(
                            DEV_ADMIN_PHONE,
                            DEV_ADMIN_NAME,
                            passwordEncoder.encode(DEV_ADMIN_PASSWORD),
                            AccountStatus.ACTIVE,
                            Set.of(UserRole.ADMIN),
                            now);
                    admin.setEmail(DEV_ADMIN_EMAIL);
                    return userRepository.save(admin);
                });
    }

    private User upsertAgent(Instant now) {
        return userRepository.findByNormalizedPhone("251911000002")
                .map(existing -> {
                    existing.setDisplayName("Dev Agent");
                    existing.setPasswordHash(passwordEncoder.encode("DevAgent123!"));
                    existing.setStatus(AccountStatus.ACTIVE);
                    existing.setRoles(new LinkedHashSet<>(Set.of(UserRole.AGENT)));
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(User.create(
                        "251911000002",
                        "Dev Agent",
                        passwordEncoder.encode("DevAgent123!"),
                        AccountStatus.ACTIVE,
                        Set.of(UserRole.AGENT),
                        now)));
    }

    private User upsertPlayer(Instant now) {
        return userRepository.findByNormalizedPhone("251911000003")
                .map(existing -> {
                    existing.setDisplayName("Dev Player");
                    existing.setPasswordHash(passwordEncoder.encode("DevPlayer123!"));
                    existing.setStatus(AccountStatus.ACTIVE);
                    existing.setRoles(new LinkedHashSet<>(Set.of(UserRole.PLAYER)));
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(User.create(
                        "251911000003",
                        "Dev Player",
                        passwordEncoder.encode("DevPlayer123!"),
                        AccountStatus.ACTIVE,
                        Set.of(UserRole.PLAYER),
                        now)));
    }
}