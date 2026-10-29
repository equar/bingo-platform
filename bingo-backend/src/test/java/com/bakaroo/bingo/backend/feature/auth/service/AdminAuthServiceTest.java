package com.bakaroo.bingo.backend.feature.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bakaroo.bingo.backend.config.JwtProperties;
import com.bakaroo.bingo.backend.common.exception.ConflictException;
import com.bakaroo.bingo.backend.feature.auth.dto.AdminAccountActivationRequest;
import com.bakaroo.bingo.backend.feature.auth.dto.ChangePasswordRequest;
import com.bakaroo.bingo.backend.feature.auth.dto.AdminLoginRequest;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AdminAuthServiceTest {

    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    JwtService jwtService;
    AdminAccountActivationTokenGenerator activationTokenGenerator;

    AdminAuthService service;

    User admin;
    User support;
    User player;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = new JwtService(new JwtProperties("01234567890123456789012345678901", 3600L));
        activationTokenGenerator = new AdminAccountActivationTokenGenerator();
        service = new AdminAuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                mock(AuthenticationAttemptThrottleService.class),
                activationTokenGenerator);

        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        admin = User.create("251911000001", "Admin One", "hash", AccountStatus.ACTIVE, Set.of(UserRole.ADMIN), now);
        admin.setEmail("admin@example.com");
        support = User.create("251911000002", "Support One", "hash", AccountStatus.ACTIVE, Set.of(UserRole.SUPPORT), now);
        player = User.create("251911000003", "Player One", "hash", AccountStatus.ACTIVE, Set.of(UserRole.PLAYER), now);
        ReflectionTestUtils.setField(admin, "id", 11L);
        ReflectionTestUtils.setField(support, "id", 12L);
        ReflectionTestUtils.setField(player, "id", 13L);
    }

    @Test
    void loginAcceptsAdminAndSupportAccounts() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Secret123!", admin.getPasswordHash())).thenReturn(true);

        var response = service.login(new AdminLoginRequest("  ADMIN@example.com  ", "Secret123!"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().id()).isEqualTo(11L);
    }

    @Test
    void loginAcceptsSupportAccountsToo() {
        when(userRepository.findByEmail("support@example.com")).thenReturn(Optional.of(support));
        when(passwordEncoder.matches("Secret123!", support.getPasswordHash())).thenReturn(true);

        assertThat(service.login(new AdminLoginRequest("support@example.com", "Secret123!")).user().id())
                .isEqualTo(12L);
    }

    @Test
    void loginRejectsNonAdminAccounts() {
        when(userRepository.findByEmail("player@example.com")).thenReturn(Optional.of(player));

        assertThrows(BadCredentialsException.class,
                () -> service.login(new AdminLoginRequest("player@example.com", "Secret123!")));
    }

    @Test
    void loginRejectsSuspendedAdminAccounts() {
        admin.setStatus(AccountStatus.SUSPENDED);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        assertThrows(BadCredentialsException.class,
                () -> service.login(new AdminLoginRequest("admin@example.com", "Secret123!")));
    }

    @Test
    void loginRejectsBadCredentialsAndBlankEmail() {
        assertThrows(BadCredentialsException.class, () -> service.login(new AdminLoginRequest("   ", "Secret123!")));

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> service.login(new AdminLoginRequest("admin@example.com", "WrongPass!")));
    }

    @Test
    void changePasswordValidatesCurrentPasswordAndPersistsNewHash() {
        when(userRepository.findById(11L)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("OldPass123!", admin.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("NewPass123!")).thenReturn("new-hash");
        admin.requirePasswordChangeUntil(Instant.now().plusSeconds(3600));

        service.changePassword(11L, new ChangePasswordRequest("OldPass123!", "NewPass123!"));

        assertThat(admin.getPasswordHash()).isEqualTo("new-hash");
        assertFalse(admin.isMustChangePassword());
        assertThat(admin.getTemporaryPasswordExpiresAt()).isNull();
        verify(userRepository).save(admin);
    }

    @Test
    void loginRejectsExpiredTemporaryPassword() {
        admin.requirePasswordChangeUntil(Instant.now().minusSeconds(1));
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        assertThrows(BadCredentialsException.class,
                () -> service.login(new AdminLoginRequest("admin@example.com", "Secret123!")));
    }

    @Test
    void loginRejectsAccountsAwaitingActivation() {
        admin.requireAccountActivation(activationTokenGenerator.hashToken("activation-token"),
                Instant.now().plusSeconds(3600));
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        assertThrows(BadCredentialsException.class,
                () -> service.login(new AdminLoginRequest("admin@example.com", "Secret123!")));
    }

    @Test
    void activationSetsPasswordAndConsumesTheSingleUseToken() {
        String rawToken = "activation-token";
        String tokenHash = activationTokenGenerator.hashToken(rawToken);
        admin.requireAccountActivation(tokenHash, Instant.now().plusSeconds(3600));
        when(userRepository.findByAccountActivationTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(admin));
        when(passwordEncoder.encode("NewPass123!")).thenReturn("new-hash");

        service.activateAccount(new AdminAccountActivationRequest(rawToken, "NewPass123!"));

        assertThat(admin.getPasswordHash()).isEqualTo("new-hash");
        assertFalse(admin.isMustChangePassword());
        assertThat(admin.getAccountActivationTokenHash()).isNull();
        assertThat(admin.getAccountActivationExpiresAt()).isNull();
        verify(userRepository).save(admin);
    }

    @Test
    void activationRejectsExpiredOrUnknownTokens() {
        String expiredToken = "expired-token";
        String expiredHash = activationTokenGenerator.hashToken(expiredToken);
        admin.requireAccountActivation(expiredHash, Instant.now().minusSeconds(1));
        when(userRepository.findByAccountActivationTokenHashForUpdate(expiredHash)).thenReturn(Optional.of(admin));

        assertThrows(BadCredentialsException.class,
                () -> service.activateAccount(new AdminAccountActivationRequest(expiredToken, "NewPass123!")));
        assertThrows(BadCredentialsException.class,
                () -> service.activateAccount(new AdminAccountActivationRequest("unknown-token", "NewPass123!")));
    }

    @Test
    void changePasswordRejectsReusingCurrentPassword() {
        when(userRepository.findById(11L)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("OldPass123!", admin.getPasswordHash())).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.changePassword(11L, new ChangePasswordRequest("OldPass123!", "OldPass123!")));
    }

    @Test
    void changePasswordRejectsIncorrectCurrentPassword() {
        when(userRepository.findById(11L)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("WrongPass!", admin.getPasswordHash())).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> service.changePassword(11L, new ChangePasswordRequest("WrongPass!", "NewPass123!")));
    }
}
