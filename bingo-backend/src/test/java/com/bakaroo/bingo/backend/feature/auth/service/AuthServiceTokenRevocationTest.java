package com.bakaroo.bingo.backend.feature.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bakaroo.bingo.backend.config.JwtProperties;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.bakaroo.bingo.backend.feature.wallet.service.SignupRewardService;
import com.bakaroo.bingo.backend.feature.wallet.service.WalletService;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTokenRevocationTest {

    @Test
    void logoutRevokesAnAdminTokenEvenWithoutAPlayerSession() {
        UserRepository users = mock(UserRepository.class);
        User admin = User.create("251911000007", "Admin", "hash", AccountStatus.ACTIVE,
                Set.of(UserRole.ADMIN), Instant.now());
        ReflectionTestUtils.setField(admin, "id", 7L);
        when(users.findByIdForUpdate(7L)).thenReturn(java.util.Optional.of(admin));
        JwtService jwt = new JwtService(new JwtProperties("01234567890123456789012345678901", 3600L));
        String alreadyIssuedAdminToken = jwt.generateToken(admin);
        AuthService service = service(users, jwt);

        service.logout(new BingoUserPrincipal(admin), null);

        assertThat(admin.getSecurityTokenVersion()).isEqualTo(1L);
        assertThat(jwt.isTokenValidForUser(alreadyIssuedAdminToken, admin.getId(),
                admin.getSecurityTokenVersion())).isFalse();
    }

    @Test
    void playerLogoutRevokesTokensAndClearsTheActivePlayerSession() {
        UserRepository users = mock(UserRepository.class);
        User player = User.create("251911000007", "Player", "hash", AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER), Instant.now());
        ReflectionTestUtils.setField(player, "id", 7L);
        player.startPlayerSession("session-7", Instant.now().plusSeconds(600));
        when(users.findByIdForUpdate(7L)).thenReturn(java.util.Optional.of(player));
        AuthService service = service(users, mock(JwtService.class));

        service.logout(new BingoUserPrincipal(player), "session-7");

        assertThat(player.getSecurityTokenVersion()).isEqualTo(1L);
        assertThat(player.getActivePlayerSessionId()).isNull();
    }

    private static AuthService service(UserRepository users, JwtService jwtService) {
        return new AuthService(users, mock(PasswordEncoder.class), mock(AuthenticationManager.class),
                new EthiopianPhoneNormalizer(), jwtService, mock(WalletService.class),
                mock(SignupRewardService.class), mock(AuthenticationAttemptThrottleService.class));
    }
}
