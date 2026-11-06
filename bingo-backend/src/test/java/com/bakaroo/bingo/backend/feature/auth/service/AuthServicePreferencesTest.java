package com.bakaroo.bingo.backend.feature.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import com.bakaroo.bingo.backend.common.exception.NotFoundException;
import com.bakaroo.bingo.backend.config.JwtProperties;
import com.bakaroo.bingo.backend.feature.auth.dto.UpdateMyPreferencesRequest;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.bakaroo.bingo.backend.feature.wallet.service.SignupRewardService;
import com.bakaroo.bingo.backend.feature.wallet.service.WalletService;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServicePreferencesTest {

    UserRepository userRepository;
    AuthService service;
    User player;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new AuthService(
                userRepository,
                mock(PasswordEncoder.class),
                mock(AuthenticationManager.class),
                new EthiopianPhoneNormalizer(),
                new JwtService(new JwtProperties("01234567890123456789012345678901", 3600L)),
                mock(WalletService.class),
                mock(SignupRewardService.class),
                mock(AuthenticationAttemptThrottleService.class));

        player = User.create("251911000003", "Player One", "hash", AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER), Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(player, "id", 13L);
    }

    @Test
    void accountsWithoutStoredPreferenceResolveToEnglish() {
        when(userRepository.findById(13L)).thenReturn(Optional.of(player));

        var response = service.me(13L);

        assertThat(response.user().preferredLanguage()).isEqualTo("en");
    }

    @Test
    void savesSupportedPreferenceForTheRequestingUser() {
        when(userRepository.findById(13L)).thenReturn(Optional.of(player));
        when(userRepository.save(player)).thenReturn(player);

        var response = service.updateMyPreferences(13L, new UpdateMyPreferencesRequest("am"));

        assertThat(player.getPreferredLanguage()).isEqualTo("am");
        assertThat(response.user().preferredLanguage()).isEqualTo("am");
        verify(userRepository).save(player);
    }

    @Test
    void switchingBackToEnglishIsPersisted() {
        player.setPreferredLanguage("am");
        when(userRepository.findById(13L)).thenReturn(Optional.of(player));
        when(userRepository.save(player)).thenReturn(player);

        var response = service.updateMyPreferences(13L, new UpdateMyPreferencesRequest("en"));

        assertThat(player.getPreferredLanguage()).isEqualTo("en");
        assertThat(response.user().preferredLanguage()).isEqualTo("en");
    }

    @Test
    void rejectsUnsupportedLanguagesWithoutSaving() {
        when(userRepository.findById(13L)).thenReturn(Optional.of(player));

        assertThrows(BadRequestException.class,
                () -> service.updateMyPreferences(13L, new UpdateMyPreferencesRequest("fr")));

        assertThat(player.getPreferredLanguage()).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsUnknownUsers() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.updateMyPreferences(99L, new UpdateMyPreferencesRequest("am")));
    }
}
