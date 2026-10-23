package com.equar.bingo.backend.feature.auth.service;

import com.equar.bingo.backend.common.exception.NotFoundException;
import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.feature.auth.dto.AdminAccountActivationRequest;
import com.equar.bingo.backend.feature.auth.dto.ChangePasswordRequest;
import com.equar.bingo.backend.feature.auth.dto.AdminLoginRequest;
import com.equar.bingo.backend.feature.auth.dto.AuthResponse;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import java.util.Locale;
import java.time.Instant;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationAttemptThrottleService throttleService;
    private final AdminAccountActivationTokenGenerator activationTokenGenerator;

    public AdminAuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationAttemptThrottleService throttleService,
            AdminAccountActivationTokenGenerator activationTokenGenerator) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.throttleService = throttleService;
        this.activationTokenGenerator = activationTokenGenerator;
    }

    @Transactional
    public AuthResponse login(AdminLoginRequest request) {
        String email = normalizeEmail(request.email());
        throttleService.requireAccountAllowed("admin", email);
        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

            if (!(user.hasRole(UserRole.ADMIN) || user.hasRole(UserRole.SUPPORT))) {
                throw new BadCredentialsException("Invalid credentials");
            }

            if (!user.isActive()) {
                throw new BadCredentialsException("Invalid credentials");
            }

            if (user.getAccountActivationTokenHash() != null) {
                throw new BadCredentialsException("Invalid credentials");
            }

            if (user.isMustChangePassword() && user.isTemporaryPasswordExpired(Instant.now())) {
                throw new BadCredentialsException("Invalid credentials");
            }

            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new BadCredentialsException("Invalid credentials");
            }

            String token = jwtService.generateToken(user);
            return new AuthResponse(token, "Bearer", jwtService.getExpirationSeconds(), toProfile(user));
        } catch (BadCredentialsException exception) {
            throttleService.recordAccountFailure("admin", email);
            throw exception;
        }
    }

    @Transactional
    public void activateAccount(AdminAccountActivationRequest request) {
        String tokenHash = activationTokenGenerator.hashToken(request.token());
        Instant now = Instant.now();
        User user = userRepository.findByAccountActivationTokenHashForUpdate(tokenHash)
                .filter(candidate -> !candidate.isAccountActivationExpired(now))
                .filter(candidate -> candidate.isActive()
                        && (candidate.hasRole(UserRole.ADMIN) || candidate.hasRole(UserRole.SUPPORT)))
                .orElseThrow(() -> new BadCredentialsException("Activation link is invalid or expired"));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.completePasswordChange();
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User account not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ConflictException("PASSWORD_REUSE_NOT_ALLOWED", "Choose a password different from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.completePasswordChange();
        userRepository.save(user);
    }

    private String normalizeEmail(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    private com.equar.bingo.backend.feature.auth.dto.UserProfileResponse toProfile(User user) {
        return new com.equar.bingo.backend.feature.auth.dto.UserProfileResponse(
                user.getId(),
                user.getDisplayName(),
                user.getNormalizedPhone(),
                user.getRoles(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.isMustChangePassword());
    }
}
