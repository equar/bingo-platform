package com.equar.bingo.backend.feature.auth.service;

import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.common.exception.NotFoundException;
import com.equar.bingo.backend.feature.auth.dto.AuthResponse;
import com.equar.bingo.backend.feature.auth.dto.LoginRequest;
import com.equar.bingo.backend.feature.auth.dto.MeResponse;
import com.equar.bingo.backend.feature.auth.dto.RegisterRequest;
import com.equar.bingo.backend.feature.auth.dto.UserProfileResponse;
import com.equar.bingo.backend.feature.wallet.service.SignupRewardService;
import com.equar.bingo.backend.feature.wallet.service.WalletService;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import java.time.Instant;
import java.util.Set;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EthiopianPhoneNormalizer phoneNormalizer;
    private final JwtService jwtService;
    private final WalletService walletService;
    private final SignupRewardService signupRewardService;

    public AuthService(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            EthiopianPhoneNormalizer phoneNormalizer,
            JwtService jwtService,
            WalletService walletService,
            SignupRewardService signupRewardService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.phoneNormalizer = phoneNormalizer;
        this.jwtService = jwtService;
        this.walletService = walletService;
        this.signupRewardService = signupRewardService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedPhone = phoneNormalizer.normalize(request.phoneNumber());
        if (userRepository.existsByNormalizedPhone(normalizedPhone)) {
            throw new ConflictException("PHONE_ALREADY_REGISTERED", "That phone number is already registered");
        }

        User user = User.create(
                normalizedPhone,
                request.displayName().trim(),
                passwordEncoder.encode(request.password()),
                AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER),
                Instant.now());

        User saved = userRepository.save(user);
        walletService.createWalletIfMissing(saved);
        signupRewardService.grantForNewPlayer(saved);
        String token = jwtService.generateToken(saved);
        return new AuthResponse(token, "Bearer", jwtService.getExpirationSeconds(), toProfile(saved));
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedPhone = phoneNormalizer.normalize(request.phoneNumber());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedPhone, request.password()));

        BingoUserPrincipal principal = (BingoUserPrincipal) authentication.getPrincipal();
        User user = principal.getUser();
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, "Bearer", jwtService.getExpirationSeconds(), toProfile(user));
    }

    public MeResponse me(String normalizedPhone) {
        User user = userRepository.findByNormalizedPhone(normalizedPhone)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User account not found"));
        return new MeResponse(toProfile(user));
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getDisplayName(),
                user.getNormalizedPhone(),
                user.getRoles(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}