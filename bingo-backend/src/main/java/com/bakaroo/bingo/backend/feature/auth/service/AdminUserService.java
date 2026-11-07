package com.bakaroo.bingo.backend.feature.auth.service;

import com.bakaroo.bingo.backend.common.exception.ConflictException;
import com.bakaroo.bingo.backend.common.exception.NotFoundException;
import com.bakaroo.bingo.backend.common.exception.ConflictException;
import com.bakaroo.bingo.backend.feature.auth.dto.AdminUserCreateRequest;
import com.bakaroo.bingo.backend.feature.auth.dto.AdminUserUpdateRequest;
import com.bakaroo.bingo.backend.feature.auth.dto.UserSummaryResponse;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EthiopianPhoneNormalizer phoneNormalizer;
    private final AdminPasswordGenerator passwordGenerator;
    private final AdminAccountNotificationService notificationService;
    private final AdminAccountActivationTokenGenerator activationTokenGenerator;
    private final String activationUrl;
    private final String resetPasswordUrl;

    @Autowired
    public AdminUserService(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EthiopianPhoneNormalizer phoneNormalizer,
            AdminPasswordGenerator passwordGenerator,
            AdminAccountNotificationService notificationService,
            AdminAccountActivationTokenGenerator activationTokenGenerator,
            @Value("${app.admin-account-email.activation-url:http://localhost:5173/activate-account}") String activationUrl,
            @Value("${app.admin-account-email.reset-password-url:http://localhost:5173/reset-password}") String resetPasswordUrl) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.phoneNormalizer = phoneNormalizer;
        this.passwordGenerator = passwordGenerator;
        this.notificationService = notificationService;
        this.activationTokenGenerator = activationTokenGenerator;
        this.activationUrl = activationUrl;
        this.resetPasswordUrl = resetPasswordUrl;
    }

    public AdminUserService(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EthiopianPhoneNormalizer phoneNormalizer,
            AdminPasswordGenerator passwordGenerator,
            AdminAccountNotificationService notificationService,
            AdminAccountActivationTokenGenerator activationTokenGenerator,
            String activationUrl) {
        this(userRepository, passwordEncoder, phoneNormalizer, passwordGenerator, notificationService,
                activationTokenGenerator, activationUrl, "http://localhost:5173/reset-password");
    }

    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> listUsers(Pageable pageable, String search, AccountStatus status) {
        return userRepository
                .findAdminUsers(List.of(UserRole.ADMIN, UserRole.SUPPORT), normalizeSearch(search), status, pageable)
                .map(this::toSummary);
    }

    @Transactional
    public UserSummaryResponse createUser(AdminUserCreateRequest request) {
        return createUser(request, null);
    }

    @Transactional
    public UserSummaryResponse createUser(AdminUserCreateRequest request, String actorDisplayName) {
        String normalizedEmail = normalizeEmail(request.email());
        String normalizedPhone = normalizeOptionalPhone(request.phoneNumber());
        validateRoles(request.roles());
        ensureEmailAvailable(normalizedEmail, null);
        if (normalizedPhone != null) {
            ensurePhoneAvailable(normalizedPhone, request.roles(), null);
        }

        String unsharedPassword = passwordGenerator.generate();
        String activationToken = activationTokenGenerator.generateToken();
        Instant createdAt = Instant.now();

        User user = User.create(
                normalizedPhone,
                request.displayName().trim(),
                passwordEncoder.encode(unsharedPassword),
                AccountStatus.PENDING,
                new LinkedHashSet<>(request.roles()),
                createdAt);
        user.setEmail(normalizedEmail);
        user.setCreatedByDisplayName(actorDisplayName);
        user.setUpdatedByDisplayName(actorDisplayName);
        user.requireAccountActivation(
                activationTokenGenerator.hashToken(activationToken),
                createdAt.plus(Duration.ofHours(24)));
        User savedUser = userRepository.save(user);
        queueActivationEmailAfterCommit(savedUser, buildActivationLink(activationToken));
        return toSummary(savedUser);
    }

    @Transactional
    public void resendActivation(Long userId) {
        resendActivation(userId, null);
    }

    @Transactional
    public void resendActivation(Long userId, Long actorUserId) {
        resendActivation(userId, actorUserId, null);
    }

    @Transactional
    public void resendActivation(Long userId, Long actorUserId, String actorDisplayName) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User account not found"));
        if (actorUserId != null && actorUserId.equals(user.getId())) {
            throw new ConflictException("SELF_ACTIVATION_RESEND_FORBIDDEN",
                    "You cannot resend an activation email to your own account");
        }
        if (user.getStatus() != AccountStatus.PENDING || user.getAccountActivationTokenHash() == null
                || !(user.hasRole(UserRole.ADMIN) || user.hasRole(UserRole.SUPPORT))) {
            throw new ConflictException("ACCOUNT_NOT_PENDING_ACTIVATION",
                    "An activation email can only be resent for a pending admin or support account");
        }

        Instant now = Instant.now();
        String activationToken = activationTokenGenerator.generateToken();
        if (actorDisplayName != null) {
            user.setUpdatedByDisplayName(actorDisplayName);
        }
        user.requireAccountActivation(activationTokenGenerator.hashToken(activationToken), now.plus(Duration.ofHours(24)));
        queueActivationEmailAfterCommit(user, buildActivationLink(activationToken));
    }

    @Transactional
    public void resetPassword(Long userId, Long actorUserId) {
        resetPassword(userId, actorUserId, null);
    }

    @Transactional
    public void resetPassword(Long userId, Long actorUserId, String actorDisplayName) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User account not found"));
        if (actorUserId != null && actorUserId.equals(user.getId())) {
            throw new ConflictException("SELF_PASSWORD_RESET_FORBIDDEN",
                    "You cannot send a password reset link to your own account");
        }
        if (user.getStatus() != AccountStatus.ACTIVE
                || !(user.hasRole(UserRole.ADMIN) || user.hasRole(UserRole.SUPPORT))) {
            throw new ConflictException("ACCOUNT_NOT_ACTIVE",
                    "A password reset link can only be sent for an active admin or support account");
        }

        Instant now = Instant.now();
        String resetToken = activationTokenGenerator.generateToken();
        if (actorDisplayName != null) {
            user.setUpdatedByDisplayName(actorDisplayName);
        }
        user.requirePasswordReset(activationTokenGenerator.hashToken(resetToken), now.plus(Duration.ofHours(24)));
        queuePasswordResetEmailAfterCommit(user, buildLink(resetPasswordUrl, resetToken));
    }

    @Transactional
    public UserSummaryResponse updateUser(Long userId, AdminUserUpdateRequest request) {
        return updateUser(userId, request, null);
    }

    @Transactional
    public UserSummaryResponse updateUser(Long userId, AdminUserUpdateRequest request, Long actorUserId) {
        return updateUser(userId, request, actorUserId, null);
    }

    @Transactional
    public UserSummaryResponse updateUser(Long userId, AdminUserUpdateRequest request, Long actorUserId,
            String actorDisplayName) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User account not found"));
        if (user.hasRole(UserRole.AGENT)) {
            throw new ConflictException("AGENT_LIFECYCLE_REQUIRED",
                    "Agent account status and role must be changed through the agent lifecycle workflow");
        }

        String normalizedEmail = normalizeEmail(request.email());
        String normalizedPhone = normalizeOptionalPhone(request.phoneNumber());
        if (!normalizedEmail.equals(user.getEmail())) {
            throw new ConflictException("ADMIN_EMAIL_IMMUTABLE", "Admin account email cannot be changed");
        }
        if (!request.roles().equals(user.getRoles())) {
            throw new ConflictException("ADMIN_ROLE_IMMUTABLE", "Admin account roles cannot be changed here");
        }
        if (actorUserId != null && actorUserId.equals(user.getId()) && request.status() != user.getStatus()) {
            throw new ConflictException("SELF_ACCOUNT_STATUS_CHANGE_FORBIDDEN",
                    "You cannot change your own account status");
        }
        if (user.getStatus() == AccountStatus.DISABLED && request.status() != AccountStatus.DISABLED) {
            throw new ConflictException("DISABLED_ACCOUNT_TERMINAL",
                    "A disabled account cannot be reactivated");
        }
        if ((user.getStatus() == AccountStatus.PENDING && request.status() != AccountStatus.PENDING)
                || (user.getStatus() != AccountStatus.PENDING && request.status() == AccountStatus.PENDING)) {
            throw new ConflictException("PENDING_STATUS_IMMUTABLE",
                    "Pending status is controlled by the account activation workflow");
        }
        if (normalizedPhone != null) {
            ensurePhoneAvailable(normalizedPhone, request.roles(), userId);
        }

        user.setDisplayName(request.displayName().trim());
        user.setNormalizedPhone(normalizedPhone);
        user.setStatus(request.status());
        if (actorDisplayName != null) {
            user.setUpdatedByDisplayName(actorDisplayName);
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

    private void ensurePhoneAvailable(String normalizedPhone, Set<UserRole> roles, Long currentUserId) {
        if (userRepository.countByPhoneAndAnyRoleExceptUser(normalizedPhone, roles, currentUserId) > 0) {
            throw new ConflictException("PHONE_ALREADY_REGISTERED", "That phone number is already registered for this role");
        }
    }

    private String normalizeOptionalPhone(String phoneNumber) {
        return phoneNumber == null || phoneNumber.isBlank() ? null : phoneNormalizer.normalize(phoneNumber);
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
                user.getUpdatedAt(),
                user.getCreatedByDisplayName(),
                user.getUpdatedByDisplayName());
    }

    private void queueActivationEmail(User user, String activationLink) {
        try {
            notificationService.sendActivationLink(user, activationLink);
        } catch (RuntimeException exception) {
            log.error("Unable to queue activation email for admin user id {}", user.getId(), exception);
        }
    }

    private void queueActivationEmailAfterCommit(User user, String activationLink) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    queueActivationEmail(user, activationLink);
                }
            });
        } else {
            queueActivationEmail(user, activationLink);
        }
    }

    private void queuePasswordResetEmailAfterCommit(User user, String resetLink) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        notificationService.sendPasswordResetLink(user, resetLink);
                    } catch (RuntimeException exception) {
                        log.error("Unable to queue password reset email for admin user id {}", user.getId(), exception);
                    }
                }
            });
        } else {
            try {
                notificationService.sendPasswordResetLink(user, resetLink);
            } catch (RuntimeException exception) {
                log.error("Unable to queue password reset email for admin user id {}", user.getId(), exception);
            }
        }
    }

    private String buildActivationLink(String activationToken) {
        return buildLink(activationUrl, activationToken);
    }

    private String buildLink(String baseUrl, String token) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .fragment("token=" + token)
                .build()
                .toUriString();
    }
}
