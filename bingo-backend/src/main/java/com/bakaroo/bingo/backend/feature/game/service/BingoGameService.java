package com.bakaroo.bingo.backend.feature.game.service;

import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import com.bakaroo.bingo.backend.common.exception.ConflictException;
import com.bakaroo.bingo.backend.common.exception.NotFoundException;
import com.bakaroo.bingo.backend.common.idempotency.IdempotentResult;
import com.bakaroo.bingo.backend.common.idempotency.IdempotencyService;
import com.bakaroo.bingo.backend.common.money.MoneyValues;
import com.bakaroo.bingo.backend.common.request.RequestHashService;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.game.config.GameProperties;
import com.bakaroo.bingo.backend.feature.game.dto.BingoPrizeConfigRequest;
import com.bakaroo.bingo.backend.feature.game.dto.BingoRoomRequest;
import com.bakaroo.bingo.backend.feature.game.dto.BingoRoomResponse;
import com.bakaroo.bingo.backend.feature.game.dto.DrawEventResponse;
import com.bakaroo.bingo.backend.feature.game.dto.GameSessionRequest;
import com.bakaroo.bingo.backend.feature.game.dto.GameSessionResponse;
import com.bakaroo.bingo.backend.feature.game.dto.PlayerRoomResponse;
import com.bakaroo.bingo.backend.feature.game.dto.PlayerGameSessionResponse;
import com.bakaroo.bingo.backend.feature.game.dto.GameSessionStateUpdateRequest;
import com.bakaroo.bingo.backend.feature.game.dto.PrizeEntitlementResponse;
import com.bakaroo.bingo.backend.feature.game.dto.PrizeEntitlementSettlementRequest;
import com.bakaroo.bingo.backend.feature.game.dto.TicketPurchaseResponse;
import com.bakaroo.bingo.backend.feature.game.dto.TicketResponse;
import com.bakaroo.bingo.backend.feature.game.dto.PlayerGameHistoryResponse;
import com.bakaroo.bingo.backend.feature.game.dto.SessionTicketLookupResponse;
import com.bakaroo.bingo.backend.feature.game.dto.WinClaimRequest;
import com.bakaroo.bingo.backend.feature.game.dto.WinClaimResponse;
import com.bakaroo.bingo.backend.feature.game.dto.WinClaimReviewRequest;
import com.bakaroo.bingo.backend.feature.game.entity.BingoPatternType;
import com.bakaroo.bingo.backend.feature.game.entity.BingoRoom;
import com.bakaroo.bingo.backend.feature.game.entity.DrawEvent;
import com.bakaroo.bingo.backend.feature.game.entity.GameSession;
import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;
import com.bakaroo.bingo.backend.feature.game.entity.PrizeEntitlement;
import com.bakaroo.bingo.backend.feature.game.entity.PrizeEntitlementStatus;
import com.bakaroo.bingo.backend.feature.game.entity.PrizeSettlementPath;
import com.bakaroo.bingo.backend.feature.game.entity.Ticket;
import com.bakaroo.bingo.backend.feature.game.entity.WinClaim;
import com.bakaroo.bingo.backend.feature.game.entity.WinClaimStatus;
import com.bakaroo.bingo.backend.feature.game.model.BingoPrizeConfig;
import com.bakaroo.bingo.backend.feature.game.model.BingoRoomSnapshot;
import com.bakaroo.bingo.backend.feature.game.model.BingoSessionSnapshot;
import com.bakaroo.bingo.backend.feature.game.repository.BingoRoomRepository;
import com.bakaroo.bingo.backend.feature.game.repository.DrawEventRepository;
import com.bakaroo.bingo.backend.feature.game.repository.GameSessionRepository;
import com.bakaroo.bingo.backend.feature.game.repository.PrizeEntitlementRepository;
import com.bakaroo.bingo.backend.feature.game.repository.TicketRepository;
import com.bakaroo.bingo.backend.feature.game.repository.WinClaimRepository;
import com.bakaroo.bingo.backend.feature.game.websocket.GameLiveDrawService;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.bakaroo.bingo.backend.feature.wallet.entity.Wallet;
import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransaction;
import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransactionType;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BingoGameService {

    private static final TypeReference<List<Integer>> INTEGER_LIST = new TypeReference<>() {
    };
    private static final int GRID_SIZE = 5;

    private final com.bakaroo.bingo.backend.feature.community.service.GameAccessService gameAccess;
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
    private final GameLiveDrawService gameLiveDrawService;
    private final WinClaimSessionValidator winClaimSessionValidator;
    private final GameRefundService refunds;
    private final GameFundingService funding;
    private final GameWinnerService winners;
    private final com.bakaroo.bingo.backend.feature.game.config.GamePolicyProperties policy;

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
            GameLiveDrawService gameLiveDrawService,
            com.bakaroo.bingo.backend.feature.community.service.GameAccessService gameAccess,
            GameFundingService funding, GameWinnerService winners, GameRefundService refunds,
            com.bakaroo.bingo.backend.feature.game.config.GamePolicyProperties policy) {
        this.funding = funding;
        this.refunds = refunds;
        this.winners = winners;
        this.policy = policy;
        this.gameAccess = gameAccess;
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
        this.gameLiveDrawService = gameLiveDrawService;
        this.winClaimSessionValidator = new WinClaimSessionValidator();
    }

    @Transactional(readOnly = true)
    public List<BingoRoomResponse> listRooms() {
        return bingoRoomRepository.findByActiveTrueOrderByNameAsc().stream().map(this::toRoomResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PlayerRoomResponse> listPlayerRooms() {
        return bingoRoomRepository.findByActiveTrueOrderByNameAsc().stream().map(this::toPlayerRoomResponse).toList();
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
    public PlayerRoomResponse getPlayerRoom(Long roomId) {
        BingoRoom room = bingoRoomRepository.findById(roomId)
                .filter(r -> r.isActive() || r.isPrivateRoom())
                .orElseThrow(() -> new NotFoundException("ROOM_NOT_FOUND", "Room not found"));
        gameAccess.requireRoomAccess(roomId);
        return toPlayerRoomResponse(room);
    }

    @Transactional(readOnly = true)
    public List<PlayerGameSessionResponse> listPlayerRoomSessions(Long roomId) {
        bingoRoomRepository.findById(roomId)
                .filter(r -> r.isActive() || r.isPrivateRoom())
                .orElseThrow(() -> new NotFoundException("ROOM_NOT_FOUND", "Room not found"));
        gameAccess.requireRoomAccess(roomId);
        return gameSessionRepository.findByRoomIdOrderByScheduledStartAtDesc(roomId).stream()
                .map(this::toPlayerSessionResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PlayerGameSessionResponse> listAgentTicketSaleSessions() {
        return gameSessionRepository.findOpenPublicRoomSessionsForAgents().stream()
                .filter(session -> ticketRepository.countBySessionId(session.getId())
                        < session.getSessionCapacitySnapshot())
                .map(this::toPlayerSessionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlayerGameSessionResponse getPlayerSession(Long sessionId) {
        GameSession session = requireSession(sessionId);
        if (!session.getRoom().isActive() && !session.getRoom().isPrivateRoom()) {
            throw new NotFoundException("SESSION_NOT_FOUND", "Game session not found");
        }
        gameAccess.requireRoomAccess(session.getRoom().getId());
        return toPlayerSessionResponse(session);
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
        GameSession session = requireSession(sessionId);
        gameAccess.requireRoomAccess(session.getRoom().getId(), principal);
        var eligibility = winningTicketsByPattern(session);
        return ticketRepository.findBySessionIdAndOwnerId(sessionId, player.getId(), pageable)
                .map(ticket -> toTicketResponse(ticket, eligibility));
    }

    @Transactional(readOnly = true)
    public Page<PlayerGameHistoryResponse> getPlayerGameHistory(BingoUserPrincipal principal, Pageable pageable) {
        User player = requirePlayer(principal);
        return ticketRepository.findPlayerGameHistory(player.getId(),
                List.of(GameSessionState.CLOSED, GameSessionState.SETTLED, GameSessionState.CANCELLED), pageable);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> getAdminPlayerTickets(Long playerId, Pageable pageable) {
        return ticketRepository.findByOwnerIdOrderByPurchasedAtDesc(playerId, pageable)
                .map(this::toTicketResponse);
    }

    @Transactional(readOnly = true)
    public Page<SessionTicketLookupResponse> getAdminSessionTickets(Long sessionId, String query, Pageable pageable) {
        if (!gameSessionRepository.existsById(sessionId)) {
            throw new NotFoundException("SESSION_NOT_FOUND", "Session not found");
        }

        String normalizedQuery = query == null || query.isBlank()
                ? null
                : query.trim().toLowerCase(Locale.ROOT);
        boolean ticketIdQuery = normalizedQuery != null && normalizedQuery.matches("#\\d+");
        String phoneQuery = normalizedQuery == null || ticketIdQuery
                ? null
                : normalizedQuery.replaceAll("\\D", "");
        if (phoneQuery != null && phoneQuery.startsWith("0")) {
            phoneQuery = phoneQuery.substring(1);
        }
        if (phoneQuery != null && phoneQuery.isBlank()) {
            phoneQuery = null;
        }

        Long ticketId = null;
        if (ticketIdQuery) {
            try {
                ticketId = Long.valueOf(normalizedQuery.substring(1));
            } catch (NumberFormatException ignored) {
                ticketId = null;
            }
        }

        return ticketRepository.searchBySessionId(sessionId, normalizedQuery, phoneQuery, ticketId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<WinClaimResponse> getAdminPlayerWinClaims(Long playerId, Pageable pageable) {
        return winClaimRepository.findByClaimantIdOrderByCreatedAtDesc(playerId, pageable)
                .map(this::toWinClaimResponse);
    }

    @Transactional(readOnly = true)
    public List<DrawEventResponse> getDrawsAfter(Long sessionId, Integer afterSequence) {
        gameAccess.requireRoomAccess(requireSession(sessionId).getRoom().getId());
        List<DrawEvent> draws = afterSequence == null
                ? drawEventRepository.findBySessionIdOrderBySequenceNoAsc(sessionId)
                : drawEventRepository.findBySessionIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(sessionId,
                        afterSequence);
        return draws.stream().map(this::toDrawResponse).toList();
    }

    @Transactional
    public BingoRoomResponse createRoom(BingoRoomRequest request) {
        validateRoomPrizeConfiguration(request);
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
        room.setConfiguredPatternsJson(writePatterns(request.configuredPrizes().stream()
                .map(BingoPrizeConfigRequest::prizeType)
                .toList()));
        room.setConfiguredPrizesJson(writePrizes(request.configuredPrizes()));
        String actorName = currentActorDisplayName();
        room.setCreatedByDisplayName(actorName);
        room.setUpdatedByDisplayName(actorName);
        return toRoomResponse(bingoRoomRepository.save(room));
    }

    @Transactional
    public BingoRoomResponse updateRoom(Long roomId, BingoRoomRequest request) {
        validateRoomPrizeConfiguration(request);
        BingoRoom room = requireRoom(roomId);
        if (request.version() == null) {
            throw new BadRequestException("ROOM_VERSION_REQUIRED", "Room version is required when updating configuration");
        }
        if (request.version().longValue() != room.getVersion()) {
            throw new ConflictException("ROOM_VERSION_CONFLICT", "Room configuration has changed; reload before updating");
        }
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
        room.setConfiguredPatternsJson(writePatterns(request.configuredPrizes().stream()
                .map(BingoPrizeConfigRequest::prizeType)
                .toList()));
        room.setConfiguredPrizesJson(writePrizes(request.configuredPrizes()));
        room.setUpdatedByDisplayName(currentActorDisplayName());
        bingoRoomRepository.saveAndFlush(room);
        return toRoomResponse(room);
    }

    @Transactional
    public GameSessionResponse createSession(Long roomId, GameSessionRequest request) {
        BingoRoom room = requireRoom(roomId);
        if (!room.isActive()) {
            throw new ConflictException("ROOM_INACTIVE", "Sessions cannot be created for an inactive room");
        }
        if (!request.scheduledStartAt().isAfter(Instant.now())) {
            throw new BadRequestException("SESSION_START_MUST_BE_FUTURE",
                    "Scheduled session start must be in the future");
        }
        if (request.state() != null && request.state() != GameSessionState.DRAFT
                && request.state() != GameSessionState.OPEN) {
            throw new BadRequestException("INVALID_INITIAL_SESSION_STATE",
                    "New sessions must start in DRAFT or OPEN state");
        }
        if (gameSessionRepository.findByRoomIdAndScheduledStartAt(roomId, request.scheduledStartAt()).isPresent()) {
            throw new ConflictException("SESSION_ALREADY_EXISTS",
                    "A session already exists for that room and start time");
        }

        GameSession session = new GameSession();
        session.setMinimumHouseMarginBps(policy.minimumHouseMarginBps());
        session.setSessionCostReserve(MoneyValues.money(policy.sessionCostReserve()));
        session.setClaimWindowSeconds(policy.claimWindow().toSeconds());
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
                        : true);
        session.setAllowMultipleWinnersPerPrize(
                request.allowMultipleWinnersPerPrize() != null ? request.allowMultipleWinnersPerPrize()
                        : true);
        session.setPatternsSnapshotJson(writePatterns(
                request.configuredPatterns() != null ? request.configuredPatterns() : roomPatterns(room)));
        if (request.configuredPrizes() != null) {
            session.setPrizesSnapshotJson(writePrizes(request.configuredPrizes()));
        } else {
            session.setPrizesSnapshotJson(writePrizeConfigs(roomPrizes(room)));
        }
        validateSessionPrizeConfiguration(session);
        try {
            return toSessionResponse(gameSessionRepository.saveAndFlush(session));
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateRoomScheduleViolation(exception)) {
                throw new ConflictException("SESSION_ALREADY_EXISTS",
                        "A session already exists for that room and start time");
            }
            throw exception;
        }
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
        if (next == GameSessionState.CANCELLED) {
            if (drawEventRepository.countBySessionId(sessionId) > 0) {
                throw new ConflictException("SESSION_ALREADY_DRAWN", "A game cannot be cancelled after the first draw");
            }
            if (ticketRepository.countBySessionId(sessionId) > 0) {
                requirePayoutsEnabled();
                refunds.refund(session);
            }
        }
        if (next == GameSessionState.STARTED) {
            validateSessionPrizeConfiguration(session);
            funding.requireFunded(session);
        }
        if (next == GameSessionState.CLOSED) {
            for (BingoPatternType pattern : readPatterns(session.getPatternsSnapshotJson())) {
                if (winners.winners(sessionId, pattern).ticketIds().isEmpty()) {
                    throw new ConflictException("SESSION_PRIZES_INCOMPLETE", "Continue drawing until all configured patterns have winners");
                }
            }
        }
        if (next == GameSessionState.SETTLED) {
            if (session.getClaimWindowSeconds() == null || session.getEndedAt() == null
                    || Instant.now().isBefore(session.getEndedAt().plusSeconds(session.getClaimWindowSeconds()))) {
                throw new ConflictException("CLAIM_WINDOW_OPEN", "The claim window must expire before final settlement");
            }
            if (winClaimRepository.countBySessionIdAndStatus(sessionId, WinClaimStatus.PENDING_REVIEW) > 0
                    || prizeEntitlementRepository.countBySessionIdAndStatus(sessionId, PrizeEntitlementStatus.PENDING) > 0) {
                throw new ConflictException("SESSION_HAS_UNRESOLVED_OBLIGATIONS",
                        "Resolve all pending claims and prizes before settling the session");
            }
        }
        session.setState(next);
        if (next == GameSessionState.STARTED) {
            session.setStartedAt(Instant.now());
        }
        if (session.getEndedAt() == null && (next == GameSessionState.CLOSED || next == GameSessionState.CANCELLED)) {
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
        gameAccess.requireRoomAccess(room.getId(), principal);
        String requestHash = requestHashService.hash("POST", "/api/v1/sessions/" + sessionId + "/tickets",
                player.getId());
        String operationId = UUID.randomUUID().toString();

        return idempotencyService.execute(player.getId(), idempotencyKey, requestHash, operationId,
                HttpStatus.CREATED.value(), TicketPurchaseResponse.class, () -> {
                    validateTicketSaleOpen(session);
                    if (!room.isActive()) {
                        throw new ConflictException("ROOM_INACTIVE", "Ticket sales are closed for this room");
                    }
                    ensureGameCurrency(session, room);
                    enforceSessionCapacity(session);
                    enforcePlayerTicketLimit(session, player);

                    ensureWallet(player);
                    Wallet wallet = walletRepository.findByOwnerIdForUpdate(player.getId())
                            .orElseThrow(() -> new NotFoundException("WALLET_NOT_FOUND", "Player wallet not found"));
                    if (!wallet.getCurrency().equals(session.getCurrencySnapshot())) {
                        throw new ConflictException("CURRENCY_MISMATCH", "Ticket currency must match the funding wallet");
                    }
                    BigDecimal price = MoneyValues.money(session.getTicketPriceSnapshot());
                    BigDecimal transferableBefore = wallet.getAvailableBalance();
                    BigDecimal promotionalBefore = wallet.getPromotionalBalance();
                    if (transferableBefore.compareTo(price) < 0) {
                        throw new ConflictException("INSUFFICIENT_BALANCE", "Available cash balance cannot go below zero");
                    }

                    BigDecimal promotionalUsed = BigDecimal.ZERO.setScale(2);
                    BigDecimal transferableUsed = price;
                    BigDecimal transferableAfter = MoneyValues.money(transferableBefore.subtract(price));
                    BigDecimal promotionalAfter = promotionalBefore;

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
                            transferableBefore,
                            transferableAfter,
                            promotionalBefore,
                            promotionalAfter,
                            wallet.getCurrency(),
                            "BINGO_TICKET",
                            ticket.getId(),
                            "Bingo ticket purchase",
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
        gameAccess.requireRoomAccess(room.getId(), new BingoUserPrincipal(player));
        if (!room.isActive()) throw new ConflictException("ROOM_INACTIVE", "Ticket sales are closed for this room");

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

    public DrawEventResponse triggerAutomaticDraw(Long sessionId, BingoUserPrincipal principal) {
        User operator = requireDrawOperator(principal);
        return gameLiveDrawService.triggerAutomaticDraw(sessionId, Instant.now(), operator);
    }

    @Transactional
    public IdempotentResult<WinClaimResponse> submitWinClaim(Long ticketId, BingoUserPrincipal principal,
            WinClaimRequest request,
            String idempotencyKey) {
        User player = requirePlayer(principal);
        Ticket ticket = ticketRepository.findByIdAndOwnerId(ticketId, player.getId())
                .orElseThrow(() -> new NotFoundException("TICKET_NOT_FOUND", "Ticket not found"));
        Long sessionId = ticket.getSession().getId();
        String requestHash = requestHashService.hash("POST", "/api/v1/tickets/" + ticketId + "/win-claims", request);
        String operationId = UUID.randomUUID().toString();

        return idempotencyService.execute(player.getId(), idempotencyKey, requestHash, operationId,
                HttpStatus.CREATED.value(), WinClaimResponse.class, () -> {
                    // State changes use the same row lock in updateSessionState. The lock makes
                    // cancellation and claim creation serialize on the authoritative session row.
                    GameSession session = gameSessionRepository.findByIdForUpdate(sessionId)
                            .orElseThrow(() -> new NotFoundException("SESSION_NOT_FOUND", "Game session not found"));
                    winClaimSessionValidator.validate(session.getState());
                    if (session.getState() == GameSessionState.CLOSED && (session.getClaimWindowSeconds() == null
                            || session.getEndedAt() == null
                            || !Instant.now().isBefore(session.getEndedAt().plusSeconds(session.getClaimWindowSeconds())))) {
                        throw new ConflictException("CLAIM_WINDOW_EXPIRED", "The claim window for this game has ended");
                    }
                    if (!readPatterns(session.getPatternsSnapshotJson()).contains(request.claimedPattern())) {
                        throw new BadRequestException("PRIZE_NOT_CONFIGURED", "Pattern is not configured for this session");
                    }
                    if (prizeEntitlementRepository.existsByTicketIdAndPrizeType(ticketId, request.claimedPattern())) {
                        throw new ConflictException("TICKET_PRIZE_ALREADY_AWARDED", "This ticket has already received this prize");
                    }

                    if (winClaimRepository.existsByTicketIdAndClaimedPatternAndDrawSequenceSnapshot(ticketId,
                            request.claimedPattern(), currentDrawSequence(sessionId))) {
                        throw new ConflictException("DUPLICATE_WIN_CLAIM",
                                "A claim already exists for this ticket, pattern, and draw snapshot");
                    }

                    List<Integer> calledNumbers = currentCalledNumbers(sessionId);
                    BingoCard ticketCard = new BingoCard(readGrid(ticket.getGridJson()));
                    boolean matches = patternEvaluator.matches(ticketCard, new HashSet<>(calledNumbers),
                            request.claimedPattern());

                    WinClaim claim = new WinClaim();
                    claim.setTicket(ticket);
                    claim.setSession(session);
                    claim.setClaimant(player);
                    claim.setClaimedPattern(request.claimedPattern());
                    claim.setDrawSequenceSnapshot(currentDrawSequence(sessionId));
                    claim.setCalledNumbersSnapshotJson(writeIntegerList(calledNumbers));
                    claim.setEvidenceJson(writeEvidence(ticketCard, calledNumbers, request.claimedPattern()));
                    claim.setOperationId(operationId);
                    if (!matches) {
                        throw new ConflictException("CLAIM_DOES_NOT_MATCH_DRAWN_NUMBERS",
                                "This ticket has not completed the claimed pattern");
                    }
                    // Reject late-completing tickets as well as unmatched cards.
                    winners.share(claim);
                    claim.setServerValidationPassed(true);
                    claim.setStatus(WinClaimStatus.PENDING_REVIEW);
                    claim = winClaimRepository.save(claim);
                    return toWinClaimResponse(claim);
                });
    }

    @Transactional(readOnly = true)
    public Page<WinClaimResponse> getMyWinClaims(BingoUserPrincipal principal, Pageable pageable) {
        User player = requirePlayer(principal);
        return winClaimRepository.findByClaimantIdOrderByCreatedAtDesc(player.getId(), pageable)
                .map(claim -> toWinClaimResponse(claim, claim.getEntitlement(), true));
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

        GameSession lockedSession = gameSessionRepository.findByIdForUpdate(claim.getSession().getId())
                .orElseThrow(() -> new NotFoundException("SESSION_NOT_FOUND", "Game session not found"));
        winClaimSessionValidator.validate(lockedSession.getState());
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

        if (prizeEntitlementRepository.existsByTicketIdAndPrizeType(claim.getTicket().getId(), claim.getClaimedPattern())) {
            throw new ConflictException("TICKET_PRIZE_ALREADY_AWARDED", "This ticket has already received this prize");
        }
        funding.requireFunded(lockedSession);
        winners.share(claim);
        PrizeEntitlement entitlement = createEntitlementForClaim(claim, admin, request.reviewNote());
        claim.setEntitlement(entitlement);
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
        User admin = requireAdmin(principal);
        requirePayoutsEnabled();
        // Match the session -> entitlement -> wallet lock order used by review and finalization.
        Long sessionId = prizeEntitlementRepository.findSessionIdById(entitlementId)
                .orElseThrow(() -> new NotFoundException("ENTITLEMENT_NOT_FOUND", "Prize entitlement not found"));
        GameSession session = gameSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new NotFoundException("SESSION_NOT_FOUND", "Game session not found"));
        PrizeEntitlement entitlement = prizeEntitlementRepository.findByIdForUpdate(entitlementId).orElseThrow();
        if (entitlement.getStatus() == PrizeEntitlementStatus.SETTLED) {
            if (request.settlementReference().trim().equals(entitlement.getSettlementReference())) {
                return toPrizeEntitlementResponse(entitlement);
            }
            throw new ConflictException("PAYOUT_ALREADY_SETTLED", "Prize has already been settled");
        }
        if (entitlement.getStatus() != PrizeEntitlementStatus.PENDING
                || entitlement.getWinClaim().getStatus() != WinClaimStatus.APPROVED) {
            throw new ConflictException("PRIZE_NOT_PAYABLE", "Only pending approved prizes can be paid");
        }
        if (entitlement.getSettlementPath() != PrizeSettlementPath.PLAYER_WALLET) {
            throw new ConflictException("AGENT_PAYOUT_REQUIRED", "Cash prizes must be disbursed through the agent payout workflow");
        }
        winClaimSessionValidator.validate(session.getState());
        funding.requireFunded(session);
        if (entitlement.getPrizeAmount().compareTo(winners.share(entitlement.getWinClaim())) != 0) {
            throw new ConflictException("PRIZE_SHARE_MISMATCH", "The entitlement differs from the authoritative prize share");
        }
        ensureWallet(entitlement.getClaimant());
        Wallet wallet = walletRepository.findByOwnerIdForUpdate(entitlement.getClaimant().getId()).orElseThrow();
        if (!wallet.getCurrency().equals(entitlement.getCurrency())) {
            throw new ConflictException("CURRENCY_MISMATCH", "Prize currency must match the destination wallet");
        }
        BigDecimal before = wallet.getAvailableBalance();
        BigDecimal after = MoneyValues.money(before.add(entitlement.getPrizeAmount()));
        wallet.setAvailableBalance(after);
        walletRepository.save(wallet);
        walletTransactionRepository.save(WalletTransaction.create(wallet, entitlement.getOperationId(),
                WalletTransactionType.PRIZE_CREDIT, entitlement.getPrizeAmount(), before, after,
                wallet.getPromotionalBalance(), wallet.getPromotionalBalance(), wallet.getCurrency(),
                "BINGO_PRIZE", entitlementId, "Bingo prize credit", null, Instant.now()));
        entitlement.setStatus(PrizeEntitlementStatus.SETTLED);
        entitlement.setSettledAt(Instant.now());
        entitlement.setSettledBy(admin);
        entitlement.setSettlementReference(request.settlementReference().trim());
        entitlement.setSettlementNote(request.settlementNote());
        return toPrizeEntitlementResponse(prizeEntitlementRepository.save(entitlement));
    }

    public void validatePrizeForPayment(Long entitlementId) {
        requirePayoutsEnabled();
        PrizeEntitlement reference = prizeEntitlementRepository.findById(entitlementId)
                .orElseThrow(() -> new NotFoundException("ENTITLEMENT_NOT_FOUND", "Prize entitlement not found"));
        GameSession session = gameSessionRepository.findByIdForUpdate(reference.getSession().getId()).orElseThrow();
        PrizeEntitlement entitlement = prizeEntitlementRepository.findByIdForUpdate(entitlementId).orElseThrow();
        winClaimSessionValidator.validate(session.getState());
        funding.requireFunded(session);
        if (entitlement.getWinClaim().getStatus() != WinClaimStatus.APPROVED
                || entitlement.getPrizeAmount().compareTo(winners.share(entitlement.getWinClaim())) != 0) {
            throw new ConflictException("PRIZE_NOT_PAYABLE", "Prize must match an approved authoritative winner share");
        }
    }

    public void requirePayoutsEnabled() {
        if (!policy.payoutsEnabled()) {
            throw new ConflictException("PRIZE_SETTLEMENT_BLOCKED", "Prize payouts are disabled by the operating configuration");
        }
    }


    private BingoRoom requireRoom(Long roomId) {
        return bingoRoomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("ROOM_NOT_FOUND", "Bingo room not found"));
    }

    private boolean isDuplicateRoomScheduleViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraintViolation) {
                String constraintName = constraintViolation.getConstraintName();
                if (constraintName != null && (constraintName.endsWith("uk_bingo_sessions_room_schedule")
                        || constraintName.endsWith("uk_bingo_sessions_room_start"))) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    private GameSession requireSession(Long sessionId) {
        return gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("SESSION_NOT_FOUND", "Game session not found"));
    }

    private void validateTicketSaleOpen(GameSession session) {
        if (session.getState() == GameSessionState.DRAFT
                || session.getState() == GameSessionState.CLOSED
                || session.getState() == GameSessionState.SETTLED
                || session.getState() == GameSessionState.CANCELLED) {
            throw new ConflictException("SESSION_NOT_OPEN", "Ticket sales are closed for this session");
        }
        if (session.getState() != GameSessionState.OPEN || session.getStartedAt() != null) {
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
        return roomPrizes(room).stream()
                .map(BingoPrizeConfig::prizeType)
                .distinct()
                .toList();
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

    private User requireDrawOperator(BingoUserPrincipal principal) {
        User user = principal.getUser();
        if (!user.isActive() || !(user.hasRole(UserRole.ADMIN) || user.hasRole(UserRole.SUPPORT))) {
            throw new AccessDeniedException("An active admin or support account is required to operate game draws");
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
        entitlement.setPrizeAmount(winners.share(claim));
        entitlement.setCurrency(claim.getSession().getCurrencySnapshot());
        entitlement.setOperationId(UUID.randomUUID().toString());
        if (config.settlementPath() == PrizeSettlementPath.AGENT_CASH) {
            entitlement.setPayoutCode(generatePayoutCode());
        }
        entitlement.setSettlementNote(reviewNote);
        return prizeEntitlementRepository.save(entitlement);
    }

    private void validateSessionPrizeConfiguration(GameSession session) {
        List<BingoPatternType> patterns = readPatterns(session.getPatternsSnapshotJson());
        List<BingoPrizeConfig> prizes = readPrizes(session.getPrizesSnapshotJson());
        Set<BingoPatternType> selected = new HashSet<>(patterns);
        Set<BingoPatternType> configured = new HashSet<>();
        if (patterns.isEmpty() || prizes.isEmpty() || selected.size() != patterns.size()) {
            throw new BadRequestException("INVALID_SESSION_PRIZE_CONFIGURATION", "Configure unique patterns and prizes");
        }
        for (BingoPrizeConfig prize : prizes) {
            if (!configured.add(prize.prizeType()) || prize.amount() == null
                    || prize.amount().signum() <= 0 || prize.settlementPath() == null) {
                throw new BadRequestException("INVALID_SESSION_PRIZE_CONFIGURATION", "Each pattern requires one valid prize");
            }
        }
        if (!selected.equals(configured)) {
            throw new BadRequestException("INVALID_SESSION_PRIZE_CONFIGURATION", "Prizes must match the selected patterns");
        }
        BigDecimal minimumPool = BigDecimal.valueOf(session.getSessionCapacitySnapshot(), 2);
        if (prizes.stream().anyMatch(prize -> prize.amount().compareTo(minimumPool) < 0)) {
            throw new BadRequestException("PRIZE_POOL_TOO_SMALL", "Prize pools must allow at least one cent per possible tied ticket");
        }
        // Late sales expose already-called numbers and invalidate earliest-completion eligibility.
        if (!session.isTicketSalesCloseOnSessionStart()) {
            throw new BadRequestException("LATE_TICKET_SALES_NOT_ALLOWED", "Ticket sales must close when the game starts");
        }
    }

    private void validateRoomPrizeConfiguration(BingoRoomRequest request) {
        if (request.configuredPatterns() == null || request.configuredPatterns().isEmpty()
                || request.configuredPrizes() == null || request.configuredPrizes().isEmpty()) {
            throw new BadRequestException("ROOM_PRIZE_CONFIGURATION_REQUIRED",
                    "Select at least one pattern and configure its prize");
        }
        EnumSet<BingoPatternType> selectedPatterns = EnumSet.copyOf(request.configuredPatterns());
        if (selectedPatterns.size() != request.configuredPatterns().size()) {
            throw new BadRequestException("DUPLICATE_ROOM_PATTERN",
                    "Each configured pattern can only be selected once");
        }
        EnumSet<BingoPatternType> prizePatterns = EnumSet.noneOf(BingoPatternType.class);
        for (var prize : request.configuredPrizes()) {
            if (!prizePatterns.add(prize.prizeType())) {
                throw new BadRequestException("DUPLICATE_ROOM_PRIZE_PATTERN",
                        "Only one prize configuration is allowed for each pattern");
            }
        }
        if (!selectedPatterns.equals(prizePatterns)) {
            throw new BadRequestException("ROOM_PRIZES_MUST_MATCH_PATTERNS",
                    "Configure exactly one prize for each selected pattern");
        }
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

    private java.util.Map<BingoPatternType, List<Long>> winningTicketsByPattern(GameSession session) {
        var result = new java.util.EnumMap<BingoPatternType, List<Long>>(BingoPatternType.class);
        if (session.getState() != GameSessionState.STARTED && session.getState() != GameSessionState.DRAWING
                && session.getState() != GameSessionState.CLOSED) return result;
        for (var pattern : readPatterns(session.getPatternsSnapshotJson())) {
            result.put(pattern, winners.winners(session.getId(), pattern).ticketIds());
        }
        return result;
    }

    private TicketResponse toTicketResponse(Ticket ticket) {
        return toTicketResponse(ticket, winningTicketsByPattern(ticket.getSession()));
    }

    private TicketResponse toTicketResponse(Ticket ticket, java.util.Map<BingoPatternType, List<Long>> eligibility) {
        var amounts = new java.util.EnumMap<BingoPatternType, BigDecimal>(BingoPatternType.class);
        for (var prize : readPrizes(ticket.getSession().getPrizesSnapshotJson())) {
            var winningIds = eligibility.getOrDefault(prize.prizeType(), List.of());
            int index = winningIds.indexOf(ticket.getId());
            if (index >= 0) amounts.put(prize.prizeType(), GameWinnerService.split(prize.amount(), winningIds.size(), index));
        }
        return new TicketResponse(
                ticket.getId(), ticket.getSession().getId(), ticket.getOwner().getId(),
                ticket.getPublicTicketCode(), ticket.getOperationId(), readGrid(ticket.getGridJson()),
                ticket.getPurchasedAt(), eligibility.entrySet().stream()
                        .filter(entry -> entry.getValue().contains(ticket.getId()))
                        .map(java.util.Map.Entry::getKey).toList(), amounts, ticket.getSession().getCurrencySnapshot());
    }

    private DrawEventResponse toDrawResponse(DrawEvent draw) {
        return new DrawEventResponse(draw.getId(), draw.getSession().getId(), draw.getSequenceNo(),
                draw.getNumberDrawn(), draw.getOperationId(), draw.getDrawnAt());
    }

    private WinClaimResponse toWinClaimResponse(WinClaim claim) {
        return toWinClaimResponse(claim, claim.getEntitlement());
    }

    private WinClaimResponse toWinClaimResponse(WinClaim claim, PrizeEntitlement entitlement) {
        return toWinClaimResponse(claim, entitlement, false);
    }

    private WinClaimResponse toWinClaimResponse(WinClaim claim, PrizeEntitlement entitlement, boolean ownerView) {
        return new WinClaimResponse(
                claim.getId(),
                claim.getTicket().getId(),
                claim.getSession().getId(),
                claim.getClaimant().getId(),
                claim.getClaimant().getDisplayName(),
                claim.getClaimant().getNormalizedPhone(),
                claim.getTicket().getPublicTicketCode(),
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
                claim.getUpdatedAt(),
                entitlement == null ? null : entitlement.getPrizeAmount(),
                entitlement == null ? null : entitlement.getCurrency(),
                entitlement == null ? null : entitlement.getStatus(),
                ownerView && policy.payoutsEnabled() && entitlement != null
                        && entitlement.getStatus() == PrizeEntitlementStatus.PENDING
                        && entitlement.getSettlementPath() == PrizeSettlementPath.AGENT_CASH
                        ? entitlement.getPayoutCode() : null);
    }

    private PrizeEntitlementResponse toPrizeEntitlementResponse(PrizeEntitlement entitlement) {
        return new PrizeEntitlementResponse(
                entitlement.getId(),
                entitlement.getWinClaim().getId(),
                entitlement.getSession().getId(),
                entitlement.getTicket().getId(),
                entitlement.getClaimant().getId(),
                entitlement.getClaimant().getDisplayName(),
                entitlement.getWinClaim().getClaimedPattern(),
                entitlement.getWinClaim().getStatus(),
                entitlement.getSession().getSessionName(),
                entitlement.getTicket().getPublicTicketCode(),
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
                entitlement.getSettlementNote(), policy.payoutsEnabled());
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
                roomPatterns(room),
                readPrizes(room.getConfiguredPrizesJson()),
                room.getCreatedAt(),
                room.getUpdatedAt(),
                room.getCreatedByDisplayName(),
                room.getUpdatedByDisplayName(),
                room.getVersion());
    }

    private String currentActorDisplayName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof BingoUserPrincipal principal)) {
            return null;
        }
        User actor = principal.getUser();
        return actor == null ? null : actor.getDisplayName();
    }

    private PlayerRoomResponse toPlayerRoomResponse(BingoRoom room) {
        return new PlayerRoomResponse(
                room.getId(),
                room.getName(),
                room.getDescription(),
                room.getTicketPrice(),
                room.getCurrency(),
                room.getTimezone(),
                room.getScheduleRule(),
                room.getSessionCapacity(),
                room.isActive(),
                roomPatterns(room),
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
                session.getUpdatedAt(), session.getMinimumHouseMarginBps(),
                session.getClaimWindowSeconds(), policy.payoutsEnabled(), funding.summarize(session));
    }

    private PlayerGameSessionResponse toPlayerSessionResponse(GameSession session) {
        return new PlayerGameSessionResponse(
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
                readPatterns(session.getPatternsSnapshotJson()),
                ticketRepository.countBySessionId(session.getId()),
                drawEventRepository.countBySessionId(session.getId()),
                session.getCreatedAt(),
                session.getUpdatedAt(), session.getClaimWindowSeconds(),
                readPrizes(session.getPrizesSnapshotJson()), policy.payoutsEnabled());
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
                        request.settlementPath(), 1));
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

}
