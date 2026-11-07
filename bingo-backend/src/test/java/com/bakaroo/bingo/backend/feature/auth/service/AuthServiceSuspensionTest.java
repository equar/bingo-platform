package com.bakaroo.bingo.backend.feature.auth.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.bakaroo.bingo.backend.common.exception.AccountSuspendedException;
import com.bakaroo.bingo.backend.feature.auth.dto.LoginRequest;
import com.bakaroo.bingo.backend.feature.user.entity.*;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.bakaroo.bingo.backend.feature.wallet.service.*;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceSuspensionTest {
    @Test
    void suspensionRequiresCorrectPinAndNeverIssuesToken() {
        var users = mock(UserRepository.class);
        var passwords = mock(PasswordEncoder.class);
        var authentication = mock(AuthenticationManager.class);
        var jwt = mock(JwtService.class);
        var throttle = mock(AuthenticationAttemptThrottleService.class);
        var service = new AuthService(users, passwords, authentication,
                new EthiopianPhoneNormalizer(), jwt, mock(WalletService.class),
                mock(SignupRewardService.class), throttle);
        var player = User.create("251911000004", "Player", "hash", AccountStatus.SUSPENDED,
                Set.of(UserRole.PLAYER), Instant.now());
        when(users.findByNormalizedPhoneAndRole("251911000004", UserRole.PLAYER))
                .thenReturn(Optional.of(player));
        when(passwords.matches("123456", "hash")).thenReturn(true);

        assertThrows(AccountSuspendedException.class,
                () -> service.login(new LoginRequest("0911000004", "123456")));
        verify(throttle).clearAccountAttempts("player", "251911000004");
        assertThrows(BadCredentialsException.class,
                () -> service.login(new LoginRequest("0911000004", "999999")));
        verify(throttle).recordAccountFailure("player", "251911000004");
        verifyNoInteractions(jwt, authentication);
    }
}
