package com.equar.bingo.backend.feature.auth.service;

import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.common.exception.NotFoundException;
import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.feature.auth.dto.AdminUserCreateRequest;
import com.equar.bingo.backend.feature.auth.dto.AdminUserUpdateRequest;
import com.equar.bingo.backend.feature.auth.dto.UserSummaryResponse;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EthiopianPhoneNormalizer phoneNormalizer;
    private final AdminPasswordGenerator passwordGenerator;
    private final AdminAccountNotificationService notificationService;
    private final AdminAccountActivationTokenGenerator activationTokenGenerator;
    private final String activationUrl;

    public AdminUserService(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EthiopianPhoneNormalizer phoneNormalizer,
            AdminPasswordGenerator passwordGenerator,
            AdminAccountNotificationService notificationService,
            AdminAccountActivationTokenGenerator activationTokenGenerator,
            @Value("${app.admin-account-email.activation-url:http://localhost:5173/activate-account}") String activationUrl) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.phoneNormalizer = phoneNormalizer;
        this.passwordGenerator = passwordGenerator;
        this.notificationService = notificationService;
        this.activationTokenGenerator = activationTokenGenerator;
        this.activationUrl = activationUrl;
    }

    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> listUsers(Pageable pageable, String search, AccountStatus status) {
        return userRepository
                .findAdminUsers(List.of(UserRole.ADMIN, UserRole.SUPPORT), normalizeSearch(search), status, pageable)
                .map(this::toSummary);
    }

    @Transactional
    public UserSummaryResponse createUser(AdminUserCreateRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        String normalizedPhone = phoneNormalizer.normalize(request.phoneNumber());
        validateRoles(request.roles());
        ensureEmailAvailable(normalizedEmail, null);
        ensurePhoneAvailable(normalizedPhone, null);

        String unsharedPassword = passwordGenerator.generate();
        String activationToken = activationTokenGenerator.generateToken();
        Instant createdAt = Instant.now();

        User user = User.create(
                normalizedPhone,
                request.displayName().trim(),
                passwordEncoder.encode(unsharedPassword),
                request.status(),
                new LinkedHashSet<>(request.roles()),
                createdAt);
        user.setEmail(normalizedEmail);
        user.requireAccountActivation(
                activationTokenGenerator.hashToken(activationToken),
                createdAt.plus(Duration.ofHours(24)));
        User savedUser = userRepository.save(user);
        String activationLink = UriComponentsBuilder.fromUriString(activationUrl)
                .fragment("token=" + activationToken)
                .build()
                .toUriString();
        notificationService.sendActivationLink(savedUser, activationLink);
        return toSummary(savedUser);
    }

    @Transactional
    public UserSummaryResponse updateUser(Long userId, AdminUserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User account not found"));
        if (user.hasRole(UserRole.AGENT)) {
            throw new ConflictException("AGENT_LIFECYCLE_REQUIRED",
                    "Agent account status and role must be changed through the agent lifecycle workflow");
        }

        String normalizedEmail = normalizeEmail(request.email());
        String normalizedPhone = phoneNormalizer.normalize(request.phoneNumber());
        validateRoles(request.roles());
        ensureEmailAvailable(normalizedEmail, userId);
        ensurePhoneAvailable(normalizedPhone, userId);

        user.setDisplayName(request.displayName().trim());
        user.setEmail(normalizedEmail);
        user.setNormalizedPhone(normalizedPhone);
        user.setStatus(request.status());
        user.setRoles(new LinkedHashSet<>(request.roles()));

        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.completePasswordChange();
        }

        return toSummary(userRepository.save(user));
    }

    private void ensureEmailAvailable(String email, Long currentUserId) {
        userRepository.findByEmail(email).ifPresent(existing -> {
            if (currentUserId == null || !existing.getId().equals(currentUserId)) {
                throw new ConflictException("EMAIL_ALREADY_REGISTERED", "That email is already registered");
            }
        });
    }

    private void ensurePhoneAvailable(String normalizedPhone, Long currentUserId) {
        userRepository.findByNormalizedPhone(normalizedPhone).ifPresent(existing -> {
            if (currentUserId == null || !existing.getId().equals(currentUserId)) {
                throw new ConflictException("PHONE_ALREADY_REGISTERED", "That phone number is already registered");
            }
        });
    }

    private void validateRoles(Set<UserRole> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new ConflictException("USER_ROLE_REQUIRED", "At least one role is required");
        }

        boolean valid = roles.stream().allMatch(role -> role == UserRole.ADMIN || role == UserRole.SUPPORT);
        if (!valid) {
            throw new ConflictException("INVALID_USER_ROLE", "Only ADMIN and SUPPORT roles are allowed here");
        }
    }

    private String normalizeEmail(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new ConflictException("EMAIL_REQUIRED", "Email is required");
        }
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }

        String trimmed = search.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed.toLowerCase(Locale.ROOT);
    }

    private UserSummaryResponse toSummary(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getNormalizedPhone(),
                user.getRoles(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
