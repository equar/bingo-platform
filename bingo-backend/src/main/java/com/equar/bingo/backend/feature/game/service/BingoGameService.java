package com.equar.bingo.backend.feature.game.service;

import com.equar.bingo.backend.common.exception.BadRequestException;
import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.common.exception.NotFoundException;
import com.equar.bingo.backend.common.idempotency.IdempotentResult;
import com.equar.bingo.backend.common.idempotency.IdempotencyService;
import com.equar.bingo.backend.common.money.MoneyValues;
import com.equar.bingo.backend.common.request.RequestHashService;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.game.config.GameProperties;
import com.equar.bingo.backend.feature.game.dto.BingoPrizeConfigRequest;
import com.equar.bingo.backend.feature.game.dto.BingoRoomRequest;
import com.equar.bingo.backend.feature.game.dto.BingoRoomResponse;
import com.equar.bingo.backend.feature.game.dto.DrawEventRequest;
import com.equar.bingo.backend.feature.game.dto.DrawEventResponse;
import com.equar.bingo.backend.feature.game.dto.GameSessionRequest;
import com.equar.bingo.backend.feature.game.dto.GameSessionResponse;
import com.equar.bingo.backend.feature.game.dto.GameSessionStateUpdateRequest;
import com.equar.bingo.backend.feature.game.dto.PrizeEntitlementResponse;
import com.equar.bingo.backend.feature.game.dto.PrizeEntitlementSettlementRequest;
import com.equar.bingo.backend.feature.game.dto.TicketPurchaseResponse;
import com.equar.bingo.backend.feature.game.dto.TicketResponse;
import com.equar.bingo.backend.feature.game.dto.WinClaimRequest;
import com.equar.bingo.backend.feature.game.dto.WinClaimResponse;
import com.equar.bingo.backend.feature.game.dto.WinClaimReviewRequest;
import com.equar.bingo.backend.feature.game.entity.BingoPatternType;
import com.equar.bingo.backend.feature.game.entity.BingoRoom;
import com.equar.bingo.backend.feature.game.entity.DrawEvent;
import com.equar.bingo.backend.feature.game.entity.GameSession;
import com.equar.bingo.backend.feature.game.entity.GameSessionState;
import com.equar.bingo.backend.feature.game.entity.PrizeEntitlement;
import com.equar.bingo.backend.feature.game.entity.PrizeEntitlementStatus;
import com.equar.bingo.backend.feature.game.entity.PrizeSettlementPath;
import com.equar.bingo.backend.feature.game.entity.Ticket;
import com.equar.bingo.backend.feature.game.entity.WinClaim;
import com.equar.bingo.backend.feature.game.entity.WinClaimStatus;
import com.equar.bingo.backend.feature.game.model.BingoPrizeConfig;
import com.equar.bingo.backend.feature.game.model.BingoRoomSnapshot;
import com.equar.bingo.backend.feature.game.model.BingoSessionSnapshot;
import com.equar.bingo.backend.feature.game.repository.BingoRoomRepository;
import com.equar.bingo.backend.feature.game.repository.DrawEventRepository;
import com.equar.bingo.backend.feature.game.repository.GameSessionRepository;
import com.equar.bingo.backend.feature.game.repository.PrizeEntitlementRepository;
import com.equar.bingo.backend.feature.game.repository.TicketRepository;
import com.equar.bingo.backend.feature.game.repository.WinClaimRepository;
import com.equar.bingo.backend.feature.game.websocket.GameDrawRecordedEvent;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.wallet.entity.Wallet;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransaction;
import com.equar.bingo.backend.feature.wallet.entity.WalletTransactionType;
import com.equar.bingo.backend.feature.wallet.repository.WalletRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BingoGameService {

    private static final TypeReference<List<Integer>> INTEGER_LIST = new TypeReference<>() {
    };
    private static final int GRID_SIZE = 5;

    private final BingoRoomRepository bingoRoomRepository;
    private final GameSessionRepository gameSessionRepository;
    private final TicketRepository ticketRepository;
    private final DrawEventRepository drawEventRepository;
    private final WinClaimRepository winClaimRepository;
    private final PrizeEntitlementRepository prizeEntitlementRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final GameProperties gameProperties;
    private final GameSnapshotMapper snapshotMapper;
    private final BingoCardGenerator cardGenerator;
    private final BingoPatternEvaluator patternEvaluator;
    private final IdempotencyService idempotencyService;
    private final RequestHashService requestHashService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    public BingoGameService(BingoRoomRepository bingoRoomRepository,
            GameSessionRepository gameSessionRepository,
            TicketRepository ticketRepository,
            DrawEventRepository drawEventRepository,
            WinClaimRepository winClaimRepository,
            PrizeEntitlementRepository prizeEntitlementRepository,
            UserRepository userRepository,
            WalletRepository walletRepository,
            WalletTransactionRepository walletTransactionRepository,
            GameProperties gameProperties,
            GameSnapshotMapper snapshotMapper,
            IdempotencyService idempotencyService,
            RequestHashService requestHashService,
            ObjectMapper objectMapper,
            ApplicationEventPublisher applicationEventPublisher) {
        this.bingoRoomRepository = bingoRoomRepository;
        this.gameSessionRepository = gameSessionRepository;
        this.ticketRepository = ticketRepository;
        this.drawEventRepository = drawEventRepository;
        this.winClaimRepository = winClaimRepository;
        this.prizeEntitlementRepository = prizeEntitlementRepository;
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.gameProperties = gameProperties;
        this.snapshotMapper = snapshotMapper;
        this.cardGenerator = new BingoCardGenerator();
        this.patternEvaluator = new BingoPatternEvaluator();
        this.idempotencyService = idempotencyService;
        this.requestHashService = requestHashService;
        this.objectMapper = objectMapper;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional(readOnly = true)
    public List<BingoRoomResponse> listRooms() {
        return bingoRoomRepository.findByActiveTrueOrderByNameAsc().stream().map(this::toRoomResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<BingoRoomResponse> listAdminRooms(String query, Boolean active, Pageable pageable) {
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
        Long roomId = null;
        if (normalizedQuery != null) {
            try {
                roomId = Long.valueOf(normalizedQuery);
            } catch (NumberFormatException ignored) {
                roomId = null;
            }
        }
        return bingoRoomRepository.searchAdminRooms(normalizedQuery, roomId, active, pageable)
                .map(this::toRoomResponse);
    }

    @Transactional(readOnly = true)
    public BingoRoomResponse getRoom(Long roomId) {
        return toRoomResponse(requireRoom(roomId));
    }

    @Transactional(readOnly = true)
    public GameSessionResponse getSession(Long sessionId) {
        GameSession session = requireSession(sessionId);
        return toSessionResponse(session);
    }

    @Transactional(readOnly = true)
    public TicketResponse getAdminTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("TICKET_NOT_FOUND", "Ticket not found"));
        return toTicketResponse(ticket);
    }

    @Transactional(readOnly = true)
    public WinClaimResponse getAdminWinClaim(Long claimId) {
        WinClaim claim = winClaimRepository.findById(claimId)
                .orElseThrow(() -> new NotFoundException("WIN_CLAIM_NOT_FOUND", "Win claim not found"));
        return toWinClaimResponse(claim);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> getMyTickets(Long sessionId, BingoUserPrincipal principal, Pageable pageable) {
        User player = requirePlayer(principal);
        requireSession(sessionId);
        return ticketRepository.findBySessionIdAndOwnerId(sessionId, player.getId(), pageable)
                .map(this::toTicketResponse);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> getAdminPlayerTickets(Long playerId, Pageable pageable) {
        return ticketRepository.findByOwnerIdOrderByPurchasedAtDesc(playerId, pageable)
                .map(this::toTicketResponse);
    }

    @Transactional(readOnly = true)
    public Page<WinClaimResponse> getAdminPlayerWinClaims(Long playerId, Pageable pageable) {
        return winClaimRepository.findByClaimantIdOrderByCreatedAtDesc(playerId, pageable)
                .map(this::toWinClaimResponse);
    }

    @Transactional(readOnly = true)
    public List<DrawEventResponse> getDrawsAfter(Long sessionId, Integer afterSequence) {
        requireSession(sessionId);
        List<DrawEvent> draws = afterSequence == null
                ? drawEventRepository.findBySessionIdOrderBySequenceNoAsc(sessionId)
                : drawEventRepository.findBySessionIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(sessionId,
                        afterSequence);
        return draws.stream().map(this::toDrawResponse).toList();
    }

    @Transactional
    public BingoRoomResponse createRoom(BingoRoomRequest request) {
        BingoRoom room = new BingoRoom();
        room.setName(request.name().trim());
        room.setDescription(request.description());
        room.setTicketPrice(MoneyValues.money(request.ticketPrice()));
        room.setCurrency(request.currency().trim().toUpperCase());
        room.setTimezone(request.timezone().trim());
        room.setScheduleRule(request.scheduleRule().trim());
        room.setSessionCapacity(
                request.sessionCapacity() > 0 ? request.sessionCapacity() : gameProperties.sessionCapacity());
        room.setMaxTicketsPerPlayer(request.maxTicketsPerPlayer() > 0 ? request.maxTicketsPerPlayer()
                : gameProperties.maxTicketsPerPlayer());
        room.setTicketSalesCloseOnSessionStart(request.ticketSalesCloseOnSessionStart());
        room.setAllowMultipleWinnersPerPrize(request.allowMultipleWinnersPerPrize());
        room.setActive(request.active());
        room.setConfiguredPatternsJson(writePatterns(request.configuredPatterns()));
        room.setConfiguredPrizesJson(writePrizes(request.configuredPrizes()));
        return toRoomResponse(bingoRoomRepository.save(room));
    }

    @Transactional
    public BingoRoomResponse updateRoom(Long roomId, BingoRoomRequest request) {
        BingoRoom room = requireRoom(roomId);
        room.setName(request.name().trim());
        room.setDescription(request.description());
        room.setTicketPrice(MoneyValues.money(request.ticketPrice()));
        room.setCurrency(request.currency().trim().toUpperCase());
        room.setTimezone(request.timezone().trim());
        room.setScheduleRule(request.scheduleRule().trim());
        room.setSessionCapacity(
                request.sessionCapacity() > 0 ? request.sessionCapacity() : gameProperties.sessionCapacity());
        room.setMaxTicketsPerPlayer(request.maxTicketsPerPlayer() > 0 ? request.maxTicketsPerPlayer()
                : gameProperties.maxTicketsPerPlayer());
        room.setTicketSalesCloseOnSessionStart(request.ticketSalesCloseOnSessionStart());
        room.setAllowMultipleWinnersPerPrize(request.allowMultipleWinnersPerPrize());
        room.setActive(request.active());
        room.setConfiguredPatternsJson(writePatterns(request.configuredPatterns()));
        room.setConfiguredPrizesJson(writePrizes(request.configuredPrizes()));
        return toRoomResponse(room);
    }

    @Transactional
    public GameSessionResponse createSession(Long roomId, GameSessionRequest request) {
        BingoRoom room = requireRoom(roomId);
        if (gameSessionRepository.findByRoomIdAndScheduledStartAt(roomId, request.scheduledStartAt()).isPresent()) {
            throw new ConflictException("SESSION_ALREADY_EXISTS",
                    "A session already exists for that room and start time");
        }

        GameSession session = new GameSession();
        session.setRoom(room);
        session.setSessionName(request.sessionName().trim());
        session.setState(request.state() == null ? GameSessionState.DRAFT : request.state());
        session.setScheduledStartAt(request.scheduledStartAt());
        session.setTicketPriceSnapshot(
                MoneyValues.money(request.ticketPrice() != null ? request.ticketPrice() : room.getTicketPrice()));
        session.setCurrencySnapshot(
                request.currency() != null ? request.currency().trim().toUpperCase() : room.getCurrency());
        session.setSessionCapacitySnapshot(
                request.sessionCapacity() != null ? request.sessionCapacity() : room.getSessionCapacity());
        session.setMaxTicketsPerPlayerSnapshot(
                request.maxTicketsPerPlayer() != null ? request.maxTicketsPerPlayer() : room.getMaxTicketsPerPlayer());
        session.setTicketSalesCloseOnSessionStart(
                request.ticketSalesCloseOnSessionStart() != null ? request.ticketSalesCloseOnSessionStart()
                        : room.isTicketSalesCloseOnSessionStart());
        session.setAllowMultipleWinnersPerPrize(
                request.allowMultipleWinnersPerPrize() != null ? request.allowMultipleWinnersPerPrize()
                        : room.isAllowMultipleWinnersPerPrize());
        session.setPatternsSnapshotJson(writePatterns(
                request.configuredPatterns() != null ? request.configuredPatterns() : roomPatterns(room)));
        if (request.configuredPrizes() != null) {
            session.setPrizesSnapshotJson(writePrizes(request.configuredPrizes()));
        } else {
            session.setPrizesSnapshotJson(writePrizeConfigs(roomPrizes(room)));
        }
        return toSessionResponse(gameSessionRepository.save(session));
    }

    @Transactional
    public GameSessionResponse updateSessionState(Long sessionId, GameSessionStateUpdateRequest request) {
        GameSession session = gameSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new NotFoundException("SESSION_NOT_FOUND", "Game session not found"));
        GameSessionState current = session.getState();
        GameSessionState next = request.state();
        if (!current.canTransitionTo(next)) {
            throw new ConflictException("INVALID_SESSION_STATE_TRANSITION",
                    "Cannot transition session from " + current + " to " + next);
        }
        session.setState(next);
        if (next == GameSessionState.STARTED) {
            session.setStartedAt(Instant.now());
        }
        if (next == GameSessionState.CLOSED || next == GameSessionState.SETTLED || next == GameSessionState.CANCELLED) {
            session.setEndedAt(Instant.now());
        }
        return toSessionResponse(session);
    }

    @Transactional
    public IdempotentResult<TicketPurchaseResponse> purchaseTicket(Long sessionId, BingoUserPrincipal principal,
            String idempotencyKey) {
        User player = requirePlayer(principal);
        GameSession session = gameSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new NotFoundException("SESSION_NOT_FOUND", "Game session not found"));
        BingoRoom room = session.getRoom();
        String requestHash = requestHashService.hash("POST", "/api/v1/sessions/" + sessionId + "/tickets",
                player.getId());
        String operationId = UUID.randomUUID().toString();

        return idempotencyService.execute(player.getId(), idempotencyKey, requestHash, operationId,
                HttpStatus.CREATED.value(), TicketPurchaseResponse.class, () -> {
                    validateTicketSaleOpen(session);
                    ensureGameCurrency(session, room);
                    enforceSessionCapacity(session);
                    enforcePlayerTicketLimit(session, player);

                    Wallet wallet = ensureWallet(player);
                    BigDecimal price = MoneyValues.money(session.getTicketPriceSnapshot());
                    BigDecimal transferableBefore = wallet.getAvailableBalance();
                    BigDecimal promotionalBefore = wallet.getPromotionalBalance();
                    BigDecimal balanceBefore = wallet.getTotalBalance();
                    if (balanceBefore.compareTo(price) < 0) {
                        throw new ConflictException("INSUFFICIENT_BALANCE", "Wallet balance cannot go below zero");
                    }

                    BigDecimal promotionalUsed = promotionalBefore.min(price);
                    BigDecimal transferableUsed = MoneyValues.money(price.subtract(promotionalUsed));
                    BigDecimal transferableAfter = MoneyValues.money(transferableBefore.subtract(transferableUsed));
                    BigDecimal promotionalAfter = MoneyValues.money(promotionalBefore.subtract(promotionalUsed));
                    BigDecimal balanceAfter = MoneyValues.money(transferableAfter.add(promotionalAfter));

                    wallet.setAvailableBalance(transferableAfter);
                    wallet.setPromotionalBalance(promotionalAfter);
                    walletRepository.save(wallet);

                    Ticket ticket = new Ticket();
                    ticket.setSession(session);
                    ticket.setOwner(player);
                    ticket.setOperationId(operationId);
                    ticket.setPublicTicketCode(generatePublicTicketCode(sessionId));
                    ticket.setGridJson(writeGrid(new BingoCardGenerator().generate().grid()));
                    ticket.setWalletFundingSnapshotJson(writeFundingSnapshot(transferableUsed, promotionalUsed,
                            transferableBefore, promotionalBefore, transferableAfter, promotionalAfter));
                    ticket = ticketRepository.save(ticket);

                    walletTransactionRepository.save(WalletTransaction.create(
                            wallet,
                            operationId,
                            WalletTransactionType.TICKET_PURCHASE_DEBIT,
                            price,
                            balanceBefore,
                            balanceAfter,
                            wallet.getCurrency(),
                            "BINGO_TICKET",
                            ticket.getId(),
                            "Bingo ticket purchase",
                            transferableBefore,
                            transferableAfter,
                            promotionalBefore,
                            promotionalAfter,
                            ticket.getWalletFundingSnapshotJson(),
                            Instant.now()));

                    return new TicketPurchaseResponse(toTicketResponse(ticket));
                });
    }

    @Transactional
    public TicketPurchaseResponse issueTicketForAgent(Long sessionId, User player, String operationId) {
        GameSession session = gameSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new NotFoundException("SESSION_NOT_FOUND", "Game session not found"));
        BingoRoom room = session.getRoom();

        validateTicketSaleOpen(session);
        ensureGameCurrency(session, room);
        enforceSessionCapacity(session);
        enforcePlayerTicketLimit(session, player);

        Ticket ticket = new Ticket();
        ticket.setSession(session);
        ticket.setOwner(player);
        ticket.setOperationId(operationId);
        ticket.setPublicTicketCode(generatePublicTicketCode(sessionId));
        ticket.setGridJson(writeGrid(new BingoCardGenerator().generate().grid()));
        ticket = ticketRepository.save(ticket);

        return new TicketPurchaseResponse(toTicketResponse(ticket));
    }

    @Transactional
    public TicketPurchaseResponse createAgentTicket(Long sessionId, User player, String operationId) {
        return issueTicketForAgent(sessionId, player, operationId);
    }

    @Transactional(readOnly = true)
    public List<BingoPatternType> getConfiguredPatterns(Long sessionId) {
        return readPatterns(requireSession(sessionId).getPatternsSnapshotJson());
    }

    @Transactional(readOnly = true)
    public List<BingoPrizeConfig> getConfiguredPrizes(Long sessionId) {
        return readPrizes(requireSession(sessionId).getPrizesSnapshotJson());
    }

    @Transactional
    public DrawEventResponse recordDraw(Long sessionId, DrawEventRequest request, BingoUserPrincipal principal) {
        User admin = requireAdmin(principal);
        GameSession session = gameSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new NotFoundException("SESSION_NOT_FOUND", "Game session not found"));
        if (session.getState() != GameSessionState.DRAWING && session.getState() != GameSessionState.STARTED) {
            throw new ConflictException("SESSION_NOT_DRAWING", "Draws can only be recorded while a session is active");
        }

        int sequenceNo = (int) drawEventRepository.countBySessionId(sessionId) + 1;
        if (drawEventRepository.findBySessionIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(sessionId, sequenceNo - 1)
                .stream()
                .anyMatch(draw -> draw.getNumberDrawn() == request.numberDrawn())) {
            throw new ConflictException("DUPLICATE_DRAW_NUMBER", "That number was already drawn for this session");
        }

        DrawEvent draw = new DrawEvent();
        draw.setSession(session);
        if (session.getState() == GameSessionState.STARTED) {
            session.setState(GameSessionState.DRAWING);
        }
        draw.setSequenceNo(sequenceNo);
        draw.setNumberDrawn(request.numberDrawn());
        draw.setDrawnBy(admin);
        draw.setOperationId(UUID.randomUUID().toString());
        draw = drawEventRepository.save(draw);
        closeSessionIfExhausted(sessionId, session);
        applicationEventPublisher.publishEvent(new GameDrawRecordedEvent(toDrawResponse(draw)));
        return toDrawResponse(draw);
    }

    @Transactional
    public IdempotentResult<WinClaimResponse> submitWinClaim(Long ticketId, BingoUserPrincipal principal,
            WinClaimRequest request,
            String idempotencyKey) {
        User player = requirePlayer(principal);
        Ticket ticket = ticketRepository.findByIdAndOwnerId(ticketId, player.getId())
                .orElseThrow(() -> new NotFoundException("TICKET_NOT_FOUND", "Ticket not found"));
        GameSession session = ticket.getSession();
        String requestHash = requestHashService.hash("POST", "/api/v1/tickets/" + ticketId + "/win-claims", request);
        String operationId = UUID.randomUUID().toString();

        return idempotencyService.execute(player.getId(), idempotencyKey, requestHash, operationId,
                HttpStatus.CREATED.value(), WinClaimResponse.class, () -> {
                    if (winClaimRepository.existsByTicketIdAndClaimedPatternAndDrawSequenceSnapshot(ticketId,
                            request.claimedPattern(), currentDrawSequence(session.getId()))) {
                        throw new ConflictException("DUPLICATE_WIN_CLAIM",
                                "A claim already exists for this ticket, pattern, and draw snapshot");
                    }

                    List<Integer> calledNumbers = currentCalledNumbers(session.getId());
                    BingoCard ticketCard = new BingoCard(readGrid(ticket.getGridJson()));
                    boolean matches = patternEvaluator.matches(ticketCard, new HashSet<>(calledNumbers),
                            request.claimedPattern());

                    WinClaim claim = new WinClaim();
                    claim.setTicket(ticket);
                    claim.setSession(session);
                    claim.setClaimant(player);
                    claim.setClaimedPattern(request.claimedPattern());
                    claim.setDrawSequenceSnapshot(currentDrawSequence(session.getId()));
                    claim.setCalledNumbersSnapshotJson(writeIntegerList(calledNumbers));
                    claim.setEvidenceJson(writeEvidence(ticketCard, calledNumbers, request.claimedPattern()));
                    claim.setOperationId(operationId);
                    claim.setServerValidationPassed(matches);
                    if (matches) {
                        claim.setStatus(WinClaimStatus.PENDING_REVIEW);
                    } else {
                        claim.setStatus(WinClaimStatus.FAILED_SERVER_VALIDATION);
                        claim.setServerValidationCode("CLAIM_DOES_NOT_MATCH_DRAWN_NUMBERS");
                        claim.setServerValidationMessage(
                                "The ticket does not satisfy the claimed pattern at the recorded draw sequence");
                    }
                    claim = winClaimRepository.save(claim);
                    return toWinClaimResponse(claim);
                });
    }

    @Transactional(readOnly = true)
    public Page<WinClaimResponse> getMyWinClaims(BingoUserPrincipal principal, Pageable pageable) {
        User player = requirePlayer(principal);
        return winClaimRepository.findByClaimantIdOrderByCreatedAtDesc(player.getId(), pageable)
                .map(this::toWinClaimResponse);
    }

    @Transactional(readOnly = true)
    public Page<WinClaimResponse> getAdminWinClaims(String query, WinClaimStatus status, BingoPatternType pattern,
            Boolean validationPassed, Pageable pageable) {
        String search = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
        Long referenceId = null;
        if (search != null) {
            try {
                referenceId = Long.valueOf(search);
            } catch (NumberFormatException ignored) {
                // Text searches still match claimant name and phone.
            }
        }
        return winClaimRepository.searchAdminClaims(search, referenceId, status, pattern, validationPassed, pageable)
                .map(this::toWinClaimResponse);
    }

    @Transactional
    public WinClaimResponse reviewWinClaim(Long claimId, BingoUserPrincipal principal, WinClaimReviewRequest request,
            boolean approve) {
        User admin = requireAdmin(principal);
        WinClaim claim = winClaimRepository.findByIdForUpdate(claimId)
                .orElseThrow(() -> new NotFoundException("WIN_CLAIM_NOT_FOUND", "Win claim not found"));

        if (!claim.isServerValidationPassed()) {
            throw new ConflictException("SERVER_VALIDATION_FAILED",
                    "Server validation failed for this claim and cannot be overridden");
        }
        if (claim.getStatus() == WinClaimStatus.APPROVED || claim.getStatus() == WinClaimStatus.REJECTED) {
            throw new ConflictException("WIN_CLAIM_ALREADY_REVIEWED", "Win claim has already been reviewed");
        }

        if (!approve) {
            claim.setStatus(WinClaimStatus.REJECTED);
            claim.setReviewedBy(admin);
            claim.setReviewedAt(Instant.now());
            claim.setReviewNote(request.reviewNote());
            return toWinClaimResponse(claim);
        }

        PrizeEntitlement entitlement = createEntitlementForClaim(claim, admin, request.reviewNote());
        claim.setStatus(WinClaimStatus.APPROVED);
        claim.setReviewedBy(admin);
        claim.setReviewedAt(Instant.now());
        claim.setReviewNote(request.reviewNote());
        winClaimRepository.save(claim);
        return toWinClaimResponse(claim, entitlement);
    }

    @Transactional
    public PrizeEntitlementResponse settleEntitlement(Long entitlementId, BingoUserPrincipal principal,
            PrizeEntitlementSettlementRequest request) {
        User reviewer = requireAdmin(principal);
        PrizeEntitlement entitlement = prizeEntitlementRepository.findByIdForUpdate(entitlementId)
                .orElseThrow(() -> new NotFoundException("ENTITLEMENT_NOT_FOUND", "Prize entitlement not found"));
        if (entitlement.getStatus() != PrizeEntitlementStatus.PENDING) {
            throw new ConflictException("ENTITLEMENT_ALREADY_SETTLED", "Prize entitlement was already settled");
        }

        if (entitlement.getSettlementPath() == PrizeSettlementPath.PLAYER_WALLET) {
            Wallet wallet = ensureWallet(entitlement.getClaimant());
            BigDecimal before = wallet.getAvailableBalance();
            BigDecimal after = MoneyValues.money(before.add(entitlement.getPrizeAmount()));
            wallet.setAvailableBalance(after);
            walletRepository.save(wallet);
            walletTransactionRepository.save(WalletTransaction.create(
                    wallet,
                    entitlement.getOperationId(),
                    WalletTransactionType.PRIZE_CREDIT,
                    entitlement.getPrizeAmount(),
                    before,
                    after,
                    wallet.getCurrency(),
                    "PRIZE_ENTITLEMENT",
                    entitlement.getId(),
                    request.settlementNote(),
                    Instant.now()));
        }

        entitlement.setStatus(PrizeEntitlementStatus.SETTLED);
        entitlement.setSettledAt(Instant.now());
        entitlement.setSettledBy(reviewer);
        entitlement.setSettlementReference(request.settlementReference());
        entitlement.setSettlementNote(request.settlementNote());
        prizeEntitlementRepository.save(entitlement);
        return toPrizeEntitlementResponse(entitlement);
    }

    private BingoRoom requireRoom(Long roomId) {
        return bingoRoomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("ROOM_NOT_FOUND", "Bingo room not found"));
    }

    private GameSession requireSession(Long sessionId) {
        return gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("SESSION_NOT_FOUND", "Game session not found"));
    }

    private void validateTicketSaleOpen(GameSession session) {
        if (session.getState() == GameSessionState.CLOSED || session.getState() == GameSessionState.SETTLED
                || session.getState() == GameSessionState.CANCELLED) {
            throw new ConflictException("SESSION_NOT_OPEN", "Ticket sales are closed for this session");
        }
        if (gameProperties.ticketSalesCloseOnSessionStart() && session.getStartedAt() != null) {
            throw new ConflictException("SESSION_ALREADY_STARTED", "Ticket sales close when the session starts");
        }
    }

    private void enforceSessionCapacity(GameSession session) {
        if (ticketRepository.countBySessionId(session.getId()) >= session.getSessionCapacitySnapshot()) {
            throw new ConflictException("SESSION_CAPACITY_REACHED", "Session capacity has been reached");
        }
    }

    private void enforcePlayerTicketLimit(GameSession session, User player) {
        if (ticketRepository.countBySessionIdAndOwnerId(session.getId(), player.getId()) >= session
                .getMaxTicketsPerPlayerSnapshot()) {
            throw new ConflictException("PLAYER_TICKET_LIMIT_REACHED",
                    "Player has reached the ticket limit for this session");
        }
    }

    private void ensureGameCurrency(GameSession session, BingoRoom room) {
        if (!session.getCurrencySnapshot().equalsIgnoreCase(room.getCurrency())) {
            throw new ConflictException("SESSION_CURRENCY_MISMATCH", "Session currency does not match room currency");
        }
    }

    private List<BingoPatternType> roomPatterns(BingoRoom room) {
        return readPatterns(room.getConfiguredPatternsJson());
    }

    private List<BingoPrizeConfig> roomPrizes(BingoRoom room) {
        return readPrizes(room.getConfiguredPrizesJson());
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
            Wallet wallet = Wallet.create(owner, "ETB");
            try {
                return walletRepository.save(wallet);
            } catch (DataIntegrityViolationException exception) {
                return walletRepository.findByOwnerId(owner.getId()).orElseThrow(() -> exception);
            }
        });
    }

    private PrizeEntitlement createEntitlementForClaim(WinClaim claim, User admin, String reviewNote) {
        if (prizeEntitlementRepository.existsByWinClaimId(claim.getId())) {
            throw new ConflictException("ENTITLEMENT_ALREADY_EXISTS", "An entitlement already exists for this claim");
        }

        List<BingoPrizeConfig> configuredPrizes = readPrizes(claim.getSession().getPrizesSnapshotJson());
        BingoPrizeConfig config = configuredPrizes.stream()
                .filter(prize -> prize.prizeType() == claim.getClaimedPattern())
                .findFirst()
                .orElseThrow(() -> new ConflictException("PRIZE_NOT_CONFIGURED",
                        "Prize type is not configured for this session"));

        PrizeEntitlement entitlement = new PrizeEntitlement();
        entitlement.setWinClaim(claim);
        entitlement.setSession(claim.getSession());
        entitlement.setTicket(claim.getTicket());
        entitlement.setClaimant(claim.getClaimant());
        entitlement.setPrizeType(config.prizeType());
        entitlement.setSettlementPath(config.settlementPath());
        entitlement.setStatus(PrizeEntitlementStatus.PENDING);
        entitlement.setPrizeAmount(MoneyValues.money(config.amount()));
        entitlement.setCurrency(claim.getSession().getCurrencySnapshot());
        entitlement.setOperationId(UUID.randomUUID().toString());
        if (config.settlementPath() == PrizeSettlementPath.AGENT_CASH) {
            entitlement.setPayoutCode(generatePayoutCode());
        }
        entitlement.setSettlementNote(reviewNote);
        return prizeEntitlementRepository.save(entitlement);
    }

    private String generatePayoutCode() {
        return "PC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private int currentDrawSequence(Long sessionId) {
        return (int) drawEventRepository.countBySessionId(sessionId);
    }

    private List<Integer> currentCalledNumbers(Long sessionId) {
        return drawEventRepository.findBySessionIdOrderBySequenceNoAsc(sessionId).stream()
                .map(DrawEvent::getNumberDrawn).toList();
    }

    private TicketResponse toTicketResponse(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getSession().getId(),
                ticket.getOwner().getId(),
                ticket.getPublicTicketCode(),
                ticket.getOperationId(),
                readGrid(ticket.getGridJson()),
                ticket.getPurchasedAt());
    }

    private DrawEventResponse toDrawResponse(DrawEvent draw) {
        return new DrawEventResponse(draw.getId(), draw.getSession().getId(), draw.getSequenceNo(),
                draw.getNumberDrawn(), draw.getOperationId(), draw.getDrawnAt());
    }

    private WinClaimResponse toWinClaimResponse(WinClaim claim) {
        return toWinClaimResponse(claim, claim.getEntitlement());
    }

    private WinClaimResponse toWinClaimResponse(WinClaim claim, PrizeEntitlement entitlement) {
        return new WinClaimResponse(
                claim.getId(),
                claim.getTicket().getId(),
                claim.getSession().getId(),
                claim.getClaimant().getId(),
                claim.getClaimedPattern(),
                claim.getStatus(),
                claim.isServerValidationPassed(),
                claim.getServerValidationCode(),
                claim.getServerValidationMessage(),
                claim.getDrawSequenceSnapshot(),
                readIntegerList(claim.getCalledNumbersSnapshotJson()),
                claim.getEvidenceJson(),
                claim.getOperationId(),
                claim.getReviewNote(),
                claim.getReviewedBy() == null ? null : claim.getReviewedBy().getId(),
                claim.getReviewedAt(),
                entitlement == null ? null : entitlement.getId(),
                claim.getCreatedAt(),
                claim.getUpdatedAt());
    }

    private PrizeEntitlementResponse toPrizeEntitlementResponse(PrizeEntitlement entitlement) {
        return new PrizeEntitlementResponse(
                entitlement.getId(),
                entitlement.getWinClaim().getId(),
                entitlement.getSession().getId(),
                entitlement.getTicket().getId(),
                entitlement.getClaimant().getId(),
                entitlement.getPrizeType(),
                entitlement.getSettlementPath(),
                entitlement.getStatus(),
                entitlement.getPrizeAmount(),
                entitlement.getCurrency(),
                entitlement.getOperationId(),
                entitlement.getCreatedAt(),
                entitlement.getSettledAt(),
                entitlement.getSettledBy() == null ? null : entitlement.getSettledBy().getId(),
                entitlement.getSettlementReference(),
                entitlement.getSettlementNote());
    }

    private BingoRoomResponse toRoomResponse(BingoRoom room) {
        return new BingoRoomResponse(
                room.getId(),
                room.getName(),
                room.getDescription(),
                room.getTicketPrice(),
                room.getCurrency(),
                room.getTimezone(),
                room.getScheduleRule(),
                room.getSessionCapacity(),
                room.getMaxTicketsPerPlayer(),
                room.isTicketSalesCloseOnSessionStart(),
                room.isAllowMultipleWinnersPerPrize(),
                room.isActive(),
                readPatterns(room.getConfiguredPatternsJson()),
                readPrizes(room.getConfiguredPrizesJson()),
                room.getCreatedAt(),
                room.getUpdatedAt());
    }

    private GameSessionResponse toSessionResponse(GameSession session) {
        return new GameSessionResponse(
                session.getId(),
                session.getRoom().getId(),
                session.getRoom().getName(),
                session.getSessionName(),
                session.getState(),
                session.getScheduledStartAt(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getTicketPriceSnapshot(),
                session.getCurrencySnapshot(),
                session.getSessionCapacitySnapshot(),
                session.getMaxTicketsPerPlayerSnapshot(),
                session.isTicketSalesCloseOnSessionStart(),
                session.isAllowMultipleWinnersPerPrize(),
                readPatterns(session.getPatternsSnapshotJson()),
                readPrizes(session.getPrizesSnapshotJson()),
                ticketRepository.countBySessionId(session.getId()),
                drawEventRepository.countBySessionId(session.getId()),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }

    private String writePatterns(List<BingoPatternType> patterns) {
        try {
            return objectMapper.writeValueAsString(patterns == null
                    ? List.of(BingoPatternType.LINE, BingoPatternType.TWO_LINES, BingoPatternType.FULL_HOUSE)
                    : patterns);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to write patterns snapshot", exception);
        }
    }

    private String writePrizes(List<BingoPrizeConfigRequest> requests) {
        List<BingoPrizeConfig> configs = new ArrayList<>();
        if (requests != null) {
            for (BingoPrizeConfigRequest request : requests) {
                configs.add(new BingoPrizeConfig(request.prizeType(), MoneyValues.money(request.amount()),
                        request.settlementPath(), request.maxWinners()));
            }
        }
        return writePrizeConfigs(configs);
    }

    private String writePrizeConfigs(List<BingoPrizeConfig> configs) {
        try {
            return objectMapper.writeValueAsString(configs);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to write prizes snapshot", exception);
        }
    }

    private List<BingoPatternType> readPatterns(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<List<BingoPatternType>>() {
            });
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read patterns snapshot", exception);
        }
    }

    private List<BingoPrizeConfig> readPrizes(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<List<BingoPrizeConfig>>() {
            });
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read prizes snapshot", exception);
        }
    }

    private List<List<Integer>> readGrid(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<List<List<Integer>>>() {
            });
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read ticket grid", exception);
        }
    }

    private String writeGrid(List<List<Integer>> grid) {
        try {
            return objectMapper.writeValueAsString(grid);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to write ticket grid", exception);
        }
    }

    private String writeFundingSnapshot(BigDecimal transferableUsed,
            BigDecimal promotionalUsed,
            BigDecimal transferableBefore,
            BigDecimal promotionalBefore,
            BigDecimal transferableAfter,
            BigDecimal promotionalAfter) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "transferableUsed", transferableUsed,
                    "promotionalUsed", promotionalUsed,
                    "transferableBefore", transferableBefore,
                    "promotionalBefore", promotionalBefore,
                    "transferableAfter", transferableAfter,
                    "promotionalAfter", promotionalAfter));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to write wallet funding snapshot", exception);
        }
    }

    private String writeIntegerList(List<Integer> integers) {
        try {
            return objectMapper.writeValueAsString(integers);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to write number list", exception);
        }
    }

    private List<Integer> readIntegerList(String value) {
        try {
            return objectMapper.readValue(value, INTEGER_LIST);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read number list", exception);
        }
    }

    private String writeEvidence(BingoCard card, List<Integer> calledNumbers, BingoPatternType claimedPattern) {
        try {
            return objectMapper.writeValueAsString(new java.util.LinkedHashMap<String, Object>() {
                {
                    put("grid", card.grid());
                    put("calledNumbers", calledNumbers);
                    put("claimedPattern", claimedPattern.name());
                }
            });
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to write claim evidence", exception);
        }
    }

    private String generatePublicTicketCode(Long sessionId) {
        return "TK-" + sessionId + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private void closeSessionIfExhausted(Long sessionId, GameSession session) {
        if (drawEventRepository.countBySessionId(sessionId) >= 75 && session.getState() != GameSessionState.CLOSED
                && session.getState() != GameSessionState.SETTLED && session.getState() != GameSessionState.CANCELLED) {
            session.setState(GameSessionState.CLOSED);
            session.setEndedAt(Instant.now());
        }
    }
}