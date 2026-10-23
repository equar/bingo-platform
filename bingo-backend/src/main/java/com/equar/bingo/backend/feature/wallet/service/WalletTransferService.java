package com.equar.bingo.backend.feature.wallet.service;

import com.equar.bingo.backend.common.exception.BadRequestException;
import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.common.exception.NotFoundException;
import com.equar.bingo.backend.common.idempotency.IdempotentResult;
import com.equar.bingo.backend.common.idempotency.IdempotencyService;
import com.equar.bingo.backend.common.money.MoneyValues;
import com.equar.bingo.backend.common.request.RequestHashService;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.wallet.config.WalletProperties;
import com.equar.bingo.backend.feature.wallet.dto.SignupRewardSettingsResponse;
import com.equar.bingo.backend.feature.wallet.dto.WalletRecipientLookupResponse;
import com.equar.bingo.backend.feature.wallet.dto.WalletTransferRequest;
import com.equar.bingo.backend.feature.wallet.dto.WalletTransferResponse;
import com.equar.bingo.backend.feature.wallet.dto.WalletTransferDetailResponse;
import com.equar.bingo.backend.feature.wallet.dto.WalletTransferLedgerEntryResponse;
import com.equar.bingo.backend.feature.wallet.dto.WalletTransferSettingsRequest;
import com.equar.bingo.backend.feature.wallet.dto.WalletTransferSettingsResponse;
import com.equar.bingo.backend.feature.wallet.entity.Wallet;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransaction;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransactionType;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransfer;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransferPolicy;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransferStatus;
import com.equar.bingo.backend.feature.wallet.repository.WalletRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletTransferPolicyRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletTransferRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletTransferService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletTransferRepository walletTransferRepository;
    private final WalletTransferPolicyRepository policyRepository;
    private final WalletProperties walletProperties;
    private final EthiopianPhoneNormalizer phoneNormalizer;
    private final IdempotencyService idempotencyService;
    private final RequestHashService requestHashService;
    private final ObjectMapper objectMapper;

    public WalletTransferService(UserRepository userRepository,
            WalletRepository walletRepository,
            WalletTransactionRepository walletTransactionRepository,
            WalletTransferRepository walletTransferRepository,
            WalletTransferPolicyRepository policyRepository,
            WalletProperties walletProperties,
            EthiopianPhoneNormalizer phoneNormalizer,
            IdempotencyService idempotencyService,
            RequestHashService requestHashService,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.walletTransferRepository = walletTransferRepository;
        this.policyRepository = policyRepository;
        this.walletProperties = walletProperties;
        this.phoneNormalizer = phoneNormalizer;
        this.idempotencyService = idempotencyService;
        this.requestHashService = requestHashService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public WalletRecipientLookupResponse lookupRecipient(String phoneNumber) {
        User recipient = findPlayerByPhone(phoneNumber);
        return new WalletRecipientLookupResponse(recipient.getId(), recipient.getDisplayName(),
                recipient.getNormalizedPhone());
    }

    @Transactional
    public IdempotentResult<WalletTransferResponse> transfer(BingoUserPrincipal principal,
            WalletTransferRequest request,
            String idempotencyKey) {
        User sender = requirePlayer(principal);
        String normalizedRecipientPhone = phoneNormalizer.normalize(request.recipientPhoneNumber());
        User recipient = findPlayerByPhone(normalizedRecipientPhone);
        if (recipient.getId().equals(sender.getId())) {
            throw new BadRequestException("SELF_TRANSFER_NOT_ALLOWED", "You cannot transfer to yourself");
        }

        WalletTransferPolicy policy = currentPolicy();
        if (!policy.isEnabled()) {
            throw new BadRequestException("TRANSFERS_DISABLED", "Wallet transfers are currently disabled");
        }
        Instant policyCheckTime = Instant.now();
        if (policyCheckTime.isBefore(policy.getEffectiveFrom())
                || (policy.getEffectiveUntil() != null
                        && !policyCheckTime.isBefore(policy.getEffectiveUntil()))) {
            throw new BadRequestException("TRANSFER_POLICY_INACTIVE",
                    "Wallet transfers are outside the configured policy effective period");
        }
        if (!walletProperties.currency().equalsIgnoreCase(request.currency())
                || !policy.getCurrency().equalsIgnoreCase(request.currency())) {
            throw new BadRequestException("UNSUPPORTED_CURRENCY", "Currency does not match wallet transfer policy");
        }

        BigDecimal amount = MoneyValues.money(request.amount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("INVALID_AMOUNT", "Transfer amount must be greater than zero");
        }
        if (amount.compareTo(policy.getMaxSingleTransferAmount()) > 0) {
            throw new BadRequestException("TRANSFER_LIMIT_EXCEEDED", "Transfer amount exceeds the configured limit");
        }

        String requestHash = requestHashService.hash("POST", "/api/v1/wallet/transfers", request);
        String operationId = UUID.randomUUID().toString();

        return idempotencyService.execute(sender.getId(), idempotencyKey, requestHash, operationId,
                200, WalletTransferResponse.class, () -> executeTransfer(sender, recipient, amount, request, policy,
                        operationId));
    }

    @Transactional(readOnly = true)
    public WalletTransferSettingsResponse currentSettings() {
        return toSettingsResponse(currentPolicy());
    }

    @Transactional(readOnly = true)
    public Page<WalletTransferSettingsResponse> settingsHistory(Pageable pageable) {
        return policyRepository.findAllByOrderByCreatedAtDescIdDesc(pageable).map(this::toSettingsResponse);
    }

    @Transactional
    public WalletTransferSettingsResponse saveSettings(BingoUserPrincipal principal,
            WalletTransferSettingsRequest request) {
        requireAdmin(principal);
        WalletTransferPolicy policy = policyRepository.save(WalletTransferPolicy.create(
                request.enabled(),
                MoneyValues.money(request.maxSingleTransferAmount()),
                MoneyValues.money(request.dailyOutgoingLimitAmount()),
                request.currency().toUpperCase(),
                request.effectiveFrom(),
                request.effectiveUntil(),
                principal.getUser(),
                request.reason(),
                Instant.now()));
        return toSettingsResponse(policy);
    }

    @Transactional(readOnly = true)
    public Page<WalletTransferResponse> searchTransfers(String query,
            WalletTransferStatus status,
            String senderPhone,
            String recipientPhone,
            Pageable pageable) {
        return walletTransferRepository.search(query, status, senderPhone, recipientPhone, pageable)
                .map(this::toTransferResponse);
    }

    @Transactional(readOnly = true)
    public WalletTransferDetailResponse getTransferDetail(Long transferId) {
        WalletTransfer transfer = walletTransferRepository.findById(transferId)
                .orElseThrow(() -> new NotFoundException("TRANSFER_NOT_FOUND", "Wallet transfer not found"));
        var ledgerEntries = walletTransactionRepository
                .findByReferenceTypeAndReferenceIdOrderByCreatedAtAscIdAsc("WALLET_TRANSFER", transferId)
                .stream()
                .map(transaction -> new WalletTransferLedgerEntryResponse(
                        transaction.getId(), transaction.getWallet().getId(),
                        transaction.getWallet().getOwner().getId(), transaction.getOperationId(),
                        transaction.getTransactionType().name(), transaction.getAmount(),
                        transaction.getBalanceBefore(), transaction.getBalanceAfter(), transaction.getCurrency(),
                        transaction.getReferenceType(), transaction.getReferenceId(), transaction.getCreatedAt()))
                .toList();
        return new WalletTransferDetailResponse(toTransferResponse(transfer), ledgerEntries);
    }

    private WalletTransferResponse executeTransfer(User sender,
            User recipient,
            BigDecimal amount,
            WalletTransferRequest request,
            WalletTransferPolicy policy,
            String operationId) {
        Wallet senderWallet = ensureWallet(sender);
        Wallet recipientWallet = ensureWallet(recipient);

        Instant now = Instant.now();
        if (policy.getDailyOutgoingLimitAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal sentSince = walletTransferRepository.sumSentAmountSince(sender.getId(), now.minusSeconds(86_400),
                    WalletTransferStatus.COMPLETED);
            if (sentSince.add(amount).compareTo(policy.getDailyOutgoingLimitAmount()) > 0) {
                throw new BadRequestException("DAILY_TRANSFER_LIMIT_EXCEEDED",
                        "You have exceeded the configured daily outgoing transfer limit");
            }
        }

        User first = sender.getId() <= recipient.getId() ? sender : recipient;
        User second = sender.getId() <= recipient.getId() ? recipient : sender;
        Wallet firstWallet = lockWalletForUpdate(first);
        Wallet secondWallet = lockWalletForUpdate(second);
        Wallet lockedSenderWallet = sender.getId() <= recipient.getId() ? firstWallet : secondWallet;
        Wallet lockedRecipientWallet = sender.getId() <= recipient.getId() ? secondWallet : firstWallet;

        BigDecimal senderBefore = lockedSenderWallet.getAvailableBalance();
        if (senderBefore.compareTo(amount) < 0) {
            throw new ConflictException("INSUFFICIENT_BALANCE", "Wallet balance cannot go below zero");
        }

        BigDecimal recipientBefore = lockedRecipientWallet.getAvailableBalance();
        BigDecimal senderPromotionalBalance = lockedSenderWallet.getPromotionalBalance();
        BigDecimal recipientPromotionalBalance = lockedRecipientWallet.getPromotionalBalance();
        BigDecimal senderAfter = MoneyValues.money(senderBefore.subtract(amount));
        BigDecimal recipientAfter = MoneyValues.money(recipientBefore.add(amount));

        lockedSenderWallet.setAvailableBalance(senderAfter);
        lockedRecipientWallet.setAvailableBalance(recipientAfter);
        walletRepository.save(lockedSenderWallet);
        walletRepository.save(lockedRecipientWallet);

        WalletTransfer transfer = WalletTransfer.create(sender, recipient, sender.getNormalizedPhone(),
                recipient.getNormalizedPhone(), operationId, amount, request.currency(), request.note(), senderBefore,
                senderAfter, recipientBefore, recipientAfter, now);
        transfer = walletTransferRepository.save(transfer);

        String metadataJson = writeMetadataJson(transfer, sender, recipient);

        walletTransactionRepository.save(WalletTransaction.create(
                lockedSenderWallet,
                operationId,
                WalletTransactionType.WALLET_TRANSFER_DEBIT,
                amount,
                senderBefore,
                senderAfter,
                senderPromotionalBalance,
                senderPromotionalBalance,
                lockedSenderWallet.getCurrency(),
                "WALLET_TRANSFER",
                transfer.getId(),
                request.note(),
                metadataJson,
                now));
        walletTransactionRepository.save(WalletTransaction.create(
                lockedRecipientWallet,
                operationId,
                WalletTransactionType.WALLET_TRANSFER_CREDIT,
                amount,
                recipientBefore,
                recipientAfter,
                recipientPromotionalBalance,
                recipientPromotionalBalance,
                lockedRecipientWallet.getCurrency(),
                "WALLET_TRANSFER",
                transfer.getId(),
                request.note(),
                metadataJson,
                now));

        return toTransferResponse(transfer);
    }

    private User findPlayerByPhone(String phoneNumber) {
        String normalizedPhone = phoneNormalizer.normalize(phoneNumber);
        User user = userRepository.findByNormalizedPhone(normalizedPhone)
                .orElseThrow(() -> new NotFoundException("PLAYER_NOT_FOUND", "Player account not found"));
        if (!user.hasRole(UserRole.PLAYER)) {
            throw new BadRequestException("NOT_A_PLAYER", "That phone number does not belong to a player account");
        }
        return user;
    }

    private WalletTransferPolicy currentPolicy() {
        return policyRepository.findFirstByOrderByCreatedAtDescIdDesc()
                .orElseGet(() -> WalletTransferPolicy.create(false, BigDecimal.ZERO.setScale(2),
                        BigDecimal.ZERO.setScale(2), walletProperties.currency(), Instant.EPOCH, null, null, null,
                        Instant.now()));
    }

    private Wallet ensureWallet(User owner) {
        return walletRepository.findByOwnerId(owner.getId()).orElseGet(() -> walletRepository.save(Wallet.create(owner,
                walletProperties.currency())));
    }

    private Wallet lockWalletForUpdate(User owner) {
        return walletRepository.findByOwnerIdForUpdate(owner.getId()).orElseGet(() -> ensureWallet(owner));
    }

    private User requirePlayer(BingoUserPrincipal principal) {
        User user = principal.getUser();
        if (!user.hasRole(UserRole.PLAYER)) {
            throw new BadRequestException("PLAYER_ROLE_REQUIRED", "This endpoint is available to player accounts only");
        }
        return user;
    }

    private void requireAdmin(BingoUserPrincipal principal) {
        if (!principal.getUser().hasRole(UserRole.ADMIN)) {
            throw new BadRequestException("ADMIN_ROLE_REQUIRED", "This endpoint is available to admin accounts only");
        }
    }

    private WalletTransferResponse toTransferResponse(WalletTransfer transfer) {
        return new WalletTransferResponse(transfer.getId(), transfer.getOperationId(), transfer.getSender().getId(),
                transfer.getSenderPhoneSnapshot(), transfer.getRecipient().getId(),
                transfer.getRecipientPhoneSnapshot(),
                transfer.getAmount(), transfer.getCurrency(), transfer.getNote(), transfer.getStatus(),
                transfer.getSenderBalanceBefore(), transfer.getSenderBalanceAfter(),
                transfer.getRecipientBalanceBefore(),
                transfer.getRecipientBalanceAfter(), transfer.getCreatedAt(), transfer.getCompletedAt(),
                transfer.getFailureReason());
    }

    private WalletTransferSettingsResponse toSettingsResponse(WalletTransferPolicy policy) {
        Instant now = Instant.now();
        return new WalletTransferSettingsResponse(policy.getId(), policy.isEnabled(),
                policy.getMaxSingleTransferAmount(), policy.getDailyOutgoingLimitAmount(), policy.getCurrency(),
                policy.getEffectiveFrom(), policy.getEffectiveUntil(),
                policy.getChangedBy() == null ? null : policy.getChangedBy().getId(), policy.getReason(),
                policy.getCreatedAt(), policy.getUpdatedAt(), policy.isEnabled()
                        && !now.isBefore(policy.getEffectiveFrom())
                        && (policy.getEffectiveUntil() == null || now.isBefore(policy.getEffectiveUntil())));
    }

    private String writeMetadataJson(WalletTransfer transfer, User sender, User recipient) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "transferId", transfer.getId(),
                    "senderId", sender.getId(),
                    "recipientId", recipient.getId(),
                    "senderPhone", sender.getNormalizedPhone(),
                    "recipientPhone", recipient.getNormalizedPhone()));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to write wallet transfer metadata", exception);
        }
    }
}
