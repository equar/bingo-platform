package com.bakaroo.bingo.backend.feature.wallet.service;

import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import com.bakaroo.bingo.backend.common.exception.ConflictException;
import com.bakaroo.bingo.backend.common.exception.NotFoundException;
import com.bakaroo.bingo.backend.common.money.MoneyValues;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.bakaroo.bingo.backend.feature.wallet.config.WalletProperties;
import com.bakaroo.bingo.backend.feature.wallet.dto.SignupRewardGrantResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.SignupRewardSettingsRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.SignupRewardSettingsResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.PromotionalRewardCampaignRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.PromotionalRewardCampaignResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.PromotionalRewardGrantRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.PromotionalRewardGrantResponse;
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
    private final UserRepository userRepository;

    public SignupRewardService(SignupRewardPolicyRepository policyRepository,
            SignupRewardGrantRepository grantRepository,
            WalletRepository walletRepository,
            WalletTransactionRepository walletTransactionRepository,
            WalletProperties walletProperties,
            UserRepository userRepository) {
        this.policyRepository = policyRepository;
        this.grantRepository = grantRepository;
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.walletProperties = walletProperties;
        this.userRepository = userRepository;
    }

    @Transactional
    public void grantForNewPlayer(User player) {
        if (!player.hasRole(UserRole.PLAYER) || !player.isActive()) {
            return;
        }

        Instant registeredAt = Optional.ofNullable(player.getCreatedAt()).orElseGet(Instant::now);
        Optional<SignupRewardPolicy> policyOptional = policyRepository
                .findAllByTriggerTypeOrderByCreatedAtDesc(SignupRewardPolicy.TriggerType.SIGNUP).stream()
                .filter(policy -> policy.isEnabled() && isActive(policy, registeredAt)).findFirst();
        if (policyOptional.isEmpty()) {
            return;
        }
        SignupRewardPolicy policy = policyOptional.get();
        Instant now = Instant.now();
        if (policy.getRewardAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (grantRepository.existsByTriggerTypeAndPlayerId(SignupRewardPolicy.TriggerType.SIGNUP, player.getId())) {
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
        SignupRewardPolicy policy = policyRepository
                .findFirstByTriggerTypeOrderByCreatedAtDesc(SignupRewardPolicy.TriggerType.SIGNUP)
                .orElseGet(() -> SignupRewardPolicy.create(false, BigDecimal.ZERO.setScale(2), "ETB", Instant.EPOCH,
                        null, null, null, Instant.now()));
        return toSettingsResponse(policy);
    }

    @Transactional(readOnly = true)
    public Page<SignupRewardSettingsResponse> settingsHistory(Pageable pageable) {
        return settingsHistory(null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<SignupRewardSettingsResponse> settingsHistory(String query, Boolean enabled, Pageable pageable) {
        String search = normalizeSearch(query);
        return policyRepository.searchHistory(search, enabled, parseAmount(search), pageable)
                .map(this::toSettingsResponse);
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
        requireAdmin(principal);
        SignupRewardPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new NotFoundException("SIGNUP_REWARD_POLICY_NOT_FOUND",
                        "Signup reward policy not found"));
        policy.setEnabled(enabled);
        policy.setChangedBy(principal.getUser());
        return toSettingsResponse(policy);
    }

    @Transactional(readOnly = true)
    public Page<SignupRewardGrantResponse> listGrants(Pageable pageable) {
        return grantRepository.findAllByOrderByAwardedAtDesc(pageable).map(this::toGrantResponse);
    }

    @Transactional(readOnly = true)
    public Page<SignupRewardGrantResponse> listGrants(String query, Pageable pageable) {
        String search = normalizeSearch(query);
        return grantRepository.search(search, parseAmount(search), pageable).map(this::toGrantResponse);
    }

    @Transactional(readOnly = true)
    public Page<PromotionalRewardCampaignResponse> listCampaigns(String query, Boolean enabled, Pageable pageable) {
        String search = normalizeSearch(query);
        return policyRepository.searchHistory(search, enabled, parseAmount(search), pageable).map(this::toCampaignResponse);
    }

    @Transactional
    public PromotionalRewardCampaignResponse createCampaign(BingoUserPrincipal principal,
            PromotionalRewardCampaignRequest request) {
        requireAdmin(principal);
        validateCampaign(request.rewardAmount(), request.currency(), request.effectiveFrom(), request.effectiveUntil());
        String name = request.name().trim();
        if (name.isEmpty()) throw new BadRequestException("INVALID_REWARD_CAMPAIGN_NAME", "Campaign name is required");
        SignupRewardPolicy campaign = policyRepository.save(SignupRewardPolicy.create(name,
                request.triggerType(), request.enabled(), MoneyValues.money(request.rewardAmount()),
                request.currency().trim().toUpperCase(Locale.ROOT), request.effectiveFrom(),
                request.effectiveUntil(), principal.getUser(), request.reason(), Instant.now()));
        return toCampaignResponse(campaign);
    }

    @Transactional
    public PromotionalRewardGrantResponse awardManualCampaign(BingoUserPrincipal principal,
            PromotionalRewardGrantRequest request) {
        requireAdmin(principal);
        SignupRewardPolicy campaign = policyRepository.findById(request.campaignId())
                .orElseThrow(() -> new NotFoundException("PROMOTIONAL_REWARD_CAMPAIGN_NOT_FOUND", "Reward campaign not found"));
        if (campaign.getTriggerType() != SignupRewardPolicy.TriggerType.MANUAL)
            throw new BadRequestException("PROMOTIONAL_REWARD_TRIGGER_MISMATCH", "Only manual campaigns can be awarded by an admin");
        Instant now = Instant.now();
        if (!campaign.isEnabled() || !isActive(campaign, now))
            throw new BadRequestException("PROMOTIONAL_REWARD_CAMPAIGN_INACTIVE", "Reward campaign is not active");
        User player = userRepository.findById(request.playerId())
                .orElseThrow(() -> new NotFoundException("PLAYER_NOT_FOUND", "Player not found"));
        if (!player.hasRole(UserRole.PLAYER) || !player.isActive())
            throw new BadRequestException("INVALID_REWARD_PLAYER", "An active player account is required");
        if (grantRepository.existsByPolicyIdAndPlayerId(campaign.getId(), player.getId()))
            throw new ConflictException("PROMOTIONAL_REWARD_ALREADY_GRANTED", "This campaign was already awarded to the player");
        Wallet wallet = walletRepository.findByOwnerIdForUpdate(player.getId()).orElseGet(() ->
                walletRepository.save(Wallet.create(player, campaign.getCurrency())));
        if (!campaign.getCurrency().equalsIgnoreCase(wallet.getCurrency()))
            throw new ConflictException("PROMOTIONAL_REWARD_CURRENCY_MISMATCH", "Campaign currency must match the player's wallet currency");
        BigDecimal amount = MoneyValues.money(campaign.getRewardAmount());
        BigDecimal before = wallet.getPromotionalBalance();
        BigDecimal after = MoneyValues.money(before.add(amount));
        wallet.setPromotionalBalance(after);
        walletRepository.save(wallet);
        String operationId = UUID.randomUUID().toString();
        SignupRewardGrant grant = grantRepository.save(SignupRewardGrant.create(player, campaign, operationId,
                amount, campaign.getCurrency(), now, principal.getUser()));
        walletTransactionRepository.save(WalletTransaction.create(wallet, operationId,
                WalletTransactionType.PROMOTIONAL_REWARD_CREDIT, amount,
                wallet.getAvailableBalance(), wallet.getAvailableBalance(), before, after,
                wallet.getCurrency(), "PROMOTIONAL_REWARD", grant.getId(), campaign.getReason(), null, now));
        return toPromotionalGrantResponse(grant);
    }

    @Transactional(readOnly = true)
    public Page<PromotionalRewardGrantResponse> listPromotionalGrants(String query, Pageable pageable) {
        String search = normalizeSearch(query);
        return (search == null ? grantRepository.findAllByOrderByAwardedAtDesc(pageable)
                : grantRepository.search(search, parseAmount(search), pageable)).map(this::toPromotionalGrantResponse);
    }

    private void requireAdmin(BingoUserPrincipal principal) {
        if (!principal.getUser().hasRole(UserRole.ADMIN))
            throw new BadRequestException("ADMIN_ROLE_REQUIRED", "This endpoint is available to admin accounts only");
    }

    private void validateCampaign(BigDecimal amount, String currency, Instant from, Instant until) {
        if (amount.signum() <= 0) throw new BadRequestException("INVALID_PROMOTIONAL_REWARD_AMOUNT", "Reward amount must be positive");
        if (until != null && !until.isAfter(from)) throw new BadRequestException("INVALID_PROMOTIONAL_REWARD_DATES", "Effective until must be later than effective from");
        if (!walletProperties.currency().equalsIgnoreCase(currency.trim()))
            throw new BadRequestException("UNSUPPORTED_PROMOTIONAL_REWARD_CURRENCY", "Campaign currency must match the configured wallet currency");
    }

    private PromotionalRewardCampaignResponse toCampaignResponse(SignupRewardPolicy campaign) {
        return new PromotionalRewardCampaignResponse(campaign.getId(), campaign.getName(), campaign.getTriggerType(),
                campaign.isEnabled(), campaign.getRewardAmount(), campaign.getCurrency(), campaign.getEffectiveFrom(),
                campaign.getEffectiveUntil(), campaign.getChangedBy() == null ? null : campaign.getChangedBy().getId(),
                campaign.getReason(), campaign.getCreatedAt(), campaign.getUpdatedAt(),
                campaign.isEnabled() && isActive(campaign, Instant.now()));
    }

    private PromotionalRewardGrantResponse toPromotionalGrantResponse(SignupRewardGrant grant) {
        return new PromotionalRewardGrantResponse(grant.getId(), grant.getPolicy().getName(),
                grant.getPolicy().getTriggerType(), grant.getPlayer().getId(), grant.getPlayer().getDisplayName(),
                grant.getPlayer().getNormalizedPhone(), grant.getPolicy().getId(),
                grant.getAwardedBy() == null ? null : grant.getAwardedBy().getId(), grant.getOperationId(),
                grant.getRewardAmount(), grant.getCurrency(), grant.getAwardedAt());
    }

    private String normalizeSearch(String query) {
        return query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
    }

    private BigDecimal parseAmount(String search) {
        if (search == null) {
            return null;
        }
        try {
            return new BigDecimal(search);
        } catch (NumberFormatException ignored) {
            return null;
        }
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
