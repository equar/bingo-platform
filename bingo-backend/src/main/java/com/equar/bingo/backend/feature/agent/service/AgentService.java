package com.equar.bingo.backend.feature.agent.service;

import com.equar.bingo.backend.common.exception.BadRequestException;
import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.common.exception.NotFoundException;
import com.equar.bingo.backend.common.api.PageResponse;
import com.equar.bingo.backend.common.idempotency.IdempotentResult;
import com.equar.bingo.backend.common.idempotency.IdempotencyService;
import com.equar.bingo.backend.common.money.MoneyValues;
import com.equar.bingo.backend.common.request.RequestHashService;
import com.equar.bingo.backend.feature.agent.config.AgentProperties;
import com.equar.bingo.backend.feature.agent.dto.*;
import com.equar.bingo.backend.feature.agent.entity.*;
import com.equar.bingo.backend.feature.agent.repository.*;
import com.equar.bingo.backend.feature.auth.dto.AuthResponse;
import com.equar.bingo.backend.feature.auth.dto.UserProfileResponse;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.auth.service.AuthenticationAttemptThrottleService;
import com.equar.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.equar.bingo.backend.feature.auth.service.JwtService;
import com.equar.bingo.backend.feature.game.dto.TicketPurchaseResponse;
import com.equar.bingo.backend.feature.game.dto.TicketResponse;
import com.equar.bingo.backend.feature.game.entity.Ticket;
import com.equar.bingo.backend.feature.game.entity.PrizeEntitlement;
import com.equar.bingo.backend.feature.game.entity.PrizeEntitlementStatus;
import com.equar.bingo.backend.feature.game.entity.PrizeSettlementPath;
import com.equar.bingo.backend.feature.game.repository.PrizeEntitlementRepository;
import com.equar.bingo.backend.feature.game.repository.TicketRepository;
import com.equar.bingo.backend.feature.game.service.BingoGameService;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.wallet.config.WalletProperties;
import com.equar.bingo.backend.feature.wallet.entity.Wallet;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransaction;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransactionType;
import com.equar.bingo.backend.feature.wallet.repository.WalletRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import com.equar.bingo.backend.feature.wallet.service.SignupRewardService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentService {

    private static final int MAX_HISTORY_PAGE_SIZE = 100;
    private static final int MAX_HISTORY_SCAN = 10_000;

    private final AgentRepository agentRepository;
    private final AgentFloatRepository agentFloatRepository;
    private final AgentFloatTransactionRepository agentFloatTransactionRepository;
    private final CommissionRuleRepository commissionRuleRepository;
    private final CommissionAccrualRepository commissionAccrualRepository;
    private final FloatTopUpRequestRepository floatTopUpRequestRepository;
    private final AgentOperationRepository agentOperationRepository;
    private final PayoutVerificationRecordRepository payoutVerificationRecordRepository;
    private final PrizeEntitlementRepository prizeEntitlementRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final BingoGameService bingoGameService;
    private final AgentProperties agentProperties;
    private final WalletProperties walletProperties;
    private final EthiopianPhoneNormalizer phoneNormalizer;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final PasswordEncoder agentPinEncoder;
    private final JwtService jwtService;
    private final IdempotencyService idempotencyService;
    private final RequestHashService requestHashService;
    private final AuthenticationAttemptThrottleService throttleService;
    private final SignupRewardService signupRewardService;

    public AgentService(AgentRepository agentRepository,
            AgentFloatRepository agentFloatRepository,
            AgentFloatTransactionRepository agentFloatTransactionRepository,
            CommissionRuleRepository commissionRuleRepository,
            CommissionAccrualRepository commissionAccrualRepository,
            FloatTopUpRequestRepository floatTopUpRequestRepository,
            AgentOperationRepository agentOperationRepository,
            PayoutVerificationRecordRepository payoutVerificationRecordRepository,
            PrizeEntitlementRepository prizeEntitlementRepository,
            TicketRepository ticketRepository,
            UserRepository userRepository,
            WalletRepository walletRepository,
            WalletTransactionRepository walletTransactionRepository,
            BingoGameService bingoGameService,
            AgentProperties agentProperties,
            WalletProperties walletProperties,
            EthiopianPhoneNormalizer phoneNormalizer,
            AuthenticationManager authenticationManager,
            PasswordEncoder passwordEncoder,
            @org.springframework.beans.factory.annotation.Qualifier("agentPinEncoder") PasswordEncoder agentPinEncoder,
            JwtService jwtService,
            IdempotencyService idempotencyService,
            RequestHashService requestHashService,
            AuthenticationAttemptThrottleService throttleService,
            SignupRewardService signupRewardService) {
        this.agentRepository = agentRepository;
        this.agentFloatRepository = agentFloatRepository;
        this.agentFloatTransactionRepository = agentFloatTransactionRepository;
        this.commissionRuleRepository = commissionRuleRepository;
        this.commissionAccrualRepository = commissionAccrualRepository;
        this.floatTopUpRequestRepository = floatTopUpRequestRepository;
        this.agentOperationRepository = agentOperationRepository;
        this.payoutVerificationRecordRepository = payoutVerificationRecordRepository;
        this.prizeEntitlementRepository = prizeEntitlementRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.bingoGameService = bingoGameService;
        this.agentProperties = agentProperties;
        this.walletProperties = walletProperties;
        this.phoneNormalizer = phoneNormalizer;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.agentPinEncoder = agentPinEncoder;
        this.jwtService = jwtService;
        this.idempotencyService = idempotencyService;
        this.requestHashService = requestHashService;
        this.throttleService = throttleService;
        this.signupRewardService = signupRewardService;
    }

    @Transactional
    public AuthResponse login(AgentLoginRequest request) {
        String normalizedPhone = phoneNormalizer.normalize(request.phoneNumber());
        throttleService.requireAccountAllowed(
                "agent",
                normalizedPhone,
                agentProperties.loginAttemptLimit(),
                agentProperties.attemptWindow());

        org.springframework.security.core.Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedPhone, request.password()));
        } catch (org.springframework.security.core.AuthenticationException exception) {
            throttleService.recordAccountFailure(
                    "agent",
                    normalizedPhone,
                    agentProperties.loginAttemptLimit(),
                    agentProperties.attemptWindow());
            throw exception;
        }
        User user = ((BingoUserPrincipal) authentication.getPrincipal()).getUser();
        Agent agent = requireApprovedAgent(user.getId());
        if (!agentPinEncoder.matches(request.pin(), user.getAgentPinHash())) {
            throttleService.recordAccountFailure(
                    "agent",
                    normalizedPhone,
                    agentProperties.loginAttemptLimit(),
                    agentProperties.attemptWindow());
            throw new BadRequestException("INVALID_PIN", "Invalid agent PIN");
        }
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, "Bearer", jwtService.getExpirationSeconds(), toProfile(user));
    }

    @Transactional(readOnly = true)
    public AgentDashboardResponse dashboard(BingoUserPrincipal principal) {
        Agent agent = requireApprovedAgent(principal.getUser().getId());
        AgentFloat agentFloat = requireAgentFloat(agent.getId());
        return new AgentDashboardResponse(agent.getId(), agent.getStatus().name(), agentFloat.getAvailableBalance(),
                agentFloat.getClearingBalance(),
                commissionAccrualRepository.findByAgentIdOrderByCreatedAtDesc(agent.getId()).size(),
                commissionAccrualRepository.findByAgentIdOrderByCreatedAtDesc(agent.getId()).size(),
                floatTopUpRequestRepository.findByAgentIdOrderByCreatedAtDesc(agent.getId()).size(), Instant.now());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse lookupPlayer(String normalizedPhone) {
        User player = userRepository.findByNormalizedPhone(phoneNormalizer.normalize(normalizedPhone))
                .orElseThrow(() -> new NotFoundException("PLAYER_NOT_FOUND", "Player account not found"));
        return toProfile(player);
    }

    @Transactional
    public UserProfileResponse registerPlayer(AgentPlayerRegistrationRequest request) {
        String normalizedPhone = phoneNormalizer.normalize(request.phoneNumber());
        if (userRepository.existsByNormalizedPhone(normalizedPhone)) {
            throw new ConflictException("PHONE_ALREADY_REGISTERED", "That phone number is already registered");
        }
        String generatedPassword = UUID.randomUUID().toString() + "!aA1";
        User player = User.create(normalizedPhone, request.displayName().trim(),
                passwordEncoder.encode(generatedPassword),
                AccountStatus.ACTIVE, Set.of(UserRole.PLAYER), Instant.now());
        player = userRepository.save(player);
        walletRepository.save(Wallet.create(player, walletProperties.currency()));
        signupRewardService.grantForNewPlayer(player);
        return toProfile(player);
    }

    @Transactional
    public IdempotentResult<TicketPurchaseResponse> sellTicket(BingoUserPrincipal principal,
            AgentTicketSaleRequest request,
            String idempotencyKey) {
        Agent agent = requireApprovedAgent(principal.getUser().getId());
        verifyPin(agent.getUser(), request.pin());
        User player = lookupOrCreatePlayer(request.playerPhoneNumber());
        String requestHash = requestHashService.hash("POST", "/api/v1/agent/tickets", request);
        String operationId = UUID.randomUUID().toString();

        return idempotencyService.execute(principal.getUser().getId(), idempotencyKey, requestHash, operationId,
                HttpStatus.CREATED.value(), TicketPurchaseResponse.class, () -> {
                    AgentFloat agentFloat = requireAgentFloatForUpdate(agent.getId());
                    BigDecimal ticketPrice = bingoGameService.getSession(request.sessionId()).ticketPrice();
                    debitAgentFloat(agentFloat, ticketPrice, AgentFloatTransactionType.TICKET_SALE_DEBIT,
                            "BINGO_TICKET", null, operationId, "Agent ticket sale");
                    TicketPurchaseResponse response = bingoGameService.createAgentTicket(request.sessionId(), player,
                            operationId);
                    Ticket ticket = ticketRepository.findById(response.ticket().id())
                            .orElseThrow(() -> new NotFoundException("TICKET_NOT_FOUND", "Ticket not found"));
                    createCommissionAccrual(agent, ticket, ticketPrice, operationId);
                    return response;
                });
    }

    @Transactional
    public IdempotentResult<AgentFloatResponse> transferFloat(BingoUserPrincipal principal,
            AgentFloatTransferRequest request,
            String idempotencyKey) {
        Agent agent = requireApprovedAgent(principal.getUser().getId());
        verifyPin(agent.getUser(), request.pin());
        if (!request.currency().equalsIgnoreCase(requireAgentFloat(agent.getId()).getCurrency())) {
            throw new BadRequestException("UNSUPPORTED_CURRENCY", "Currency does not match agent float currency");
        }
        User player = lookupOrCreatePlayer(request.playerPhoneNumber());
        String requestHash = requestHashService.hash("POST", "/api/v1/agent/float/transfers", request);
        String operationId = UUID.randomUUID().toString();

        return idempotencyService.execute(principal.getUser().getId(), idempotencyKey, requestHash, operationId,
                HttpStatus.OK.value(), AgentFloatResponse.class, () -> {
                    AgentFloat agentFloat = requireAgentFloatForUpdate(agent.getId());
                    Wallet wallet = walletRepository.findByOwnerIdForUpdate(player.getId())
                            .orElseGet(() -> walletRepository.save(Wallet.create(player, request.currency())));
                    BigDecimal amount = requirePositiveAmount(request.amount(), "INVALID_AMOUNT",
                            "Transfer amount must be greater than zero");
                    debitAgentFloat(agentFloat, amount, AgentFloatTransactionType.PLAYER_WALLET_TRANSFER_DEBIT,
                            "PLAYER_WALLET", player.getId(), operationId, request.note());

                    BigDecimal before = wallet.getAvailableBalance();
                    BigDecimal after = MoneyValues.money(before.add(amount));
                    BigDecimal promotionalBalance = wallet.getPromotionalBalance();
                    wallet.setAvailableBalance(after);
                    walletRepository.save(wallet);
                    walletTransactionRepository.save(WalletTransaction.create(wallet, operationId,
                            WalletTransactionType.AGENT_FLOAT_TRANSFER_CREDIT, amount, before, after,
                            promotionalBalance, promotionalBalance,
                            wallet.getCurrency(),
                            "AGENT_TRANSFER", agent.getId(), request.note(), null, Instant.now()));
                    return toFloatResponse(agentFloat);
                });
    }

    @Transactional(readOnly = true)
    public AgentPayoutLookupResponse lookupApprovedPayout(BingoUserPrincipal principal, String payoutCode) {
        requireApprovedAgent(principal.getUser().getId());
        throttleService.requireAllowed(
                "agent-payout-code:" + payoutCode,
                agentProperties.payoutCodeAttemptLimit(),
                agentProperties.attemptWindow(),
                "AGENT_PAYOUT_CODE_RATE_LIMITED",
                "Too many payout code attempts");
        PrizeEntitlement entitlement = prizeEntitlementRepository.findByPayoutCode(payoutCode)
                .orElseThrow(() -> new NotFoundException("PAYOUT_CODE_NOT_FOUND", "Payout code not found"));
        if (entitlement.getSettlementPath() != PrizeSettlementPath.AGENT_CASH) {
            throw new BadRequestException("NOT_AGENT_CASH", "That entitlement is not agent cash");
        }
        return new AgentPayoutLookupResponse(entitlement.getId(), entitlement.getSession().getId(),
                entitlement.getTicket().getId(), entitlement.getClaimant().getId(), entitlement.getSettlementPath(),
                entitlement.getStatus(), entitlement.getPrizeAmount(), entitlement.getCurrency(),
                entitlement.getPayoutCode(),
                entitlement.getCreatedAt());
    }

    @Transactional
    public IdempotentResult<PayoutVerificationResponse> verifyAndDisbursePayout(BingoUserPrincipal principal,
            AgentPayoutVerificationRequest request,
            String idempotencyKey) {
        Agent agent = requireApprovedAgent(principal.getUser().getId());
        throttleService.requireAllowed(
                "agent-payout-code:" + request.payoutCode(),
                agentProperties.payoutCodeAttemptLimit(),
                agentProperties.attemptWindow(),
                "AGENT_PAYOUT_CODE_RATE_LIMITED",
                "Too many payout code attempts");
        verifyPin(agent.getUser(), request.pin());
        String requestHash = requestHashService.hash("POST", "/api/v1/agent/payouts/verify", request);
        String operationId = UUID.randomUUID().toString();

        return idempotencyService.execute(principal.getUser().getId(), idempotencyKey, requestHash, operationId,
                HttpStatus.OK.value(), PayoutVerificationResponse.class, () -> {
                    PrizeEntitlement entitlement = prizeEntitlementRepository
                            .findByPayoutCodeForUpdate(request.payoutCode())
                            .orElseThrow(() -> new NotFoundException("PAYOUT_CODE_NOT_FOUND", "Payout code not found"));
                    if (entitlement.getSettlementPath() != PrizeSettlementPath.AGENT_CASH) {
                        throw new BadRequestException("NOT_AGENT_CASH", "That entitlement is not agent cash");
                    }
                    if (entitlement.getStatus() != PrizeEntitlementStatus.PENDING) {
                        throw new ConflictException("PAYOUT_ALREADY_SETTLED", "That payout has already been settled");
                    }

                    AgentFloat agentFloat = requireAgentFloatForUpdate(agent.getId());
                    BigDecimal amount = requirePositiveAmount(entitlement.getPrizeAmount(), "INVALID_PAYOUT_AMOUNT",
                            "Payout amount must be greater than zero");
                    AgentFloatTransaction payoutTransaction = debitAgentFloat(agentFloat, amount,
                            AgentFloatTransactionType.PAYOUT_DISBURSEMENT_DEBIT,
                            "PAYOUT_ENTITLEMENT", entitlement.getId(), operationId, request.clearingReference());

                    entitlement.setStatus(PrizeEntitlementStatus.SETTLED);
                    entitlement.setSettledAt(Instant.now());
                    entitlement.setSettlementReference(request.clearingReference());
                    prizeEntitlementRepository.save(entitlement);

                    PayoutVerificationRecord record = PayoutVerificationRecord.create(entitlement, agent,
                            request.payoutCode(), amount, operationId);
                    record.setStatus(PayoutVerificationStatus.DISBURSED);
                    record.setVerifiedBy(principal.getUser());
                    record.setVerifiedAt(Instant.now());
                    record.setClearingReference(request.clearingReference());
                    record.setFloatTransaction(payoutTransaction);
                    record = payoutVerificationRecordRepository.save(record);

                    return new PayoutVerificationResponse(record.getId(), entitlement.getId(), agent.getId(),
                            request.payoutCode(), record.getStatus(), amount, request.clearingReference(),
                            record.getVerifiedAt(), payoutTransaction.getId());
                });
    }

    @Transactional(readOnly = true)
    public AgentFloatResponse getFloat(BingoUserPrincipal principal) {
        Agent agent = requireApprovedAgent(principal.getUser().getId());
        return toFloatResponse(requireAgentFloat(agent.getId()));
    }

    @Transactional(readOnly = true)
    public List<AgentFloatTransactionResponse> getFloatHistory(BingoUserPrincipal principal) {
        Agent agent = requireApprovedAgent(principal.getUser().getId());
        return agentFloatTransactionRepository.findByAgentFloatAgentIdOrderByCreatedAtDesc(agent.getId()).stream()
                .map(this::toFloatTransactionResponse).toList();
    }

    @Transactional
    public IdempotentResult<FloatTopUpRequestResponse> submitTopUpRequest(BingoUserPrincipal principal,
            SubmitFloatTopUpRequest request,
            String idempotencyKey) {
        Agent agent = requireApprovedAgent(principal.getUser().getId());
        String requestHash = requestHashService.hash("POST", "/api/v1/agent/float-top-up-requests", request);
        String operationId = UUID.randomUUID().toString();

        return idempotencyService.execute(principal.getUser().getId(), idempotencyKey, requestHash, operationId,
                HttpStatus.CREATED.value(), FloatTopUpRequestResponse.class, () -> {
                    BigDecimal amount = requirePositiveAmount(request.amount(), "INVALID_AMOUNT",
                            "Top-up amount must be greater than zero");
                    FloatTopUpRequest topUpRequest = FloatTopUpRequest.create(agent, principal.getUser(), amount,
                            request.currency().toUpperCase(), operationId);
                    topUpRequest.setReviewNote(request.note());
                    topUpRequest = floatTopUpRequestRepository.save(topUpRequest);
                    return toFloatTopUpResponse(topUpRequest);
                });
    }

    @Transactional
    public FloatTopUpRequestResponse reviewTopUpRequest(Long requestId, BingoUserPrincipal principal,
            ReviewFloatTopUpRequest request) {
        User reviewer = requireAdmin(principal);
        FloatTopUpRequest topUpRequest = floatTopUpRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new NotFoundException("TOP_UP_REQUEST_NOT_FOUND", "Float top-up request not found"));
        if (topUpRequest.getStatus() != FloatTopUpRequestStatus.PENDING) {
            throw new ConflictException("TOP_UP_REQUEST_ALREADY_REVIEWED", "Float top-up request already reviewed");
        }

        topUpRequest.setReviewedBy(reviewer);
        topUpRequest.setReviewedAt(Instant.now());
        topUpRequest.setReviewNote(request.reviewNote());
        topUpRequest.setStatus(request.approve() ? FloatTopUpRequestStatus.APPROVED : FloatTopUpRequestStatus.REJECTED);
        topUpRequest = floatTopUpRequestRepository.save(topUpRequest);
        if (request.approve()) {
            AgentFloat agentFloat = requireAgentFloatForUpdate(topUpRequest.getAgent().getId());
            BigDecimal before = agentFloat.getAvailableBalance();
            BigDecimal after = MoneyValues.money(before.add(topUpRequest.getAmount()));
            agentFloat.setAvailableBalance(after);
            agentFloatRepository.save(agentFloat);
            agentFloatTransactionRepository.save(AgentFloatTransaction.create(agentFloat,
                    AgentFloatTransactionType.TOP_UP,
                    topUpRequest.getAmount(),
                    before,
                    after,
                    agentFloat.getClearingBalance(),
                    agentFloat.getClearingBalance(),
                    "FLOAT_TOP_UP_REQUEST",
                    topUpRequest.getId(),
                    UUID.randomUUID().toString(),
                    request.reviewNote()));
        }
        return toFloatTopUpResponse(topUpRequest);
    }

    @Transactional(readOnly = true)
    public List<FloatTopUpRequestResponse> getTopUpRequests(BingoUserPrincipal principal) {
        Agent agent = requireApprovedAgent(principal.getUser().getId());
        return floatTopUpRequestRepository.findByAgentIdOrderByCreatedAtDesc(agent.getId()).stream()
                .map(this::toFloatTopUpResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CommissionAccrualResponse> getCommissionHistory(BingoUserPrincipal principal) {
        Agent agent = requireApprovedAgent(principal.getUser().getId());
        return commissionAccrualRepository.findByAgentIdOrderByCreatedAtDesc(agent.getId()).stream()
                .map(this::toCommissionResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AgentFinancialHistoryEntryResponse> getFinancialHistory(
            BingoUserPrincipal principal,
            Pageable pageable,
            String type,
            String status,
            Instant from,
            Instant to) {
        Agent agent = requireApprovedAgent(principal.getUser().getId());
        if (pageable.getPageSize() > MAX_HISTORY_PAGE_SIZE) {
            throw new BadRequestException("INVALID_PAGE_SIZE",
                    "Financial history page size must not exceed " + MAX_HISTORY_PAGE_SIZE);
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BadRequestException("INVALID_DATE_RANGE", "History start must be before history end");
        }

        long scanLimit = pageable.getOffset() + pageable.getPageSize();
        if (scanLimit > MAX_HISTORY_SCAN) {
            throw new BadRequestException("HISTORY_PAGE_TOO_DEEP",
                    "Use a narrower date range to view older financial activity");
        }

        String normalizedType = normalizeHistoryType(type);
        String normalizedStatus = normalizeHistoryStatus(status);
        HistorySelection selection = selectionFor(normalizedType);
        Pageable fetchPage = PageRequest.of(0, (int) scanLimit);
        List<AgentFinancialHistoryEntryResponse> entries = new ArrayList<>();
        long totalElements = 0;

        if (selection.includeFloat()
                && (normalizedStatus == null || normalizedStatus.equals("COMPLETED"))) {
            Page<AgentFloatTransaction> floatPage = agentFloatTransactionRepository.findFinancialHistory(
                    agent.getId(), selection.floatType(), from, to, fetchPage);
            totalElements += floatPage.getTotalElements();
            String currency = requireAgentFloat(agent.getId()).getCurrency();
            floatPage.getContent().stream()
                    .map(transaction -> toFinancialHistoryEntry(transaction, currency))
                    .forEach(entries::add);
        }

        if (selection.includeCommission()
                && (normalizedStatus == null
                        || normalizedStatus.equals(CommissionAccrualStatus.ACCRUED.name())
                        || normalizedStatus.equals(CommissionAccrualStatus.SETTLED.name()))) {
            CommissionAccrualStatus commissionStatus = normalizedStatus == null
                    ? null
                    : CommissionAccrualStatus.valueOf(normalizedStatus);
            Page<CommissionAccrual> commissionPage = commissionAccrualRepository.findFinancialHistory(
                    agent.getId(), commissionStatus, from, to, fetchPage);
            totalElements += commissionPage.getTotalElements();
            String currency = requireAgentFloat(agent.getId()).getCurrency();
            commissionPage.getContent().stream()
                    .map(accrual -> toFinancialHistoryEntry(accrual, currency))
                    .forEach(entries::add);
        }

        if (selection.includeTopUpRequest()
                && (normalizedStatus == null
                        || normalizedStatus.equals(FloatTopUpRequestStatus.PENDING.name())
                        || normalizedStatus.equals(FloatTopUpRequestStatus.APPROVED.name())
                        || normalizedStatus.equals(FloatTopUpRequestStatus.REJECTED.name()))) {
            FloatTopUpRequestStatus topUpStatus = normalizedStatus == null
                    ? null
                    : FloatTopUpRequestStatus.valueOf(normalizedStatus);
            Page<FloatTopUpRequest> topUpPage = floatTopUpRequestRepository.findFinancialHistory(
                    agent.getId(), topUpStatus, from, to, fetchPage);
            totalElements += topUpPage.getTotalElements();
            topUpPage.getContent().stream()
                    .map(this::toFinancialHistoryEntry)
                    .forEach(entries::add);
        }

        entries.sort(Comparator.comparing(AgentFinancialHistoryEntryResponse::createdAt).reversed());
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), entries.size());
        List<AgentFinancialHistoryEntryResponse> content = start >= end
                ? List.of()
                : entries.subList(start, end);
        Pageable responsePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return PageResponse.from(new PageImpl<>(content, responsePageable, totalElements));
    }

    private AgentFinancialHistoryEntryResponse toFinancialHistoryEntry(
            AgentFloatTransaction transaction,
            String currency) {
        return new AgentFinancialHistoryEntryResponse(
                switch (transaction.getTransactionType()) {
                    case OPENING_BALANCE -> "openingBalance";
                    case TOP_UP -> "floatTopUp";
                    case TICKET_SALE_DEBIT -> "ticketSale";
                    case PLAYER_WALLET_TRANSFER_DEBIT -> "playerWalletTopUp";
                    case PAYOUT_DISBURSEMENT_DEBIT -> "payoutDisbursement";
                    case PAYOUT_CLEARING_CREDIT -> "payoutClearing";
                    case COMMISSION_CREDIT -> "commissionCredit";
                    case MANUAL_ADJUSTMENT -> "manualAdjustment";
                },
                transaction.getAmount(),
                currency,
                transaction.getCreatedAt(),
                "COMPLETED",
                transaction.getOperationId(),
                null,
                transaction.getNote(),
                transaction.getRelatedEntityType() == null || transaction.getRelatedEntityId() == null
                        ? null
                        : transaction.getRelatedEntityType() + "#" + transaction.getRelatedEntityId());
    }

    private AgentFinancialHistoryEntryResponse toFinancialHistoryEntry(
            CommissionAccrual accrual,
            String currency) {
        return new AgentFinancialHistoryEntryResponse(
                "commissionAccrual",
                accrual.getCommissionAmount(),
                currency,
                accrual.getSettledAt() == null ? accrual.getCreatedAt() : accrual.getSettledAt(),
                accrual.getStatus().name(),
                accrual.getSettlementReference() == null || accrual.getSettlementReference().isBlank()
                        ? accrual.getOperationId()
                        : accrual.getSettlementReference(),
                null,
                accrual.getSettlementNote(),
                "TICKET#" + accrual.getTicket().getId());
    }

    private AgentFinancialHistoryEntryResponse toFinancialHistoryEntry(FloatTopUpRequest request) {
        return new AgentFinancialHistoryEntryResponse(
                "floatTopUpRequest",
                request.getAmount(),
                request.getCurrency(),
                request.getCreatedAt(),
                request.getStatus().name(),
                request.getOperationId(),
                request.getReviewedBy() == null ? null : "ADMIN#" + request.getReviewedBy().getId(),
                request.getReviewNote(),
                null);
    }

    private String normalizeHistoryType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String normalized = type.trim();
        if (!List.of(
                "openingBalance",
                "floatTopUp",
                "ticketSale",
                "playerWalletTopUp",
                "payoutDisbursement",
                "payoutClearing",
                "commissionCredit",
                "manualAdjustment",
                "commissionAccrual",
                "floatTopUpRequest",
                "other").contains(normalized)) {
            throw new BadRequestException("INVALID_HISTORY_TYPE", "Unknown financial history type");
        }
        return normalized;
    }

    private String normalizeHistoryStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        if (!List.of("COMPLETED", "ACCRUED", "SETTLED", "PENDING", "APPROVED", "REJECTED").contains(normalized)) {
            throw new BadRequestException("INVALID_HISTORY_STATUS", "Unknown financial history status");
        }
        return normalized;
    }

    private HistorySelection selectionFor(String type) {
        if (type == null) {
            return new HistorySelection(true, null, true, true);
        }
        return switch (type) {
            case "openingBalance" -> new HistorySelection(
                    true, AgentFloatTransactionType.OPENING_BALANCE, false, false);
            case "floatTopUp" -> new HistorySelection(
                    true, AgentFloatTransactionType.TOP_UP, false, false);
            case "ticketSale" -> new HistorySelection(
                    true, AgentFloatTransactionType.TICKET_SALE_DEBIT, false, false);
            case "playerWalletTopUp" -> new HistorySelection(
                    true, AgentFloatTransactionType.PLAYER_WALLET_TRANSFER_DEBIT, false, false);
            case "payoutDisbursement" -> new HistorySelection(
                    true, AgentFloatTransactionType.PAYOUT_DISBURSEMENT_DEBIT, false, false);
            case "payoutClearing" -> new HistorySelection(
                    true, AgentFloatTransactionType.PAYOUT_CLEARING_CREDIT, false, false);
            case "commissionCredit" -> new HistorySelection(
                    true, AgentFloatTransactionType.COMMISSION_CREDIT, false, false);
            case "manualAdjustment" -> new HistorySelection(
                    true, AgentFloatTransactionType.MANUAL_ADJUSTMENT, false, false);
            case "commissionAccrual" -> new HistorySelection(false, null, true, false);
            case "floatTopUpRequest" -> new HistorySelection(false, null, false, true);
            case "other" -> new HistorySelection(false, null, false, false);
            default -> throw new BadRequestException("INVALID_HISTORY_TYPE", "Unknown financial history type");
        };
    }

    private record HistorySelection(
            boolean includeFloat,
            AgentFloatTransactionType floatType,
            boolean includeCommission,
            boolean includeTopUpRequest) {
    }

    @Transactional
    public AgentProfileResponse adminApproveAgent(Long userId, AgentProfileResponse request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User account not found"));
        if (!user.hasRole(UserRole.AGENT)) {
            user.getRoles().add(UserRole.AGENT);
        }
        user.setStatus(AccountStatus.ACTIVE);
        User savedUser = userRepository.save(user);

        Agent agent = agentRepository.findByUserId(userId).orElse(null);
        if (agent == null) {
            agent = Agent.create(savedUser, AgentStatus.APPROVED, request.city(), request.territory(),
                    request.idDocumentType(), request.idDocumentNumber(), request.idDocumentFrontReference(),
                    request.idDocumentBackReference());
        }
        agent.setUser(savedUser);
        agent.setStatus(AgentStatus.APPROVED);
        agent.setCity(request.city());
        agent.setTerritory(request.territory());
        agent.setIdDocumentType(request.idDocumentType());
        agent.setIdDocumentNumber(request.idDocumentNumber());
        agent.setIdDocumentFrontReference(request.idDocumentFrontReference());
        agent.setIdDocumentBackReference(request.idDocumentBackReference());
        Agent savedAgent = agentRepository.save(agent);
        if (agentFloatRepository.findByAgentId(savedAgent.getId()).isEmpty()) {
            agentFloatRepository.save(AgentFloat.create(savedAgent, "ETB", BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO.setScale(2)));
        }
        return toAgentProfileResponse(savedAgent);
    }

    @Transactional
    public AgentProfileResponse adminSuspendAgent(Long agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new NotFoundException("AGENT_NOT_FOUND", "Agent not found"));
        agent.setStatus(AgentStatus.SUSPENDED);
        agent.getUser().setStatus(AccountStatus.SUSPENDED);
        return toAgentProfileResponse(agentRepository.save(agent));
    }

    @Transactional
    public CommissionRuleRequest upsertCommissionRule(BingoUserPrincipal principal, CommissionRuleRequest request) {
        requireAdmin(principal);
        CommissionRule rule = commissionRuleRepository.findFirstByActiveTrueOrderByVersionNumberDesc()
                .map(existing -> {
                    existing.setCommissionRateBps(request.commissionRateBps());
                    existing.setActive(request.active());
                    existing.setEffectiveFrom(request.effectiveFrom());
                    return existing;
                })
                .orElseGet(() -> CommissionRule.create(1, request.commissionRateBps(), request.active(),
                        request.effectiveFrom()));
        commissionRuleRepository.save(rule);
        return request;
    }

    private User requireAdmin(BingoUserPrincipal principal) {
        User user = principal.getUser();
        if (!user.hasRole(UserRole.ADMIN)) {
            throw new BadRequestException("ADMIN_ROLE_REQUIRED", "This endpoint is available to admin accounts only");
        }
        return user;
    }

    private void createCommissionAccrual(Agent agent, Ticket ticket, BigDecimal saleAmount, String operationId) {
        commissionRuleRepository.findFirstByActiveTrueOrderByVersionNumberDesc().ifPresent(rule -> {
            BigDecimal commission = MoneyValues.money(saleAmount
                    .multiply(BigDecimal.valueOf(rule.getCommissionRateBps()))
                    .divide(BigDecimal.valueOf(10000)));
            CommissionAccrual accrual = CommissionAccrual.create(agent, ticket, ticket.getSession().getId(), rule,
                    saleAmount,
                    commission, operationId);
            commissionAccrualRepository.save(accrual);
        });
    }

    private AgentFloatTransaction debitAgentFloat(AgentFloat agentFloat, BigDecimal amount,
            AgentFloatTransactionType type, String relatedEntityType, Long relatedEntityId, String operationId,
            String note) {
        BigDecimal normalizedAmount = requirePositiveAmount(amount, "INVALID_AMOUNT",
                "Amount must be greater than zero");
        BigDecimal beforeAvailable = agentFloat.getAvailableBalance();
        BigDecimal afterAvailable = MoneyValues.money(beforeAvailable.subtract(normalizedAmount));
        if (afterAvailable.compareTo(BigDecimal.ZERO) < 0) {
            throw new ConflictException("AGENT_FLOAT_OVERDRAFT", "Agent float cannot go below zero");
        }
        BigDecimal beforeClearing = agentFloat.getClearingBalance();
        BigDecimal afterClearing = type == AgentFloatTransactionType.PAYOUT_DISBURSEMENT_DEBIT
                ? MoneyValues.money(beforeClearing.add(normalizedAmount))
                : beforeClearing;
        agentFloat.setAvailableBalance(afterAvailable);
        agentFloat.setClearingBalance(afterClearing);
        agentFloatRepository.save(agentFloat);
        return agentFloatTransactionRepository.save(AgentFloatTransaction.create(agentFloat, type, normalizedAmount,
                beforeAvailable, afterAvailable, beforeClearing, afterClearing, relatedEntityType, relatedEntityId,
                operationId, note));
    }

    private BigDecimal requirePositiveAmount(BigDecimal amount, String code, String message) {
        BigDecimal normalizedAmount = MoneyValues.money(amount);
        if (normalizedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(code, message);
        }
        return normalizedAmount;
    }

    private Agent requireApprovedAgent(Long userId) {
        Agent agent = agentRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("AGENT_NOT_FOUND", "Agent profile not found"));
        if (agent.getStatus() != AgentStatus.APPROVED) {
            throw new ConflictException("AGENT_NOT_APPROVED", "Agent is not approved");
        }
        if (!agent.getUser().isActive()) {
            throw new ConflictException("AGENT_NOT_ACTIVE", "Agent account is not active");
        }
        return agent;
    }

    private AgentFloat requireAgentFloat(Long agentId) {
        return agentFloatRepository.findByAgentId(agentId)
                .orElseThrow(() -> new NotFoundException("AGENT_FLOAT_NOT_FOUND", "Agent float not found"));
    }

    private AgentFloat requireAgentFloatForUpdate(Long agentId) {
        return agentFloatRepository.findByAgentIdForUpdate(agentId)
                .orElseThrow(() -> new NotFoundException("AGENT_FLOAT_NOT_FOUND", "Agent float not found"));
    }

    private User lookupOrCreatePlayer(String phoneNumber) {
        String normalizedPhone = phoneNormalizer.normalize(phoneNumber);
        return userRepository.findByNormalizedPhone(normalizedPhone).orElseGet(() -> {
            String generatedPassword = UUID.randomUUID().toString() + "!aA1";
            User player = User.create(normalizedPhone, normalizedPhone, passwordEncoder.encode(generatedPassword),
                    AccountStatus.ACTIVE, Set.of(UserRole.PLAYER), Instant.now());
            return userRepository.save(player);
        });
    }

    private void verifyPin(User user, String pin) {
        throttleService.requireAllowed("agent-pin:" + user.getNormalizedPhone(), agentProperties.pinAttemptLimit(),
                agentProperties.attemptWindow(), "AGENT_PIN_RATE_LIMITED", "Too many PIN attempts");
        if (!agentPinEncoder.matches(pin, user.getAgentPinHash())) {
            throw new BadRequestException("INVALID_PIN", "Invalid agent PIN");
        }
        throttleService.clear("agent-pin:" + user.getNormalizedPhone());
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(user.getId(), user.getDisplayName(), user.getNormalizedPhone(), user.getRoles(),
                user.getStatus(), user.getCreatedAt(), user.getUpdatedAt(), user.isMustChangePassword());
    }

    private AgentProfileResponse toAgentProfileResponse(Agent agent) {
        return new AgentProfileResponse(agent.getId(), agent.getUser().getId(), agent.getUser().getNormalizedPhone(),
                agent.getUser().getDisplayName(), agent.getStatus(), agent.getCity(), agent.getTerritory(),
                agent.getIdDocumentType(), agent.getIdDocumentNumber(), agent.getIdDocumentFrontReference(),
                agent.getIdDocumentBackReference(), agent.getCreatedAt(), agent.getUpdatedAt());
    }

    private AgentFloatResponse toFloatResponse(AgentFloat agentFloat) {
        return new AgentFloatResponse(agentFloat.getAgent().getId(), agentFloat.getId(), agentFloat.getCurrency(),
                agentFloat.getAvailableBalance(), agentFloat.getClearingBalance(), agentFloat.getUpdatedAt());
    }

    private AgentFloatTransactionResponse toFloatTransactionResponse(AgentFloatTransaction transaction) {
        return new AgentFloatTransactionResponse(transaction.getId(), transaction.getAgentFloat().getId(),
                transaction.getTransactionType(), transaction.getAmount(), transaction.getAvailableBefore(),
                transaction.getAvailableAfter(), transaction.getClearingBefore(), transaction.getClearingAfter(),
                transaction.getRelatedEntityType(), transaction.getRelatedEntityId(), transaction.getOperationId(),
                transaction.getNote(), transaction.getCreatedAt());
    }

    private FloatTopUpRequestResponse toFloatTopUpResponse(FloatTopUpRequest request) {
        return new FloatTopUpRequestResponse(request.getId(), request.getAgent().getId(),
                request.getRequestedBy().getId(),
                request.getAmount(), request.getCurrency(), request.getStatus(), request.getOperationId(),
                request.getReviewNote(), request.getReviewedBy() == null ? null : request.getReviewedBy().getId(),
                request.getReviewedAt(), request.getCreatedAt(), request.getUpdatedAt());
    }

    private CommissionAccrualResponse toCommissionResponse(CommissionAccrual accrual) {
        return new CommissionAccrualResponse(accrual.getId(), accrual.getAgent().getId(), accrual.getTicket().getId(),
                accrual.getSessionId(), accrual.getCommissionRuleVersion(), accrual.getSaleAmount(),
                accrual.getCommissionAmount(), accrual.getStatus(), accrual.getOperationId(), accrual.getCreatedAt(),
                accrual.getSettledAt(), accrual.getSettlementReference(), accrual.getSettlementNote());
    }
}