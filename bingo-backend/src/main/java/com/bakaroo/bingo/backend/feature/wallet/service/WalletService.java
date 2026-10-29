package com.bakaroo.bingo.backend.feature.wallet.service;

import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import com.bakaroo.bingo.backend.common.exception.ConflictException;
import com.bakaroo.bingo.backend.common.exception.NotFoundException;
import com.bakaroo.bingo.backend.common.idempotency.IdempotentResult;
import com.bakaroo.bingo.backend.common.idempotency.IdempotencyService;
import com.bakaroo.bingo.backend.common.money.MoneyValues;
import com.bakaroo.bingo.backend.common.request.RequestHashService;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.bakaroo.bingo.backend.feature.wallet.config.WalletProperties;
import com.bakaroo.bingo.backend.feature.wallet.dto.DepositClaimRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.DepositClaimResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.DepositReviewRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.PaymentInstructionsResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletAdjustmentRequest;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletAdjustmentResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletBalanceResponse;
import com.bakaroo.bingo.backend.feature.wallet.dto.WalletTransactionResponse;
import com.bakaroo.bingo.backend.feature.wallet.entity.DepositClaim;
import com.bakaroo.bingo.backend.feature.wallet.entity.DepositClaimStatus;
import com.bakaroo.bingo.backend.feature.wallet.entity.Wallet;
import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransaction;
import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransactionType;
import com.bakaroo.bingo.backend.feature.wallet.payment.PaymentProvider;
import com.bakaroo.bingo.backend.feature.wallet.repository.DepositClaimRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final DepositClaimRepository depositClaimRepository;
    private final UserRepository userRepository;
    private final WalletProperties walletProperties;
    private final PaymentProvider paymentProvider;
    private final TelebirrReferenceNormalizer telebirrReferenceNormalizer;
    private final IdempotencyService idempotencyService;
    private final RequestHashService requestHashService;

    public WalletService(WalletRepository walletRepository,
            WalletTransactionRepository walletTransactionRepository,
            DepositClaimRepository depositClaimRepository,
            UserRepository userRepository,
            WalletProperties walletProperties,
            PaymentProvider paymentProvider,
            TelebirrReferenceNormalizer telebirrReferenceNormalizer,
            IdempotencyService idempotencyService,
            RequestHashService requestHashService) {
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.depositClaimRepository = depositClaimRepository;
        this.userRepository = userRepository;
        this.walletProperties = walletProperties;
        this.paymentProvider = paymentProvider;
        this.telebirrReferenceNormalizer = telebirrReferenceNormalizer;
        this.idempotencyService = idempotencyService;
        this.requestHashService = requestHashService;
    }

    @Transactional(readOnly = true)
    public WalletBalanceResponse getBalance(BingoUserPrincipal principal) {
        User player = requirePlayer(principal);
        Wallet wallet = ensureWallet(player);
        return new WalletBalanceResponse(wallet.getId(), wallet.getCurrency(), wallet.getAvailableBalance(),
                wallet.getPromotionalBalance(), wallet.getTotalBalance(), wallet.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public Page<WalletTransactionResponse> getTransactions(BingoUserPrincipal principal, Pageable pageable) {
        User player = requirePlayer(principal);
        ensureWallet(player);
        return walletTransactionRepository.findByOwnerId(player.getId(), pageable).map(this::toTransactionResponse);
    }

    @Transactional(readOnly = true)
    public Page<WalletTransactionResponse> getAdminPlayerTransactions(Long playerId, Pageable pageable) {
        return walletTransactionRepository.findByOwnerId(playerId, pageable).map(this::toTransactionResponse);
    }

    @Transactional(readOnly = true)
    public Page<DepositClaimResponse> getOwnDeposits(BingoUserPrincipal principal, Pageable pageable) {
        User player = requirePlayer(principal);
        return depositClaimRepository.findByPlayerId(player.getId(), pageable).map(this::toDepositResponse);
    }

    @Transactional(readOnly = true)
    public Page<DepositClaimResponse> getAdminPlayerDeposits(Long playerId, Pageable pageable) {
        return depositClaimRepository.findByPlayerId(playerId, pageable).map(this::toDepositResponse);
    }

    @Transactional(readOnly = true)
    public Page<DepositClaimResponse> getAdminDeposits(DepositClaimStatus status, String query, Pageable pageable) {
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
        Long referenceId = null;
        if (normalizedQuery != null) {
            try {
                referenceId = Long.valueOf(normalizedQuery);
            } catch (NumberFormatException ignored) {
                referenceId = null;
            }
        }
        Page<DepositClaim> page = depositClaimRepository.searchAdminDeposits(status, normalizedQuery, referenceId,
                pageable);
        return page.map(this::toDepositResponse);
    }

    @Transactional
    public IdempotentResult<DepositClaimResponse> submitDeposit(BingoUserPrincipal principal,
            DepositClaimRequest request,
            String idempotencyKey) {
        User player = requirePlayer(principal);
        if (!walletProperties.currency().equalsIgnoreCase(request.currency())) {
            throw new BadRequestException("UNSUPPORTED_CURRENCY",
                    "Only " + walletProperties.currency() + " deposits are supported");
        }

        String normalizedReference = telebirrReferenceNormalizer.normalize(request.telebirrReference());
        String requestHash = requestHashService.hash("POST", "/api/v1/wallet/deposits", request);
        String operationId = UUID.randomUUID().toString();

        return idempotencyService.execute(player.getId(), idempotencyKey, requestHash, operationId,
                HttpStatus.CREATED.value(), DepositClaimResponse.class, () -> {
                    if (depositClaimRepository.existsByNormalizedTelebirrReference(normalizedReference)) {
                        throw new ConflictException("DUPLICATE_TELEBIRR_REFERENCE",
                                "That telebirr reference has already been submitted");
                    }

                    BigDecimal cashAmount = MoneyValues.money(request.submittedCashAmount());
                    BigDecimal conversionRate = MoneyValues.conversionRate(walletProperties.depositConversionRate());
                    BigDecimal creditAmount = MoneyValues.creditAmount(cashAmount, conversionRate);

                    DepositClaim claim = DepositClaim.create(
                            player,
                            cashAmount,
                            walletProperties.currency(),
                            conversionRate,
                            creditAmount,
                            normalizedReference,
                            operationId,
                            Instant.now());
                    claim = depositClaimRepository.save(claim);

                    return toDepositResponse(claim);
                });
    }

    @Transactional
    public IdempotentResult<DepositClaimResponse> approveDeposit(Long depositId,
            BingoUserPrincipal principal,
            DepositReviewRequest request,
            String idempotencyKey) {
        requireAdmin(principal);
        throw new ConflictException("DEPOSIT_VERIFICATION_REQUIRED",
                "An unverified deposit cannot be approved or credited");
    }

    @Transactional
    public IdempotentResult<DepositClaimResponse> rejectDeposit(Long depositId,
            BingoUserPrincipal principal,
            DepositReviewRequest request,
            String idempotencyKey) {
        User reviewer = requireFinanceApprover(principal);
        String requestHash = requestHashService.hash("POST", "/api/v1/admin/deposits/" + depositId + "/reject",
                request);
        String operationId = UUID.randomUUID().toString();

        return idempotencyService.execute(reviewer.getId(), idempotencyKey, requestHash, operationId,
                HttpStatus.OK.value(), DepositClaimResponse.class, () -> {
                    DepositClaim claim = depositClaimRepository.findByIdForUpdate(depositId)
                            .orElseThrow(() -> new NotFoundException("DEPOSIT_NOT_FOUND", "Deposit claim not found"));

                    if (claim.getStatus() != DepositClaimStatus.PENDING
                            && claim.getStatus() != DepositClaimStatus.AWAITING_VERIFICATION
                            && claim.getStatus() != DepositClaimStatus.VERIFIED
                            && claim.getStatus() != DepositClaimStatus.VERIFICATION_EXCEPTION) {
                        throw new ConflictException("DEPOSIT_ALREADY_REVIEWED",
                                "Only an unposted deposit claim can be rejected");
                    }

                    claim.setStatus(DepositClaimStatus.REJECTED);
                    claim.setReviewedBy(reviewer);
                    claim.setReviewedAt(Instant.now());
                    claim.setReviewNote(request.reviewNote());
                    DepositClaim saved = depositClaimRepository.save(claim);
                    return toDepositResponse(saved);
                });
    }

    @Transactional
    public IdempotentResult<WalletAdjustmentResponse> adjustPlayerWallet(Long playerId,
            BingoUserPrincipal principal,
            WalletAdjustmentRequest request,
            String idempotencyKey) {
        requireAdmin(principal);
        throw new ConflictException("WALLET_ADJUSTMENT_MAKER_CHECKER_REQUIRED",
                "Direct wallet adjustments are disabled; submit a maker-checker request instead");
    }

    @Transactional(readOnly = true)
    public PaymentInstructionsResponse currentPaymentInstructions() {
        return toPaymentInstructions();
    }

    @Transactional
    public Wallet createWalletIfMissing(User owner) {
        return ensureWallet(owner);
    }

    private User requirePlayer(BingoUserPrincipal principal) {
        User user = principal.getUser();
        if (!user.hasRole(UserRole.PLAYER)) {
            throw new BadRequestException("PLAYER_ROLE_REQUIRED", "This endpoint is available to player accounts only");
        }
        return user;
    }

    private User requireAdmin(BingoUserPrincipal principal) {
        User user = principal.getUser();
        if (!user.hasRole(UserRole.ADMIN)) {
            throw new BadRequestException("ADMIN_ROLE_REQUIRED", "This endpoint is available to admin accounts only");
        }
        return user;
    }

    private User requireFinanceApprover(BingoUserPrincipal principal) {
        User user = principal.getUser();
        boolean staffUser = user.hasRole(UserRole.ADMIN) || user.hasRole(UserRole.SUPPORT);
        if (!user.isActive() || !staffUser || !user.hasRole(UserRole.FINANCE_APPROVER)) {
            throw new ConflictException("FINANCE_APPROVER_REQUIRED",
                    "An active finance approver is required");
        }
        return user;
    }

    private Wallet ensureWallet(User owner) {
        return walletRepository.findByOwnerId(owner.getId()).orElseGet(() -> {
            Wallet wallet = Wallet.create(owner, walletProperties.currency());
            try {
                return walletRepository.save(wallet);
            } catch (DataIntegrityViolationException exception) {
                return walletRepository.findByOwnerId(owner.getId()).orElseThrow(() -> exception);
            }
        });
    }

    private Wallet lockWalletForUpdate(User owner) {
        return walletRepository.findByOwnerIdForUpdate(owner.getId()).orElseGet(() -> ensureWallet(owner));
    }

    private WalletTransactionResponse toTransactionResponse(WalletTransaction transaction) {
        return new WalletTransactionResponse(
                transaction.getId(),
                transaction.getOperationId(),
                transaction.getTransactionType().name(),
                transaction.getAmount(),
                transaction.getBalanceBefore(),
                transaction.getBalanceAfter(),
                transaction.getTransferableBalanceBefore(),
                transaction.getTransferableBalanceAfter(),
                transaction.getPromotionalBalanceBefore(),
                transaction.getPromotionalBalanceAfter(),
                transaction.getCurrency(),
                transaction.getReferenceType(),
                transaction.getReferenceId(),
                transaction.getNote(),
                transaction.getMetadataJson(),
                transaction.getCreatedAt());
    }

    private DepositClaimResponse toDepositResponse(DepositClaim claim) {
        return new DepositClaimResponse(
                claim.getId(),
                claim.getOperationId(),
                claim.getPlayer().getId(),
                claim.getSubmittedCashAmount(),
                claim.getCurrency(),
                claim.getConversionRateSnapshot(),
                claim.getCalculatedCreditAmount(),
                claim.getNormalizedTelebirrReference(),
                claim.getStatus().name(),
                claim.getSubmittedAt(),
                claim.getReviewedBy() == null ? null : claim.getReviewedBy().getId(),
                claim.getReviewedAt(),
                claim.getReviewNote(),
                claim.getVerifiedBy() == null ? null : claim.getVerifiedBy().getId(),
                claim.getVerifiedAt(),
                toPaymentInstructions());
    }

    private PaymentInstructionsResponse toPaymentInstructions() {
        var current = paymentProvider.currentInstructions();
        return new PaymentInstructionsResponse(
                current.providerName(),
                current.businessAccount(),
                current.paymentInstructions(),
                current.currency());
    }
}