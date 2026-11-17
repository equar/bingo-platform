package com.bakaroo.bingo.backend.feature.auth.service;

import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import com.bakaroo.bingo.backend.common.exception.AccountSuspendedException;
import com.bakaroo.bingo.backend.common.exception.ConflictException;
import com.bakaroo.bingo.backend.common.exception.NotFoundException;
import com.bakaroo.bingo.backend.feature.auth.dto.AuthResponse;
import com.bakaroo.bingo.backend.feature.auth.dto.ChangePlayerPinRequest;
import com.bakaroo.bingo.backend.feature.auth.dto.LoginRequest;
import com.bakaroo.bingo.backend.feature.auth.dto.MeResponse;
import com.bakaroo.bingo.backend.feature.auth.dto.RegisterRequest;
import com.bakaroo.bingo.backend.feature.auth.dto.UpdateMyPreferencesRequest;
import com.bakaroo.bingo.backend.feature.auth.dto.UpdateMyProfileRequest;
import com.bakaroo.bingo.backend.feature.auth.dto.UserProfileResponse;
import com.bakaroo.bingo.backend.feature.wallet.service.SignupRewardService;
import com.bakaroo.bingo.backend.feature.wallet.service.WalletService;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserLanguages;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import java.sql.SQLException;
import java.util.Set;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EthiopianPhoneNormalizer phoneNormalizer;
    private final JwtService jwtService;
    private final WalletService walletService;
    private final SignupRewardService signupRewardService;
    private final AuthenticationAttemptThrottleService throttleService;

    public AuthService(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            EthiopianPhoneNormalizer phoneNormalizer,
            JwtService jwtService,
            WalletService walletService,
            SignupRewardService signupRewardService,
            AuthenticationAttemptThrottleService throttleService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.phoneNormalizer = phoneNormalizer;
        this.jwtService = jwtService;
        this.walletService = walletService;
        this.signupRewardService = signupRewardService;
        this.throttleService = throttleService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedPhone = phoneNormalizer.normalize(request.phoneNumber());
        if (userRepository.countByPhoneAndAnyRoleExceptUser(normalizedPhone, Set.of(UserRole.PLAYER), null) > 0) {
            throw new ConflictException("PHONE_ALREADY_REGISTERED", "That phone number is already registered");
        }

        User user = User.create(
                normalizedPhone,
                request.displayName().trim(),
                passwordEncoder.encode(request.password()),
                AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER),
                Instant.now());
        String requestedReferralCode = request.referralCode() == null ? null
                : request.referralCode().trim().toUpperCase(Locale.ROOT);
        User referrer = null;
        if (requestedReferralCode != null && !requestedReferralCode.isBlank()) {
            referrer = userRepository.findByReferralCodeIgnoreCase(requestedReferralCode)
                    .orElseThrow(() -> new BadRequestException("INVALID_REFERRAL_CODE", "Referral code was not found"));
            if (!referrer.hasRole(UserRole.PLAYER) || !referrer.isActive()
                    || normalizedPhone.equals(referrer.getNormalizedPhone())) {
                throw new BadRequestException("INVALID_REFERRAL_CODE", "Referral code must belong to another active player");
            }
        }
        user.setReferralDetails(User.newReferralCode(), referrer);

        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicatePhoneConstraint(exception)) {
                throw new ConflictException("PHONE_ALREADY_REGISTERED", "That phone number is already registered");
            }
            throw exception;
        }
        walletService.createWalletIfMissing(saved);
        signupRewardService.grantForNewPlayer(saved);
        if (referrer != null) signupRewardService.grantReferralReward(referrer, saved);
        String sessionId = jwtService.newPlayerSessionId();
        saved.startPlayerSession(sessionId, jwtService.tokenExpiresAt());
        userRepository.save(saved);
        String token = jwtService.generateToken(saved, sessionId);
        return new AuthResponse(token, "Bearer", jwtService.getExpirationSeconds(), toProfile(saved));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        return login(request, false);
    }

    @Transactional
    public AuthResponse replacePlayerSession(LoginRequest request) {
        return login(request, true);
    }

    private AuthResponse login(LoginRequest request, boolean replaceActiveSession) {
        String normalizedPhone = phoneNormalizer.normalize(request.phoneNumber());
        throttleService.requireAccountAllowed("player", normalizedPhone);
        var player = userRepository.findByNormalizedPhoneAndRole(normalizedPhone, UserRole.PLAYER);
        if (player.isEmpty()) {
            throttleService.recordAccountFailure("player", normalizedPhone);
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid credentials");
        }
        if (player.get().getStatus() == AccountStatus.SUSPENDED) {
            if (!passwordEncoder.matches(request.password(), player.get().getPasswordHash())) {
                throttleService.recordAccountFailure("player", normalizedPhone);
                throw new org.springframework.security.authentication.BadCredentialsException("Invalid credentials");
            }
            throttleService.clearAccountAttempts("player", normalizedPhone);
            throw new AccountSuspendedException();
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedPhone, request.password()));
        } catch (AuthenticationException exception) {
            throttleService.recordAccountFailure("player", normalizedPhone);
            throw new org.springframework.security.authentication.BadCredentialsException(
                    "Invalid credentials", exception);
        }

        BingoUserPrincipal principal = (BingoUserPrincipal) authentication.getPrincipal();
        User user = userRepository.findByIdForUpdate(principal.getUser().getId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User account not found"));
        Instant now = Instant.now();
        if (!replaceActiveSession
                && user.getActivePlayerSessionId() != null
                && user.getActivePlayerSessionExpiresAt() != null
                && user.getActivePlayerSessionExpiresAt().isAfter(now)) {
            throw new ConflictException("PLAYER_ALREADY_LOGGED_IN",
                    "This player account is already logged in on another device. Log out there first, then try again.");
        }
        String sessionId = jwtService.newPlayerSessionId();
        user.startPlayerSession(sessionId, jwtService.tokenExpiresAt());
        userRepository.save(user);
        throttleService.clearAccountAttempts("player", normalizedPhone);
        String token = jwtService.generateToken(user, sessionId);
        return new AuthResponse(token, "Bearer", jwtService.getExpirationSeconds(), toProfile(user));
    }

    @Transactional
    public void logout(Long userId, String sessionId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User account not found"));
        if (sessionId != null && sessionId.equals(user.getActivePlayerSessionId())) {
            user.clearPlayerSession();
        }
    }

    public String sessionId(String token) { return jwtService.extractPlayerSessionId(token); }

    public MeResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User account not found"));
        return new MeResponse(toProfile(user));
    }

    @Transactional
    public void changeMyPin(Long userId, ChangePlayerPinRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User account not found"));
        if (!user.hasRole(UserRole.PLAYER)) {
            throw new BadRequestException("PLAYER_ACCOUNT_REQUIRED", "A player account is required to change this PIN");
        }
        throttleService.requireAccountAllowed("player", user.getNormalizedPhone());
        if (!passwordEncoder.matches(request.currentPin(), user.getPasswordHash())) {
            throttleService.recordAccountFailure("player", user.getNormalizedPhone());
            throw new BadRequestException("INVALID_CURRENT_PIN", "Current PIN is incorrect");
        }
        if (passwordEncoder.matches(request.newPin(), user.getPasswordHash())) {
            throw new ConflictException("PIN_REUSE_NOT_ALLOWED", "Choose a PIN different from the current PIN");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPin()));
        userRepository.save(user);
        throttleService.clearAccountAttempts("player", user.getNormalizedPhone());
    }

    @Transactional
    public MeResponse updateMyPreferences(Long userId, UpdateMyPreferencesRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User account not found"));
        if (request.preferredLanguage() != null && !UserLanguages.isSupported(request.preferredLanguage())) {
            throw new BadRequestException("UNSUPPORTED_LANGUAGE",
                    "preferredLanguage must be one of: " + String.join(", ", UserLanguages.SUPPORTED_LANGUAGES));
        }
        if (request.preferredLanguage() != null) user.setPreferredLanguage(request.preferredLanguage());
        if (request.preferredCalendar() != null) user.setPreferredCalendar(request.preferredCalendar());
        return new MeResponse(toProfile(userRepository.save(user)));
    }

    @Transactional
    public AuthResponse updateMyProfile(Long userId, UpdateMyProfileRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User account not found"));
        String displayName = request.displayName().trim();
        if (displayName.isEmpty()) {
            throw new BadRequestException("INVALID_DISPLAY_NAME", "Display name is required");
        }
        String normalizedPhone = phoneNormalizer.normalize(request.phoneNumber());
        if (!normalizedPhone.equals(user.getNormalizedPhone())
                && userRepository.countByPhoneAndAnyRoleExceptUser(
                        normalizedPhone, user.getRoles(), userId) > 0) {
            throw new ConflictException("PHONE_ALREADY_REGISTERED", "That phone number is already registered");
        }
        user.setDisplayName(displayName);
        user.setNormalizedPhone(normalizedPhone);
        if (user.getActivePlayerSessionId() != null) {
            user.startPlayerSession(user.getActivePlayerSessionId(), jwtService.tokenExpiresAt());
        }
        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicatePhoneConstraint(exception)) {
                throw new ConflictException("PHONE_ALREADY_REGISTERED", "That phone number is already registered");
            }
            throw exception;
        }
        return new AuthResponse(jwtService.generateToken(saved, saved.getActivePlayerSessionId()), "Bearer", jwtService.getExpirationSeconds(),
                toProfile(saved));
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getNormalizedPhone(),
                user.getRoles(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.isMustChangePassword(),
                UserLanguages.resolve(user.getPreferredLanguage()),
                user.getReferralCode(),
                user.getPreferredCalendar() == null ? "AUTO" : user.getPreferredCalendar());
    }

    private boolean isDuplicatePhoneConstraint(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && "23000".equals(sqlException.getSQLState())
                    && sqlException.getMessage() != null
                    && (sqlException.getMessage().contains("uk_users_normalized_phone")
                            || sqlException.getMessage().contains("uk_user_roles_role_phone"))) {
                return true;
            }
        }
        return false;
    }
}
