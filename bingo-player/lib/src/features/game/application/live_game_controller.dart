import 'dart:async';
import 'dart:math' as math;

import 'package:bingo_player/src/core/config/app_config.dart';
import 'package:bingo_player/src/core/models/page_result.dart';
import 'package:bingo_player/src/core/network/api_error.dart';
import 'package:bingo_player/src/features/auth/data/auth_session_controller.dart';
import 'package:bingo_player/src/features/game/application/live_game_timeline.dart';
import 'package:bingo_player/src/features/game/application/live_game_transport.dart';
import 'package:bingo_player/src/features/game/data/game_repository.dart';
import 'package:bingo_player/src/features/game/domain/live_game_models.dart';
import 'package:bingo_player/src/features/game/domain/ticket_models.dart';
import 'package:bingo_player/src/features/lobby/domain/lobby_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';

const int _ticketPageSize = 10;
const int _claimPageSize = 20;
const Object _stateUnset = Object();

final liveGameControllerProvider = StateNotifierProvider.autoDispose
    .family<LiveGameController, LiveGameState, int>((ref, sessionId) {
      final controller = LiveGameController(
        sessionId: sessionId,
        repository: ref.watch(gameRepositoryProvider),
        readAuthSession: () => ref.read(authSessionControllerProvider),
        clearAuthSession: ({String? reason}) => ref
            .read(authSessionControllerProvider.notifier)
            .clearSession(reason: reason),
        appConfig: ref.watch(appConfigProvider),
        transport: StompLiveGameTransport(),
      );
      unawaited(controller.start());
      ref.onDispose(controller.dispose);
      return controller;
    });

enum ClaimSubmissionOutcome { submitted, alreadySubmitted, notSubmitted }

class PurchaseFlowResult {
  const PurchaseFlowResult({required this.purchasedCount, this.error});

  final int purchasedCount;
  final ApiError? error;
}

enum LiveGameConnectionState {
  idle,
  connecting,
  connected,
  reconnecting,
  paused,
  finished,
  expired,
  disconnected,
}

class LiveGameState {
  const LiveGameState({
    required this.isLoading,
    required this.connectionState,
    required this.ticketPageIndex,
    required this.selectedTicketIndex,
    this.session,
    this.ticketPage,
    this.allTickets = const [],
    this.winningTickets = const [],
    this.claimPage,
    this.draws = const [],
    this.isPurchasingTicket = false,
    this.purchaseOutcomeUncertain = false,
    this.purchaseRecoveryBlocked = false,
    this.isSubmittingClaim = false,
    this.pendingPurchaseIdempotencyKey,
    this.pendingPurchaseAccountId,
    this.selectedClaimPattern,
    this.pendingClaimIdempotencyKey,
    this.infoMessage,
    this.errorMessage,
    this.apiError,
  });

  final bool isLoading;
  final LiveGameConnectionState connectionState;
  final GameSession? session;
  final PageResult<TicketResponse>? ticketPage;
  final List<TicketResponse> allTickets;
  final List<TicketResponse> winningTickets;
  final int ticketPageIndex;
  final int selectedTicketIndex;
  final PageResult<WinClaimRecord>? claimPage;
  final List<LiveDrawEvent> draws;
  final bool isPurchasingTicket;
  final bool purchaseOutcomeUncertain;
  final bool purchaseRecoveryBlocked;
  final bool isSubmittingClaim;
  final String? pendingPurchaseIdempotencyKey;
  final int? pendingPurchaseAccountId;
  final String? selectedClaimPattern;
  final String? pendingClaimIdempotencyKey;
  final String? infoMessage;
  final String? errorMessage;
  final ApiError? apiError;

  bool get isTerminalSession {
    final state = session?.state.toUpperCase();
    return state == 'CLOSED' || state == 'SETTLED' || state == 'CANCELLED';
  }

  bool get selectedTicketIsWinner =>
      selectedClaimPattern != null &&
      selectedTicket?.eligibleClaimPatterns.contains(selectedClaimPattern) ==
          true;

  bool get canSubmitClaim => isClaimWindowOpen && selectedTicketIsWinner;

  bool get isClaimWindowOpen {
    final game = session;
    if (game == null) return false;
    if (game.state == 'STARTED' || game.state == 'DRAWING') return true;
    if (game.state != 'CLOSED' ||
        game.endedAt == null ||
        game.claimWindowSeconds == null) {
      return false;
    }
    return DateTime.now().isBefore(
      game.endedAt!.add(Duration(seconds: game.claimWindowSeconds!)),
    );
  }

  bool get canPageBackTickets => ticketPageIndex > 0;

  bool get canPageForwardTickets => ticketPage?.last == false;

  TicketResponse? get selectedTicket {
    final tickets = ticketPage?.content ?? const [];
    if (tickets.isEmpty ||
        selectedTicketIndex < 0 ||
        selectedTicketIndex >= tickets.length) {
      return null;
    }
    return tickets[selectedTicketIndex];
  }

  List<int> get calledNumbers =>
      draws.map((draw) => draw.numberDrawn).toList(growable: false);

  LiveGameState copyWith({
    bool? isLoading,
    LiveGameConnectionState? connectionState,
    GameSession? session,
    Object? ticketPage = _stateUnset,
    List<TicketResponse>? allTickets,
    List<TicketResponse>? winningTickets,
    int? ticketPageIndex,
    int? selectedTicketIndex,
    Object? claimPage = _stateUnset,
    Object? draws = _stateUnset,
    bool? isPurchasingTicket,
    bool? purchaseOutcomeUncertain,
    bool? purchaseRecoveryBlocked,
    bool? isSubmittingClaim,
    Object? pendingPurchaseIdempotencyKey = _stateUnset,
    Object? pendingPurchaseAccountId = _stateUnset,
    Object? selectedClaimPattern = _stateUnset,
    Object? pendingClaimIdempotencyKey = _stateUnset,
    Object? infoMessage = _stateUnset,
    Object? errorMessage = _stateUnset,
    Object? apiError = _stateUnset,
  }) {
    return LiveGameState(
      isLoading: isLoading ?? this.isLoading,
      connectionState: connectionState ?? this.connectionState,
      session: session ?? this.session,
      ticketPage: ticketPage == _stateUnset
          ? this.ticketPage
          : ticketPage as PageResult<TicketResponse>?,
      allTickets: allTickets ?? this.allTickets,
      winningTickets: winningTickets ?? this.winningTickets,
      ticketPageIndex: ticketPageIndex ?? this.ticketPageIndex,
      selectedTicketIndex: selectedTicketIndex ?? this.selectedTicketIndex,
      claimPage: claimPage == _stateUnset
          ? this.claimPage
          : claimPage as PageResult<WinClaimRecord>?,
      draws: draws == _stateUnset ? this.draws : draws as List<LiveDrawEvent>,
      isPurchasingTicket: isPurchasingTicket ?? this.isPurchasingTicket,
      purchaseOutcomeUncertain:
          purchaseOutcomeUncertain ?? this.purchaseOutcomeUncertain,
      purchaseRecoveryBlocked:
          purchaseRecoveryBlocked ?? this.purchaseRecoveryBlocked,
      isSubmittingClaim: isSubmittingClaim ?? this.isSubmittingClaim,
      pendingPurchaseIdempotencyKey:
          pendingPurchaseIdempotencyKey == _stateUnset
          ? this.pendingPurchaseIdempotencyKey
          : pendingPurchaseIdempotencyKey as String?,
      pendingPurchaseAccountId: pendingPurchaseAccountId == _stateUnset
          ? this.pendingPurchaseAccountId
          : pendingPurchaseAccountId as int?,
      selectedClaimPattern: selectedClaimPattern == _stateUnset
          ? this.selectedClaimPattern
          : selectedClaimPattern as String?,
      pendingClaimIdempotencyKey: pendingClaimIdempotencyKey == _stateUnset
          ? this.pendingClaimIdempotencyKey
          : pendingClaimIdempotencyKey as String?,
      infoMessage: infoMessage == _stateUnset
          ? this.infoMessage
          : infoMessage as String?,
      errorMessage: errorMessage == _stateUnset
          ? this.errorMessage
          : errorMessage as String?,
      apiError: apiError == _stateUnset
          ? errorMessage == null
                ? null
                : this.apiError
          : apiError as ApiError?,
    );
  }
}

class LiveGameController extends StateNotifier<LiveGameState> {
  LiveGameController({
    required this.sessionId,
    required LiveGameRepository repository,
    required AuthSessionState Function() readAuthSession,
    required Future<void> Function({String? reason}) clearAuthSession,
    required AppConfig appConfig,
    required LiveGameTransport transport,
    this.reconnectInitialDelay = const Duration(seconds: 1),
    this.reconnectMaxDelay = const Duration(seconds: 30),
  }) : _repository = repository,
       _readAuthSession = readAuthSession,
       _clearAuthSession = clearAuthSession,
       _appConfig = appConfig,
       _transport = transport,
       _timeline = LiveGameTimeline(),
       super(
         const LiveGameState(
           isLoading: true,
           connectionState: LiveGameConnectionState.idle,
           ticketPageIndex: 0,
           selectedTicketIndex: 0,
         ),
       );

  final int sessionId;
  final LiveGameRepository _repository;
  final AuthSessionState Function() _readAuthSession;
  final Future<void> Function({String? reason}) _clearAuthSession;
  final AppConfig _appConfig;
  final LiveGameTransport _transport;
  final LiveGameTimeline _timeline;
  final Duration reconnectInitialDelay;
  final Duration reconnectMaxDelay;
  final Map<int, LiveDrawEvent> _bufferedDraws = <int, LiveDrawEvent>{};
  final Uuid _uuid = const Uuid();
  final Map<String, WinClaimRecord> _submittedClaims = {};

  bool _disposed = false;
  bool _pauseRequested = false;
  bool _recoveringGap = false;
  bool _reconnectInFlight = false;
  bool _connecting = false;
  int _reconnectAttempt = 0;
  Timer? _reconnectTimer;
  Timer? _snapshotTimer;
  bool _syncingSnapshot = false;

  Future<void> start() async {
    if (_disposed || _connecting) {
      return;
    }
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
    _pauseRequested = false;
    await _restorePendingPurchase();
    await _connectAndSync(isResume: false);
  }

  Future<void> resume() async {
    if (_disposed) {
      return;
    }
    if (!state.isTerminalSession) {
      await start();
    }
  }

  Future<void> pause() async {
    _snapshotTimer?.cancel();
    _snapshotTimer = null;
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
    _pauseRequested = true;
    state = state.copyWith(
      connectionState: LiveGameConnectionState.paused,
      infoMessage: null,
    );
    await _transport.disconnect();
  }

  Future<void> refresh() async {
    if (_disposed || _connecting) {
      return;
    }
    await _connectAndSync(isResume: true);
  }

  bool _purchaseFlowInProgress = false;

  Future<ApiError?> purchaseTicket() async {
    final result = await purchaseTickets(1);
    return result.error;
  }

  Future<PurchaseFlowResult> purchaseTickets(int count) async {
    if (_purchaseFlowInProgress || state.isPurchasingTicket) {
      return PurchaseFlowResult(
        purchasedCount: 0,
        error: ApiError(code: 'PURCHASE_IN_PROGRESS'),
      );
    }
    await _restorePurchaseForCurrentAccount();
    if (_purchaseFlowInProgress || state.isPurchasingTicket) {
      return PurchaseFlowResult(
        purchasedCount: 0,
        error: ApiError(code: 'PURCHASE_IN_PROGRESS'),
      );
    }
    if (state.purchaseOutcomeUncertain) {
      return PurchaseFlowResult(
        purchasedCount: 0,
        error:
            state.apiError ??
            ApiError(code: 'PURCHASE_OUTCOME_UNCERTAIN', outcomeUnknown: true),
      );
    }
    if (state.purchaseRecoveryBlocked) {
      return PurchaseFlowResult(
        purchasedCount: 0,
        error: ApiError(code: 'PURCHASE_OUTCOME_UNKNOWN', outcomeUnknown: true),
      );
    }
    if (count < 1 || count > 10 || _disposed || state.isTerminalSession) {
      return const PurchaseFlowResult(purchasedCount: 0);
    }
    _purchaseFlowInProgress = true;
    state = state.copyWith(isPurchasingTicket: true, apiError: null);
    var purchasedCount = 0;
    ApiError? error;
    try {
      for (var index = 0; index < count; index++) {
        error = await _purchaseOne();
        if (error != null) break;
        purchasedCount++;
      }
    } finally {
      _purchaseFlowInProgress = false;
      if (!_disposed) state = state.copyWith(isPurchasingTicket: false);
    }
    return PurchaseFlowResult(purchasedCount: purchasedCount, error: error);
  }

  /// Replays the original key only after an ownership refresh. The backend's
  /// idempotency record returns the original committed purchase without a
  /// second debit; if it never committed, this is the user's explicit retry.
  Future<ApiError?> resolvePendingPurchase() async {
    await _restorePurchaseForCurrentAccount();
    if (!state.purchaseOutcomeUncertain ||
        _purchaseFlowInProgress ||
        state.isPurchasingTicket) {
      return ApiError(code: 'PURCHASE_OUTCOME_UNKNOWN', outcomeUnknown: true);
    }
    if (state.purchaseRecoveryBlocked ||
        state.pendingPurchaseIdempotencyKey == null) {
      return ApiError(code: 'PURCHASE_OUTCOME_UNKNOWN', outcomeUnknown: true);
    }
    _purchaseFlowInProgress = true;
    state = state.copyWith(isPurchasingTicket: true, apiError: null);
    try {
      await _loadSnapshotAndHistory();
      if (_disposed) return null;
      final error = await _purchaseOne();
      return error;
    } finally {
      _purchaseFlowInProgress = false;
      if (!_disposed) state = state.copyWith(isPurchasingTicket: false);
    }
  }

  Future<ApiError?> _purchaseOne() async {
    if (_disposed) {
      return null;
    }
    if (state.isTerminalSession &&
        state.pendingPurchaseIdempotencyKey == null) {
      return ApiError(code: 'SESSION_CLOSED', statusCode: 409);
    }

    final authSession = _readAuthSession().session;
    if (authSession == null || authSession.isExpired) {
      await _expireSession();
      return ApiError(code: 'SESSION_EXPIRED', statusCode: 401);
    }

    final accountId = authSession.user.id;
    final idempotencyKey = state.pendingPurchaseAccountId == accountId
        ? state.pendingPurchaseIdempotencyKey ?? _uuid.v4()
        : _uuid.v4();
    state = state.copyWith(
      isPurchasingTicket: true,
      pendingPurchaseIdempotencyKey: idempotencyKey,
      pendingPurchaseAccountId: accountId,
      infoMessage: null,
      errorMessage: null,
    );

    try {
      await _savePendingPurchaseKey(accountId, idempotencyKey);
      final purchase = await _repository.purchaseTicket(
        sessionId,
        idempotencyKey,
      );
      await _removePendingPurchaseKey(accountId);
      if (_disposed) {
        return null;
      }
      state = state.copyWith(
        isPurchasingTicket: true,
        pendingPurchaseIdempotencyKey: null,
        pendingPurchaseAccountId: null,
        purchaseOutcomeUncertain: false,
        purchaseRecoveryBlocked: false,
        allTickets:
            state.allTickets.any((item) => item.id == purchase.ticket.id)
            ? state.allTickets
            : [...state.allTickets, purchase.ticket],
        infoMessage: 'TICKET_PURCHASED',
      );
      try {
        await _loadSnapshotAndHistory(selectedTicketId: purchase.ticket.id);
      } catch (_) {
        // The purchase response confirms the debit and ownership even if the
        // follow-up list refresh is unavailable.
      }
      if (!_disposed) {
        state = state.copyWith(
          pendingPurchaseIdempotencyKey: null,
          pendingPurchaseAccountId: null,
          purchaseOutcomeUncertain: false,
          purchaseRecoveryBlocked: false,
          infoMessage: 'TICKET_PURCHASED',
        );
      }
      return null;
    } catch (error) {
      if (_isAuthError(error)) {
        await _expireSession();
        state = state.copyWith(
          pendingPurchaseIdempotencyKey: null,
          purchaseOutcomeUncertain: false,
        );
        return normalizeApiError(error);
      }
      final apiError = normalizeApiError(error);
      if (apiError.outcomeUnknown) {
        try {
          await _loadSnapshotAndHistory();
        } catch (_) {
          // Keep the original idempotency key and block new purchases until
          // the player explicitly resolves the outcome.
        }
      } else {
        await _removePendingPurchaseKey(accountId);
        state = state.copyWith(pendingPurchaseAccountId: null);
      }
      state = state.copyWith(
        isPurchasingTicket: true,
        apiError: apiError,
        purchaseOutcomeUncertain: apiError.outcomeUnknown,
        purchaseRecoveryBlocked: false,
      );
      return apiError;
    }
  }

  String _purchaseStorageKey(int accountId) =>
      'player.pendingPurchase.$accountId.$sessionId';

  Future<void> _savePendingPurchaseKey(int accountId, String key) async {
    final storageKey = _purchaseStorageKey(accountId);
    final prefs = await SharedPreferences.getInstance();
    if (!await prefs.setString(storageKey, key)) {
      throw StateError('Unable to save the pending purchase key.');
    }
  }

  Future<void> _removePendingPurchaseKey(int accountId) async {
    final storageKey = _purchaseStorageKey(accountId);
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(storageKey);
    } catch (_) {}
  }

  Future<void> _restorePendingPurchase() async {
    final accountId = _readAuthSession().session?.user.id;
    if (accountId == null) return;
    final storageKey = _purchaseStorageKey(accountId);
    try {
      final prefs = await SharedPreferences.getInstance();
      final key = prefs.getString(storageKey);
      if (key != null && key.isNotEmpty) {
        state = state.copyWith(
          pendingPurchaseIdempotencyKey: key,
          pendingPurchaseAccountId: accountId,
          purchaseOutcomeUncertain: true,
          purchaseRecoveryBlocked: false,
          apiError: ApiError(
            code: 'PURCHASE_OUTCOME_UNKNOWN',
            outcomeUnknown: true,
          ),
        );
      } else {
        state = state.copyWith(
          pendingPurchaseIdempotencyKey: null,
          pendingPurchaseAccountId: accountId,
          purchaseOutcomeUncertain: false,
          purchaseRecoveryBlocked: false,
          apiError: null,
        );
      }
    } catch (_) {
      state = state.copyWith(
        pendingPurchaseIdempotencyKey: null,
        pendingPurchaseAccountId: accountId,
        purchaseOutcomeUncertain: true,
        purchaseRecoveryBlocked: true,
        apiError: ApiError(
          code: 'PURCHASE_OUTCOME_UNKNOWN',
          outcomeUnknown: true,
        ),
      );
    }
  }

  Future<void> _restorePurchaseForCurrentAccount() async {
    final accountId = _readAuthSession().session?.user.id;
    if (accountId != null &&
        state.pendingPurchaseAccountId != null &&
        state.pendingPurchaseAccountId != accountId) {
      await _restorePendingPurchase();
    }
  }

  Future<void> selectTicketPage(int pageIndex) async {
    if (_disposed || pageIndex < 0 || pageIndex == state.ticketPageIndex) {
      return;
    }
    state = state.copyWith(
      ticketPageIndex: pageIndex,
      selectedTicketIndex: 0,
      pendingClaimIdempotencyKey: null,
      errorMessage: null,
    );
    await _loadTicketPage(pageIndex);
  }

  void selectTicketIndex(int index) {
    final tickets = state.ticketPage?.content ?? const [];
    if (index < 0 || index >= tickets.length) {
      return;
    }
    final ticket = tickets[index];
    final currentPattern = state.selectedClaimPattern;
    final claimPattern =
        ticket.eligibleClaimPatterns.isNotEmpty &&
            !ticket.eligibleClaimPatterns.contains(currentPattern)
        ? ticket.eligibleClaimPatterns.first
        : currentPattern;
    state = state.copyWith(
      selectedTicketIndex: index,
      selectedClaimPattern: claimPattern,
      pendingClaimIdempotencyKey: null,
      errorMessage: null,
    );
  }

  Future<bool> selectTicketById(int ticketId) async {
    final knownIndex = state.allTickets.indexWhere(
      (ticket) => ticket.id == ticketId,
    );
    if (knownIndex < 0) return false;
    final targetPage = knownIndex ~/ _ticketPageSize;
    if (targetPage != state.ticketPageIndex) {
      await selectTicketPage(targetPage);
      if (_disposed) return false;
    }
    final pageIndex = (state.ticketPage?.content ?? const <TicketResponse>[])
        .indexWhere((ticket) => ticket.id == ticketId);
    if (pageIndex < 0) return false;
    selectTicketIndex(pageIndex);
    return true;
  }

  void selectClaimPattern(String pattern) {
    state = state.copyWith(
      selectedClaimPattern: pattern,
      pendingClaimIdempotencyKey: null,
      errorMessage: null,
    );
  }

  Future<ClaimSubmissionOutcome> submitClaim() async {
    final selectedTicket = state.selectedTicket;
    final selectedPattern = state.selectedClaimPattern;
    if (selectedTicket == null ||
        selectedPattern == null ||
        !state.canSubmitClaim ||
        state.isSubmittingClaim) {
      return ClaimSubmissionOutcome.notSubmitted;
    }

    final claimKey = '${selectedTicket.id}:$selectedPattern';
    final currentDrawSequence = state.draws.isEmpty
        ? 0
        : state.draws.last.sequenceNo;
    final knownClaims = [
      ...?state.claimPage?.content,
      ..._submittedClaims.values,
    ];
    if (knownClaims.any(
      (claim) =>
          claim.ticketId == selectedTicket.id &&
          claim.claimedPattern == selectedPattern &&
          (claim.serverValidationPassed ||
              claim.drawSequenceSnapshot >= currentDrawSequence),
    )) {
      state = state.copyWith(
        infoMessage: 'CLAIM_ALREADY_SUBMITTED',
        apiError: null,
        errorMessage: null,
      );
      return ClaimSubmissionOutcome.alreadySubmitted;
    }
    final idempotencyKey = state.pendingClaimIdempotencyKey ?? _uuid.v4();
    state = state.copyWith(
      isSubmittingClaim: true,
      pendingClaimIdempotencyKey: idempotencyKey,
      infoMessage: null,
      errorMessage: null,
      apiError: null,
    );

    try {
      final claim = await _repository.submitWinClaim(
        selectedTicket.id,
        selectedPattern,
        idempotencyKey,
      );
      if (!claim.serverValidationPassed) {
        try {
          await _loadSnapshotAndHistory();
          await _refreshClaims();
        } catch (_) {}
        state = state.copyWith(
          isSubmittingClaim: false,
          pendingClaimIdempotencyKey: null,
          apiError: ApiError(
            code:
                claim.serverValidationCode ??
                'CLAIM_DOES_NOT_MATCH_DRAWN_NUMBERS',
          ),
        );
        return ClaimSubmissionOutcome.notSubmitted;
      }
      _submittedClaims[claimKey] = claim;
      // Refresh both ticket eligibility and claim status after the accepted
      // response. The accepted response remains authoritative if refresh fails.
      try {
        await _loadSnapshotAndHistory();
        await _refreshClaims();
      } catch (_) {}
      if (_disposed) return ClaimSubmissionOutcome.notSubmitted;
      state = state.copyWith(
        isSubmittingClaim: false,
        pendingClaimIdempotencyKey: null,
        infoMessage: 'CLAIM_SUBMITTED',
      );
      return ClaimSubmissionOutcome.submitted;
    } catch (error) {
      if (_isAuthError(error)) {
        await _expireSession();
        return ClaimSubmissionOutcome.notSubmitted;
      }
      final apiError = normalizeApiError(error);
      final alreadySubmitted =
          apiError.code == 'DUPLICATE_WIN_CLAIM' ||
          apiError.code == 'TICKET_PRIZE_ALREADY_AWARDED';
      if (alreadySubmitted || apiError.outcomeUnknown) {
        try {
          await _loadSnapshotAndHistory();
          await _refreshClaims();
        } catch (_) {
          // Keep the original idempotency key for an uncertain response.
        }
      }
      state = state.copyWith(
        isSubmittingClaim: false,
        pendingClaimIdempotencyKey: alreadySubmitted ? null : idempotencyKey,
        infoMessage: alreadySubmitted ? 'CLAIM_ALREADY_SUBMITTED' : null,
        apiError: alreadySubmitted ? null : apiError,
      );
      return alreadySubmitted
          ? ClaimSubmissionOutcome.alreadySubmitted
          : ClaimSubmissionOutcome.notSubmitted;
    }
  }

  @override
  void dispose() {
    _snapshotTimer?.cancel();
    if (_disposed) {
      return;
    }
    _disposed = true;
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
    _transport.disconnect();
    super.dispose();
  }

  Future<void> _connectAndSync({required bool isResume}) async {
    if (_disposed || _connecting) {
      return;
    }
    _connecting = true;
    final authSession = _readAuthSession().session;
    final wsUri = _appConfig.liveGameWebSocketUri;
    if (authSession == null || authSession.isExpired) {
      await _expireSession();
      _connecting = false;
      return;
    }
    if (wsUri == null) {
      state = state.copyWith(
        isLoading: false,
        connectionState: LiveGameConnectionState.disconnected,
        errorMessage: 'API_BASE_URL is missing or invalid.',
      );
      _connecting = false;
      return;
    }

    state = state.copyWith(
      isLoading: state.session == null,
      connectionState: isResume
          ? LiveGameConnectionState.reconnecting
          : LiveGameConnectionState.connecting,
      errorMessage: null,
      infoMessage: null,
    );

    try {
      // REST history and claim eligibility must remain available when the live
      // socket cannot connect, including for games that have already ended.
      await _loadSnapshotAndHistory();
      await _drainBufferedDraws();
      if (_disposed || _pauseRequested) return;
      state = state.copyWith(isLoading: false);
      if (state.isTerminalSession) {
        _snapshotTimer?.cancel();
        _snapshotTimer = null;
        state = state.copyWith(
          connectionState: LiveGameConnectionState.finished,
        );
        await _transport.disconnect();
        return;
      }
      _snapshotTimer ??= Timer.periodic(const Duration(seconds: 5), (_) {
        unawaited(_syncSnapshot());
      });
      await _transport.connect(
        uri: wsUri,
        accessToken: authSession.accessToken,
        sessionId: sessionId,
        onDraw: _bufferIncomingDraw,
        onError: _handleTransportError,
        onDisconnected: _handleTransportDisconnected,
      );
      await _drainBufferedDraws();
      if (_disposed) {
        return;
      }
      if (!_transport.isConnected) {
        state = state.copyWith(
          isLoading: false,
          connectionState: LiveGameConnectionState.reconnecting,
        );
        _scheduleReconnect();
        return;
      }
      _reconnectAttempt = 0;
      _reconnectTimer?.cancel();
      _reconnectTimer = null;
      state = state.copyWith(
        isLoading: false,
        connectionState: state.isTerminalSession
            ? LiveGameConnectionState.finished
            : LiveGameConnectionState.connected,
      );
      if (state.isTerminalSession) {
        await _transport.disconnect();
      }
    } catch (error) {
      if (_isAuthError(error)) {
        await _expireSession();
        return;
      }
      if (_disposed || _pauseRequested) {
        return;
      }
      state = state.copyWith(
        isLoading: false,
        connectionState: LiveGameConnectionState.disconnected,
        apiError: normalizeApiError(error),
      );
      _scheduleReconnect();
    } finally {
      _connecting = false;
    }
  }

  Future<void> _syncSnapshot() async {
    if (_disposed || _pauseRequested || _connecting || _syncingSnapshot) return;
    _syncingSnapshot = true;
    try {
      await _loadSnapshotAndHistory();
      await _drainBufferedDraws();
      if (!_disposed && state.isTerminalSession) {
        _snapshotTimer?.cancel();
        _snapshotTimer = null;
        state = state.copyWith(
          connectionState: LiveGameConnectionState.finished,
        );
        await _transport.disconnect();
      }
    } catch (error) {
      if (_isAuthError(error)) await _expireSession();
      // Socket updates and the next poll can recover transient REST failures.
    } finally {
      _syncingSnapshot = false;
    }
  }

  Future<void> _loadSnapshotAndHistory({int? selectedTicketId}) async {
    final session = await _repository.getSession(sessionId);
    if (_disposed) {
      return;
    }
    final ticketPage = await _repository.getMyTickets(
      sessionId,
      page: state.ticketPageIndex,
      size: _ticketPageSize,
    );
    if (_disposed) {
      return;
    }
    var allTickets = state.allTickets;
    final activeSession = !{
      'OPEN',
      'DRAFT',
    }.contains(session.state.toUpperCase());
    if (activeSession || allTickets.length != ticketPage.totalElements) {
      final fetched = <TicketResponse>[];
      for (var page = 0; page * 100 < ticketPage.totalElements; page++) {
        final batch = await _repository.getMyTickets(
          sessionId,
          page: page,
          size: 100,
        );
        if (_disposed) return;
        fetched.addAll(batch.content);
      }
      allTickets = fetched;
    }
    final claimPage = await _repository.getMyWinClaims(
      page: 0,
      size: _claimPageSize,
    );
    if (_disposed) {
      return;
    }
    final missingDraws = await _repository.getDraws(
      sessionId,
      afterSequence: _timeline.lastSequence,
    );
    if (_disposed) {
      return;
    }

    final selectedTicketIndex = selectedTicketId == null
        ? _clampSelectedTicketIndex(ticketPage)
        : _ticketIndexForId(ticketPage, selectedTicketId);

    state = state.copyWith(
      session: session,
      ticketPage: ticketPage,
      allTickets: allTickets,
      winningTickets: {for (final ticket in allTickets) ticket.id: ticket}
          .values
          .where(
            (ticket) =>
                ticket.confirmedPrizes.isNotEmpty &&
                ticket.prizeCurrency.isNotEmpty,
          )
          .toList(),
      selectedTicketIndex: selectedTicketIndex,
      claimPage: claimPage,
      selectedClaimPattern:
          state.selectedClaimPattern ?? _defaultClaimPattern(session),
      infoMessage: null,
      errorMessage: null,
    );

    for (final draw in missingDraws) {
      _bufferedDraws[draw.sequenceNo] = draw;
    }
  }

  Future<void> _loadTicketPage(int pageIndex) async {
    try {
      final ticketPage = await _repository.getMyTickets(
        sessionId,
        page: pageIndex,
        size: _ticketPageSize,
      );
      if (_disposed) {
        return;
      }
      state = state.copyWith(
        ticketPage: ticketPage,
        selectedTicketIndex: _clampSelectedTicketIndex(ticketPage),
        errorMessage: null,
      );
    } catch (error) {
      if (_isAuthError(error)) {
        await _expireSession();
        return;
      }
      state = state.copyWith(apiError: normalizeApiError(error));
    }
  }

  Future<void> _refreshClaims() async {
    final claimPage = await _repository.getMyWinClaims(
      page: 0,
      size: _claimPageSize,
    );
    if (_disposed) {
      return;
    }
    state = state.copyWith(claimPage: claimPage);
  }

  void _bufferIncomingDraw(LiveDrawEvent event) {
    if (_disposed || event.sessionId != sessionId) {
      return;
    }
    if (_timeline.containsSequence(event.sequenceNo) ||
        _bufferedDraws.containsKey(event.sequenceNo)) {
      return;
    }
    _bufferedDraws[event.sequenceNo] = event;
    if (event.sequenceNo > _timeline.lastSequence + 1 && !_recoveringGap) {
      unawaited(_recoverMissingDraws());
    }
    unawaited(_drainBufferedDraws());
  }

  Future<void> _recoverMissingDraws() async {
    if (_disposed || _recoveringGap) {
      return;
    }
    _recoveringGap = true;
    try {
      final missingDraws = await _repository.getDraws(
        sessionId,
        afterSequence: _timeline.lastSequence,
      );
      for (final draw in missingDraws) {
        _bufferedDraws.putIfAbsent(draw.sequenceNo, () => draw);
      }
    } catch (error) {
      if (_isAuthError(error)) {
        await _expireSession();
        return;
      }
      state = state.copyWith(apiError: normalizeApiError(error));
    } finally {
      _recoveringGap = false;
    }
    await _drainBufferedDraws();
  }

  Future<void> _drainBufferedDraws() async {
    if (_disposed) {
      return;
    }
    var changed = false;
    while (_bufferedDraws.containsKey(_timeline.lastSequence + 1)) {
      final next = _bufferedDraws.remove(_timeline.lastSequence + 1);
      if (next == null) {
        break;
      }
      if (_timeline.ingest(next)) {
        changed = true;
      }
    }
    if (changed) {
      state = state.copyWith(
        draws: _timeline.orderedDraws,
        infoMessage: null,
        errorMessage: null,
      );
      // Ask the server for winner eligibility as soon as a call arrives.
      if (!_connecting && !_syncingSnapshot && !state.isTerminalSession) {
        unawaited(_syncSnapshot());
      }
    }
  }

  Future<void> _handleTransportError(Object error) async {
    if (_disposed || _pauseRequested) {
      return;
    }
    if (_isAuthError(error)) {
      await _expireSession();
      return;
    }
    state = state.copyWith(
      connectionState: LiveGameConnectionState.reconnecting,
      apiError: normalizeApiError(error),
    );
    if (_connecting || _reconnectInFlight) {
      _scheduleReconnect();
      return;
    }
    await _attemptReconnect();
  }

  Future<void> _handleTransportDisconnected() async {
    if (_disposed || _pauseRequested || state.isTerminalSession) {
      return;
    }
    state = state.copyWith(
      connectionState: LiveGameConnectionState.reconnecting,
    );
    if (_connecting || _reconnectInFlight) {
      _scheduleReconnect();
      return;
    }
    await _attemptReconnect();
  }

  Future<void> _attemptReconnect() async {
    if (_disposed || _pauseRequested || _reconnectInFlight) {
      return;
    }
    if (_connecting) {
      _scheduleReconnect();
      return;
    }
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
    _reconnectInFlight = true;
    try {
      await _connectAndSync(isResume: true);
    } finally {
      _reconnectInFlight = false;
    }
  }

  void _scheduleReconnect() {
    if (_disposed ||
        _pauseRequested ||
        state.isTerminalSession ||
        _reconnectTimer != null) {
      return;
    }
    final multiplier = 1 << _reconnectAttempt.clamp(0, 5);
    final delayMilliseconds = math.min(
      reconnectMaxDelay.inMilliseconds,
      reconnectInitialDelay.inMilliseconds * multiplier,
    );
    _reconnectAttempt += 1;
    state = state.copyWith(
      connectionState: LiveGameConnectionState.reconnecting,
    );
    _reconnectTimer = Timer(Duration(milliseconds: delayMilliseconds), () {
      _reconnectTimer = null;
      unawaited(_attemptReconnect());
    });
  }

  Future<void> _expireSession() async {
    _snapshotTimer?.cancel();
    _snapshotTimer = null;
    _pauseRequested = true;
    await _transport.disconnect();
    await _clearAuthSession(reason: 'SESSION_EXPIRED');
    if (_disposed) {
      return;
    }
    state = state.copyWith(
      connectionState: LiveGameConnectionState.expired,
      isLoading: false,
    );
  }

  int _clampSelectedTicketIndex(PageResult<TicketResponse> ticketPage) {
    if (ticketPage.content.isEmpty) {
      return 0;
    }
    if (state.selectedTicketIndex < 0) {
      return 0;
    }
    if (state.selectedTicketIndex >= ticketPage.content.length) {
      return ticketPage.content.length - 1;
    }
    return state.selectedTicketIndex;
  }

  int _ticketIndexForId(PageResult<TicketResponse> ticketPage, int ticketId) {
    final index = ticketPage.content.indexWhere(
      (ticket) => ticket.id == ticketId,
    );
    if (index >= 0) {
      return index;
    }
    return _clampSelectedTicketIndex(ticketPage);
  }

  String? _defaultClaimPattern(GameSession session) {
    if (session.configuredPatterns.isEmpty) {
      return null;
    }
    return session.configuredPatterns.first;
  }

  bool _isAuthError(Object error) {
    final apiError = normalizeApiError(error);
    return apiError.statusCode == 401 ||
        apiError.code == 'UNAUTHORIZED' ||
        apiError.code == 'SESSION_EXPIRED';
  }
}
