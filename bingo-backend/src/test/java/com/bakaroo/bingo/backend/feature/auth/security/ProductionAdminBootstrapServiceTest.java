package com.bakaroo.bingo.backend.feature.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bakaroo.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class ProductionAdminBootstrapServiceTest {
    private static final String PHONE = "+251 911 234 567";
    private static final String EMAIL = "admin@example.com";
    private static final String NAME = "Production Admin";
    private static final String PASSWORD = "long-random-password-1234";

    @Mock
    private UserRepository users;
    @Mock
    private PasswordEncoder passwordEncoder;

    private ProductionAdminBootstrapService service;

    @BeforeEach
    void setUp() {
        service = new ProductionAdminBootstrapService(users, passwordEncoder, new EthiopianPhoneNormalizer());
    }

    @Test
    void createsOnlyAnActiveAdminAccount() {
        when(users.findByNormalizedPhone("251911234567")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn("bcrypt-hash");

        assertEquals(ProductionAdminBootstrapService.Result.CREATED,
                service.bootstrap(PHONE, NAME, EMAIL, PASSWORD));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        User created = captor.getValue();
        assertEquals("251911234567", created.getNormalizedPhone());
        assertEquals(NAME, created.getDisplayName());
        assertEquals("admin@example.com", created.getEmail());
        assertEquals("bcrypt-hash", created.getPasswordHash());
        assertEquals(AccountStatus.ACTIVE, created.getStatus());
        assertEquals(Set.of(UserRole.ADMIN), created.getRoles());
        assertNull(created.getAgentPinHash());
    }

    @Test
    void encodesPasswordWithTheExistingPasswordEncoder() {
        when(users.findByNormalizedPhone("251911234567")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded-with-configured-encoder");

        service.bootstrap(PHONE, NAME, EMAIL, PASSWORD);

        verify(passwordEncoder).encode(PASSWORD);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        assertEquals("encoded-with-configured-encoder", captor.getValue().getPasswordHash());
    }

    @Test
    void repeatedExecutionSkipsTheAccountWithoutChangingIt() {
        User existing = User.create("251911234567", "Existing Admin", "existing-hash", AccountStatus.PENDING,
                Set.of(UserRole.PLAYER), Instant.now());
        existing.setEmail(EMAIL);
        when(users.findByNormalizedPhone("251911234567")).thenReturn(Optional.empty(), Optional.of(existing));
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn("new-hash");

        assertEquals(ProductionAdminBootstrapService.Result.CREATED, service.bootstrap(PHONE, NAME, EMAIL, PASSWORD));
        assertEquals(ProductionAdminBootstrapService.Result.ALREADY_EXISTS,
                service.bootstrap(PHONE, NAME, EMAIL, PASSWORD));

        assertEquals("Existing Admin", existing.getDisplayName());
        assertEquals("existing-hash", existing.getPasswordHash());
        assertEquals(AccountStatus.PENDING, existing.getStatus());
        assertEquals(Set.of(UserRole.PLAYER), existing.getRoles());
        verify(users, org.mockito.Mockito.times(1)).saveAndFlush(any(User.class));
    }

    @Test
    void existingEmailIsNeverPromotedOrRewritten() {
        User existing = User.create("251977700001", "Existing Player", "existing-hash", AccountStatus.PENDING,
                Set.of(UserRole.PLAYER), Instant.now());
        existing.setEmail(EMAIL);
        when(users.findByNormalizedPhone("251911234567")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));

        assertEquals(ProductionAdminBootstrapService.Result.ALREADY_EXISTS,
                service.bootstrap(PHONE, NAME, EMAIL, PASSWORD));

        assertEquals("251977700001", existing.getNormalizedPhone());
        assertEquals("Existing Player", existing.getDisplayName());
        assertEquals("existing-hash", existing.getPasswordHash());
        assertEquals(AccountStatus.PENDING, existing.getStatus());
        assertEquals(Set.of(UserRole.PLAYER), existing.getRoles());
        verify(users, never()).saveAndFlush(any(User.class));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void existingPhoneIsNeverPromotedOrRewritten() {
        User existing = User.create("251911234567", "Existing Support", "existing-hash", AccountStatus.ACTIVE,
                Set.of(UserRole.SUPPORT), Instant.now());
        when(users.findByNormalizedPhone("251911234567")).thenReturn(Optional.of(existing));

        assertEquals(ProductionAdminBootstrapService.Result.ALREADY_EXISTS,
                service.bootstrap(PHONE, NAME, EMAIL, PASSWORD));

        assertEquals("Existing Support", existing.getDisplayName());
        assertEquals("existing-hash", existing.getPasswordHash());
        assertEquals(Set.of(UserRole.SUPPORT), existing.getRoles());
        verify(users, never()).saveAndFlush(any(User.class));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void missingRequiredConfigurationFailsWithoutPersistence() {
        IllegalStateException phoneFailure = assertThrows(IllegalStateException.class,
                () -> service.bootstrap("", NAME, EMAIL, PASSWORD));
        IllegalStateException nameFailure = assertThrows(IllegalStateException.class,
                () -> service.bootstrap(PHONE, " ", EMAIL, PASSWORD));
        IllegalStateException emailFailure = assertThrows(IllegalStateException.class,
                () -> service.bootstrap(PHONE, NAME, "", PASSWORD));
        IllegalStateException passwordFailure = assertThrows(IllegalStateException.class,
                () -> service.bootstrap(PHONE, NAME, EMAIL, ""));

        assertTrue(phoneFailure.getMessage().contains("ADMIN_BOOTSTRAP_PHONE"));
        assertTrue(nameFailure.getMessage().contains("ADMIN_BOOTSTRAP_NAME"));
        assertTrue(emailFailure.getMessage().contains("ADMIN_BOOTSTRAP_EMAIL"));
        assertTrue(passwordFailure.getMessage().contains("ADMIN_BOOTSTRAP_PASSWORD"));
        verify(users, never()).saveAndFlush(any(User.class));
    }

    @Test
    void invalidConfigurationFailsBeforePersistence() {
        IllegalStateException phoneFailure = assertThrows(IllegalStateException.class,
                () -> service.bootstrap("not-a-phone", NAME, EMAIL, PASSWORD));
        IllegalStateException emailFailure = assertThrows(IllegalStateException.class,
                () -> service.bootstrap(PHONE, NAME, "invalid-email", PASSWORD));
        IllegalStateException passwordFailure = assertThrows(IllegalStateException.class,
                () -> service.bootstrap(PHONE, NAME, EMAIL, "short"));

        assertTrue(phoneFailure.getMessage().contains("ADMIN_BOOTSTRAP_PHONE"));
        assertTrue(emailFailure.getMessage().contains("ADMIN_BOOTSTRAP_EMAIL"));
        assertTrue(passwordFailure.getMessage().contains("ADMIN_BOOTSTRAP_PASSWORD"));
        verify(users, never()).saveAndFlush(any(User.class));
    }
}
