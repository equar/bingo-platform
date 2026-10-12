package com.equar.bingo.backend.feature.wallet.service;

import com.equar.bingo.backend.common.exception.BadRequestException;
import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.common.exception.NotFoundException;
import com.equar.bingo.backend.common.idempotency.IdempotentResult;
import com.equar.bingo.backend.common.idempotency.IdempotencyService;
import com.equar.bingo.backend.common.money.MoneyValues;
import com.equar.bingo.backend.common.request.RequestHashService;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.wallet.config.WalletProperties;
import com.equar.bingo.backend.feature.wallet.dto.DepositClaimRequest;
import com.equar.bingo.backend.feature.wallet.dto.DepositClaimResponse;
import com.equar.bingo.backend.feature.wallet.dto.DepositReviewRequest;
import com.equar.bingo.backend.feature.wallet.dto.PaymentInstructionsResponse;
import com.equar.bingo.backend.feature.wallet.dto.WalletAdjustmentRequest;
import com.equar.bingo.backend.feature.wallet.dto.WalletAdjustmentResponse;
import com.equar.bingo.backend.feature.wallet.dto.WalletBalanceResponse;
import com.equar.bingo.backend.feature.wallet.dto.WalletTransactionResponse;
import com.equar.bingo.backend.feature.wallet.entity.DepositClaim;
import com.equar.bingo.backend.feature.wallet.entity.DepositClaimStatus;
import com.equar.bingo.backend.feature.wallet.entity.Wallet;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransaction;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransactionType;
import com.equar.bingo.backend.feature.wallet.payment.PaymentProvider;
import com.equar.bingo.backend.feature.wallet.repository.DepositClaimRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
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
                wallet.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public Page<WalletTransactionResponse> getTransactions(BingoUserPrincipal principal, Pageable pageable) {
        User player = requirePlayer(principal);
        ensureWallet(player);
        return walletTransactionRepository.findByOwnerId(player.getId(), pageable).map(this::toTransactionResponse);
    }

    @Transactional(readOnly = true)
    public Page<DepositClaimResponse> getOwnDeposits(BingoUserPrincipal principal, Pageable pageable) {
        User player = requirePlayer(principal);
        return depositClaimRepository.findByPlayerId(player.getId(), pageable).map(this::toDepositResponse);
    }

    @Transactional(readOnly = true)
    public Page<DepositClaimResponse> getAdminDeposits(DepositClaimStatus status, Pageable pageable) {
        Page<DepositClaim> page = status == null
                ? depositClaimRepository.findAllOrderBySubmittedAt(pageable)
                : depositClaimRepository.findByStatus(status, pageable);
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

        return idempotencyService.execute(idempotencyKey, requestHash, operationId, HttpStatus.CREATED.value(),
                DepositClaimResponse.class, () -> {
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
        User reviewer = requireAdmin(principal);
        String requestHash = requestHashService.hash("POST", "/api/v1/admin/deposits/" + depositId + "/approve",
                request);
        String operationId = UUID.randomUUID().toString();

        return idempotencyService.execute(idempotencyKey, requestHash, operationId, HttpStatus.OK.value(),
                DepositClaimResponse.class, () -> {
                    DepositClaim claim = depositClaimRepository.findByIdForUpdate(depositId)
                            .orElseThrow(() -> new NotFoundException("DEPOSIT_NOT_FOUND", "Deposit claim not found"));

                    if (claim.getStatus() != DepositClaimStatus.PENDING) {
                        throw new ConflictException("DEPOSIT_ALREADY_REVIEWED",
                                "Deposit claim has already been reviewed");
                    }

                    Wallet wallet = lockWalletForUpdate(claim.getPlayer());
                    BigDecimal balanceBefore = wallet.getAvailableBalance();
                    BigDecimal balanceAfter = MoneyValues.money(balanceBefore.add(claim.getCalculatedCreditAmount()));

                    claim.setStatus(DepositClaimStatus.APPROVED);
                    claim.setReviewedBy(reviewer);
                    claim.setReviewedAt(Instant.now());
                    claim.setReviewNote(request.reviewNote());

                    wallet.setAvailableBalance(balanceAfter);
                    walletRepository.save(wallet);
                    walletTransactionRepository.save(WalletTransaction.create(
                            wallet,
                            operationId,
                            WalletTransactionType.DEPOSIT_CREDIT,
                            claim.getCalculatedCreditAmount(),
                            balanceBefore,
                            balanceAfter,
                            wallet.getCurrency(),
                            "DEPOSIT_CLAIM",
                            claim.getId(),
                            request.reviewNote(),
                            Instant.now()));

                    DepositClaim saved = depositClaimRepository.save(claim);
                    return toDepositResponse(saved);
                });
    }

    @Transactional
    public IdempotentResult<DepositClaimResponse> rejectDeposit(Long depositId,
            BingoUserPrincipal principal,
            DepositReviewRequest request,
            String idempotencyKey) {
        User reviewer = requireAdmin(principal);
        String requestHash = requestHashService.hash("POST", "/api/v1/admin/deposits/" + depositId + "/reject",
                request);
        String operationId = UUID.randomUUID().toString();

        return idempotencyService.execute(idempotencyKey, requestHash, operationId, HttpStatus.OK.value(),
                DepositClaimResponse.class, () -> {
                    DepositClaim claim = depositClaimRepository.findByIdForUpdate(depositId)
                            .orElseThrow(() -> new NotFoundException("DEPOSIT_NOT_FOUND", "Deposit claim not found"));

                    if (claim.getStatus() != DepositClaimStatus.PENDING) {
                        throw new ConflictException("DEPOSIT_ALREADY_REVIEWED",
                                "Deposit claim has already been reviewed");
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
        User reviewer = requireAdmin(principal);
        if (!walletProperties.currency().equalsIgnoreCase(request.currency())) {
            throw new BadRequestException("UNSUPPORTED_CURRENCY",
                    "Only " + walletProperties.currency() + " adjustments are supported");
        }

        if (request.amount().compareTo(BigDecimal.ZERO) == 0) {
            throw new BadRequestException("INVALID_AMOUNT", "Adjustment amount must not be zero");
        }

        String requestHash = requestHashService.hash("POST",
                "/api/v1/admin/players/" + playerId + "/wallet-adjustments", request);
        String operationId = UUID.randomUUID().toString();

        return idempotencyService.execute(idempotencyKey, requestHash, operationId, HttpStatus.OK.value(),
                WalletAdjustmentResponse.class, () -> {
                    User player = userRepository.findById(playerId)
                            .orElseThrow(() -> new NotFoundException("PLAYER_NOT_FOUND", "Player account not found"));
                    if (!player.hasRole(UserRole.PLAYER)) {
                        throw new BadRequestException("NOT_A_PLAYER",
                                "Wallet adjustments can only target player accounts");
                    }

                    Wallet wallet = lockWalletForUpdate(player);
                    BigDecimal balanceBefore = wallet.getAvailableBalance();
                    BigDecimal amount = MoneyValues.money(request.amount());
                    BigDecimal balanceAfter = MoneyValues.money(balanceBefore.add(amount));
                    if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
                        throw new ConflictException("INSUFFICIENT_BALANCE", "Wallet balance cannot go below zero");
                    }

                    wallet.setAvailableBalance(balanceAfter);
                    walletRepository.save(wallet);
                    walletTransactionRepository.save(WalletTransaction.create(
                            wallet,
                            operationId,
                            WalletTransactionType.WALLET_ADJUSTMENT,
                            amount,
                            balanceBefore,
                            balanceAfter,
                            wallet.getCurrency(),
                            "WALLET_ADJUSTMENT",
                            player.getId(),
                            request.reason(),
                            Instant.now()));

                    return new WalletAdjustmentResponse(
                            operationId,
                            wallet.getId(),
                            player.getId(),
                            amount,
                            balanceBefore,
                            balanceAfter,
                            request.reason(),
                            Instant.now());
                });
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
                transaction.getCurrency(),
                transaction.getReferenceType(),
                transaction.getReferenceId(),
                transaction.getNote(),
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