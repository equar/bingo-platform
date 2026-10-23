package com.equar.bingo.backend.feature.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.common.exception.NotFoundException;
import com.equar.bingo.backend.feature.auth.dto.AdminUserCreateRequest;
import com.equar.bingo.backend.feature.auth.dto.AdminUserUpdateRequest;
import com.equar.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AdminUserServiceTest {

        UserRepository userRepository;

        PasswordEncoder passwordEncoder;

        AdminPasswordGenerator passwordGenerator;

        AdminAccountActivationTokenGenerator activationTokenGenerator;

        RecordingAdminAccountNotificationService notificationService;

        EthiopianPhoneNormalizer phoneNormalizer = new EthiopianPhoneNormalizer();

        AdminUserService service;

        @BeforeEach
        void setUp() {
                userRepository = mock(UserRepository.class);
                passwordEncoder = mock(PasswordEncoder.class);
                passwordGenerator = new AdminPasswordGenerator() {
                        @Override
                        public String generate() {
                                return "Secret123!";
                        }
                };
                activationTokenGenerator = new AdminAccountActivationTokenGenerator();
                notificationService = new RecordingAdminAccountNotificationService();
                service = new AdminUserService(userRepository, passwordEncoder, phoneNormalizer, passwordGenerator,
                                notificationService, activationTokenGenerator,
                                "http://localhost:5174/activate-account");
        }

        @Test
        void listUsersNormalizesSearchAndMapsUsers() {
                User admin = User.create("251911000001", "Admin User", "hash", AccountStatus.ACTIVE,
                                Set.of(UserRole.ADMIN), Instant.parse("2026-01-01T00:00:00Z"));
                ReflectionTestUtils.setField(admin, "id", 11L);
                admin.setEmail("admin@example.com");

                when(userRepository.findAdminUsers(eq(List.of(UserRole.ADMIN, UserRole.SUPPORT)), eq("support"),
                                eq(AccountStatus.ACTIVE), eq(PageRequest.of(1, 20))))
                                .thenReturn(new PageImpl<>(List.of(admin)));

                var result = service.listUsers(PageRequest.of(1, 20), "  Support  ", AccountStatus.ACTIVE);

                assertThat(result).hasSize(1);
                assertThat(result.getContent().get(0).id()).isEqualTo(11L);
                assertThat(result.getContent().get(0).displayName()).isEqualTo("Admin User");
                assertThat(result.getContent().get(0).email()).isEqualTo("admin@example.com");
                verify(userRepository).findAdminUsers(eq(List.of(UserRole.ADMIN, UserRole.SUPPORT)), eq("support"),
                                eq(AccountStatus.ACTIVE), eq(PageRequest.of(1, 20)));
        }

        @Test
        void createUserNormalizesInputsAndPersistsSummary() {
                when(passwordEncoder.encode("Secret123!")).thenReturn("encoded-password");

                User saved = User.create("251911000001", "New Admin", "encoded-password", AccountStatus.ACTIVE,
                                Set.of(UserRole.ADMIN, UserRole.SUPPORT), Instant.parse("2026-01-01T00:00:00Z"));
                ReflectionTestUtils.setField(saved, "id", 21L);
                saved.setEmail("admin@example.com");
                when(userRepository.save(any(User.class))).thenReturn(saved);

                var response = service.createUser(new AdminUserCreateRequest("  New Admin  ", "Admin@Example.com ",
                                "0911000001", AccountStatus.ACTIVE, Set.of(UserRole.ADMIN, UserRole.SUPPORT)));

                assertThat(response.id()).isEqualTo(21L);
                assertThat(response.displayName()).isEqualTo("New Admin");
                assertThat(response.email()).isEqualTo("admin@example.com");
                assertThat(response.phoneNumber()).isEqualTo("251911000001");

                ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
                verify(userRepository).save(userCaptor.capture());
                User createdUser = userCaptor.getValue();
                assertThat(createdUser.getDisplayName()).isEqualTo("New Admin");
                assertThat(createdUser.getEmail()).isEqualTo("admin@example.com");
                assertThat(createdUser.getPasswordHash()).isEqualTo("encoded-password");
                assertThat(createdUser.isMustChangePassword()).isTrue();
                assertThat(createdUser.getAccountActivationTokenHash()).matches("[a-f0-9]{64}");
                Duration timeUntilExpiry = Duration.between(Instant.now(), createdUser.getAccountActivationExpiresAt());
                assertThat(timeUntilExpiry).isBetween(Duration.ofHours(23).plusMinutes(59),
                                Duration.ofHours(24).plusSeconds(5));
                assertThat(notificationService.lastUser).isSameAs(saved);
                assertThat(notificationService.lastActivationLink).startsWith("http://localhost:5174/activate-account#token=");
                String rawToken = org.springframework.web.util.UriComponentsBuilder
                                .fromUriString(notificationService.lastActivationLink)
                                .build()
                                .getFragment()
                                .substring("token=".length());
                assertThat(activationTokenGenerator.hashToken(rawToken))
                                .isEqualTo(createdUser.getAccountActivationTokenHash());
        }

        @Test
        void createUserRejectsUnsupportedRoles() {
                assertThrows(ConflictException.class, () -> service.createUser(new AdminUserCreateRequest(
                                "Admin", "admin@example.com", "0911000002", AccountStatus.ACTIVE,
                                Set.of(UserRole.PLAYER))));
        }

        @Test
        void updateUserUpdatesExistingRecordAndKeepsPasswordWhenBlank() {
                User existing = User.create("251911000003", "Existing Admin", "old-password", AccountStatus.ACTIVE,
                                Set.of(UserRole.ADMIN), Instant.parse("2026-01-01T00:00:00Z"));
                ReflectionTestUtils.setField(existing, "id", 31L);
                existing.setEmail("existing@example.com");

                when(userRepository.findByIdForUpdate(31L)).thenReturn(Optional.of(existing));
                when(userRepository.save(existing)).thenReturn(existing);

                var response = service.updateUser(31L,
                                new AdminUserUpdateRequest(" Updated Admin ", "updated@example.com",
                                                "0911000004", "   ", AccountStatus.SUSPENDED,
                                                Set.of(UserRole.ADMIN, UserRole.SUPPORT)));

                assertThat(response.displayName()).isEqualTo("Updated Admin");
                assertThat(response.email()).isEqualTo("updated@example.com");
                assertThat(response.phoneNumber()).isEqualTo("251911000004");
                assertThat(existing.getPasswordHash()).isEqualTo("old-password");
                assertThat(existing.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
                assertThat(existing.getRoles()).containsExactlyInAnyOrder(UserRole.ADMIN, UserRole.SUPPORT);
        }

        @Test
        void updateUserThrowsWhenMissing() {
                when(userRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

                assertThrows(NotFoundException.class, () -> service.updateUser(99L, new AdminUserUpdateRequest(
                                "Admin", "admin@example.com", "0911000005", "Secret123!", AccountStatus.ACTIVE,
                                Set.of(UserRole.ADMIN))));
        }

        @Test
        void updateUserRejectsDuplicateEmailForAnotherAccount() {
                User existing = User.create("251911000006", "Existing Admin", "hash", AccountStatus.ACTIVE,
                                Set.of(UserRole.ADMIN), Instant.parse("2026-01-01T00:00:00Z"));
                ReflectionTestUtils.setField(existing, "id", 40L);
                existing.setEmail("existing@example.com");
                User other = User.create("251911000007", "Other Admin", "hash", AccountStatus.ACTIVE,
                                Set.of(UserRole.ADMIN), Instant.parse("2026-01-01T00:00:00Z"));
                ReflectionTestUtils.setField(other, "id", 41L);
                other.setEmail("taken@example.com");

                when(userRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(existing));
                when(userRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(other));

                assertThrows(ConflictException.class, () -> service.updateUser(40L, new AdminUserUpdateRequest(
                                "Admin", "taken@example.com", "0911000006", null, AccountStatus.ACTIVE,
                                Set.of(UserRole.ADMIN))));
        }

        private static final class RecordingAdminAccountNotificationService extends AdminAccountNotificationService {

                private User lastUser;
                private String lastActivationLink;

                private RecordingAdminAccountNotificationService() {
                        super(mock(JavaMailSender.class), "no-reply@example.com");
                }

                @Override
                public void sendActivationLink(User user, String activationLink) {
                        this.lastUser = user;
                        this.lastActivationLink = activationLink;
                }
        }
}
