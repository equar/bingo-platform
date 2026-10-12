package com.equar.bingo.backend.feature.auth.security;

import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.wallet.service.WalletService;
import java.time.Instant;
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
        if (!enabled || userRepository.count() > 0) {
            return;
        }

        Instant now = Instant.now();

        userRepository.save(User.create(
                "251911000001",
                "Dev Admin",
                passwordEncoder.encode("DevAdmin123!"),
                AccountStatus.ACTIVE,
                Set.of(UserRole.ADMIN),
                now));

        userRepository.save(User.create(
                "251911000002",
                "Dev Agent",
                passwordEncoder.encode("DevAgent123!"),
                AccountStatus.ACTIVE,
                Set.of(UserRole.AGENT),
                now));

        User agent = userRepository.findByNormalizedPhone("251911000002").orElseThrow();
        agent.setAgentPinHash(agentPinEncoder.encode("2468"));
        userRepository.save(agent);

        userRepository.save(User.create(
                "251911000003",
                "Dev Player",
                passwordEncoder.encode("DevPlayer123!"),
                AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER),
                now));

        User player = userRepository.findByNormalizedPhone("251911000003").orElseThrow();
        walletService.createWalletIfMissing(player);
    }
}