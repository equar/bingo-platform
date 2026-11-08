package com.bakaroo.bingo.backend.feature.wallet.service;

import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import com.bakaroo.bingo.backend.common.exception.ConflictException;
import com.bakaroo.bingo.backend.common.exception.NotFoundException;
import com.bakaroo.bingo.backend.common.money.MoneyValues;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.wallet.config.WalletProperties;
import com.bakaroo.bingo.backend.feature.wallet.dto.SignupRewardGrantResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.SignupRewardSettingsRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.SignupRewardSettingsResponse;
import com.bakaroo.bingo.backend.feature.wallet.entity.SignupRewardGrant;
import com.bakaroo.bingo.backend.feature.wallet.entity.SignupRewardPolicy;
import com.bakaroo.bingo.backend.feature.wallet.entity.Wallet;
import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransaction;
import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransactionType;
import com.bakaroo.bingo.backend.feature.wallet.repository.SignupRewardGrantRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.SignupRewardPolicyRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignupRewardService {

    private final SignupRewardPolicyRepository policyRepository;
    private final SignupRewardGrantRepository grantRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletProperties walletProperties;

    public SignupRewardService(SignupRewardPolicyRepository policyRepository,
            SignupRewardGrantRepository grantRepository,
            WalletRepository walletRepository,
            WalletTransactionRepository walletTransactionRepository,
            WalletProperties walletProperties) {
        this.policyRepository = policyRepository;
        this.grantRepository = grantRepository;
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.walletProperties = walletProperties;
    }

    @Transactional
    public void grantForNewPlayer(User player) {
        if (!player.hasRole(UserRole.PLAYER) || !player.isActive()) {
            return;
        }

        Instant registeredAt = Optional.ofNullable(player.getCreatedAt()).orElseGet(Instant::now);
        Optional<SignupRewardPolicy> policyOptional = policyRepository.findAllByEnabledTrueOrderByCreatedAtDesc()
                .stream()
                .filter(policy -> isActive(policy, registeredAt))
                .filter(policy -> policy.getRewardAmount().compareTo(BigDecimal.ZERO) > 0)
                .findFirst();
        if (policyOptional.isEmpty()) {
            return;
        }
        SignupRewardPolicy policy = policyOptional.get();
        Instant now = Instant.now();
        if (policy.getRewardAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (grantRepository.existsByPlayerId(player.getId())) {
            return;
        }

        Wallet wallet = walletRepository.findByOwnerIdForUpdate(player.getId()).orElseGet(() -> {
            Wallet created = Wallet.create(player, policy.getCurrency());
            return walletRepository.save(created);
        });
        if (!policy.getCurrency().equalsIgnoreCase(wallet.getCurrency())) {
            throw new ConflictException("SIGNUP_REWARD_CURRENCY_MISMATCH",
                    "Signup reward currency must match the player's wallet currency");
        }

        BigDecimal rewardAmount = MoneyValues.money(policy.getRewardAmount());
        BigDecimal promotionalBefore = wallet.getPromotionalBalance();
        BigDecimal promotionalAfter = MoneyValues.money(promotionalBefore.add(rewardAmount));
        wallet.setPromotionalBalance(promotionalAfter);
        walletRepository.save(wallet);

        String operationId = UUID.randomUUID().toString();
        SignupRewardGrant grant = SignupRewardGrant.create(player, policy, operationId, rewardAmount,
                policy.getCurrency(), now);
        grantRepository.save(grant);

        walletTransactionRepository.save(WalletTransaction.create(
                wallet,
                operationId,
                WalletTransactionType.SIGNUP_REWARD_CREDIT,
                rewardAmount,
                wallet.getAvailableBalance(),
                wallet.getAvailableBalance(),
                promotionalBefore,
                promotionalAfter,
                wallet.getCurrency(),
                "SIGNUP_REWARD",
                grant.getId(),
                policy.getReason(),
                null,
                now));
    }

    @Transactional(readOnly = true)
    public SignupRewardSettingsResponse currentSettings() {
        SignupRewardPolicy policy = policyRepository.findFirstByOrderByCreatedAtDesc()
                .orElseGet(() -> SignupRewardPolicy.create(false, BigDecimal.ZERO.setScale(2), "ETB", Instant.EPOCH,
                        null, null, null, Instant.now()));
        return toSettingsResponse(policy);
    }

    @Transactional(readOnly = true)
    public Page<SignupRewardSettingsResponse> settingsHistory(Pageable pageable) {
        return policyRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toSettingsResponse);
    }

    @Transactional
    public SignupRewardSettingsResponse saveSettings(BingoUserPrincipal principal,
            SignupRewardSettingsRequest request) {
        if (!principal.getUser().hasRole(UserRole.ADMIN)) {
            throw new BadRequestException("ADMIN_ROLE_REQUIRED", "This endpoint is available to admin accounts only");
        }
        if (request.rewardAmount().signum() < 0) {
            throw new BadRequestException("INVALID_SIGNUP_REWARD_AMOUNT", "Signup reward amount cannot be negative");
        }
        if (request.effectiveUntil() != null
                && !request.effectiveUntil().isAfter(request.effectiveFrom())) {
            throw new BadRequestException("INVALID_SIGNUP_REWARD_DATES",
                    "Effective until must be later than effective from");
        }
        String currency = request.currency().trim().toUpperCase(Locale.ROOT);
        if (!walletProperties.currency().equalsIgnoreCase(currency)) {
            throw new BadRequestException("UNSUPPORTED_SIGNUP_REWARD_CURRENCY",
                    "Signup reward currency must match the configured wallet currency");
        }
        SignupRewardPolicy policy = policyRepository.save(SignupRewardPolicy.create(
                request.enabled(),
                MoneyValues.money(request.rewardAmount()),
                currency,
                request.effectiveFrom(),
                request.effectiveUntil(),
                principal.getUser(),
                request.reason(),
                Instant.now()));
        return toSettingsResponse(policy);
    }

    @Transactional
    public SignupRewardSettingsResponse setPolicyStatus(BingoUserPrincipal principal, Long policyId,
            boolean enabled) {
        SignupRewardPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new NotFoundException("SIGNUP_REWARD_POLICY_NOT_FOUND",
                        "Signup reward policy not found"));
        policy.setEnabled(enabled);
        policy.setChangedBy(principal.getUser());
        return toSettingsResponse(policy);
    }

    @Transactional(readOnly = true)
    public Page<SignupRewardGrantResponse> grants(Pageable pageable) {
        return grantRepository.findAllByOrderByAwardedAtDesc(pageable).map(this::toGrantResponse);
    }

    private boolean isActive(SignupRewardPolicy policy, Instant now) {
        return !now.isBefore(policy.getEffectiveFrom())
                && (policy.getEffectiveUntil() == null || now.isBefore(policy.getEffectiveUntil()));
    }

    private SignupRewardSettingsResponse toSettingsResponse(SignupRewardPolicy policy) {
        Instant now = Instant.now();
        return new SignupRewardSettingsResponse(
                policy.getId(),
                policy.isEnabled(),
                policy.getRewardAmount(),
                policy.getCurrency(),
                policy.getEffectiveFrom(),
                policy.getEffectiveUntil(),
                policy.getChangedBy() == null ? null : policy.getChangedBy().getId(),
                policy.getReason(),
                policy.getCreatedAt(),
                policy.getUpdatedAt(),
                policy.isEnabled() && isActive(policy, now));
    }

    private SignupRewardGrantResponse toGrantResponse(SignupRewardGrant grant) {
        return new SignupRewardGrantResponse(
                grant.getId(),
                grant.getPlayer().getId(),
                grant.getPlayer().getDisplayName(),
                grant.getPlayer().getNormalizedPhone(),
                grant.getPolicy().getId(),
                grant.getOperationId(),
                grant.getRewardAmount(),
                grant.getCurrency(),
                grant.getAwardedAt());
    }
}
