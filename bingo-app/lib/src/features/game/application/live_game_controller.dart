import 'dart:async';

import 'package:bingo_app/src/core/config/app_config.dart';
import 'package:bingo_app/src/core/models/page_result.dart';
import 'package:bingo_app/src/features/auth/data/auth_session_controller.dart';
import 'package:bingo_app/src/features/game/application/live_game_timeline.dart';
import 'package:bingo_app/src/features/game/application/live_game_transport.dart';
import 'package:bingo_app/src/features/game/data/game_repository.dart';
import 'package:bingo_app/src/features/game/domain/live_game_models.dart';
import 'package:bingo_app/src/features/game/domain/ticket_models.dart';
import 'package:bingo_app/src/features/lobby/domain/lobby_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

const int _ticketPageSize = 10;
const int _claimPageSize = 20;
const Object _stateUnset = Object();

final liveGameControllerProvider = StateNotifierProvider.autoDispose.family<LiveGameController, LiveGameState, int>(
  (ref, sessionId) {
    final controller = LiveGameController(
      sessionId: sessionId,
      repository: ref.watch(gameRepositoryProvider),
      readAuthSession: () => ref.read(authSessionControllerProvider),
      clearAuthSession: ({String? reason}) => ref.read(authSessionControllerProvider.notifier).clearSession(reason: reason),
      appConfig: ref.watch(appConfigProvider),
      transport: StompLiveGameTransport(),
    );
    unawaited(controller.start());
    ref.onDispose(controller.dispose);
    return controller;
  },
);

enum LiveGameConnectionState { idle, connecting, connected, reconnecting, paused, finished, expired, disconnected }

class LiveGameState {
  const LiveGameState({
    required this.isLoading,
    required this.connectionState,
    required this.ticketPageIndex,
    required this.selectedTicketIndex,
    this.session,
    this.ticketPage,
    this.claimPage,
    this.draws = const [],
    this.isSubmittingClaim = false,
    this.selectedClaimPattern,
    this.pendingClaimIdempotencyKey,
    this.infoMessage,
    this.errorMessage,
  });

  final bool isLoading;
  final LiveGameConnectionState connectionState;
  final GameSession? session;
  final PageResult<TicketResponse>? ticketPage;
  final int ticketPageIndex;
  final int selectedTicketIndex;
  final PageResult<WinClaimRecord>? claimPage;
  final List<LiveDrawEvent> draws;
  final bool isSubmittingClaim;
  final String? selectedClaimPattern;
  final String? pendingClaimIdempotencyKey;
  final String? infoMessage;
  final String? errorMessage;

  bool get isTerminalSession {
    final state = session?.state.toUpperCase();
    return state == 'CLOSED' || state == 'SETTLED' || state == 'CANCELLED';
  }

  bool get canPageBackTickets => ticketPageIndex > 0;

  bool get canPageForwardTickets => ticketPage?.last == false;

  TicketResponse? get selectedTicket {
    final tickets = ticketPage?.content ?? const [];
    if (tickets.isEmpty || selectedTicketIndex < 0 || selectedTicketIndex >= tickets.length) {
      return null;
    }
    return tickets[selectedTicketIndex];
  }

  List<int> get calledNumbers => draws.map((draw) => draw.numberDrawn).toList(growable: false);

  LiveGameState copyWith({
    bool? isLoading,
    LiveGameConnectionState? connectionState,
    GameSession? session,
    Object? ticketPage = _stateUnset,
    int? ticketPageIndex,
    int? selectedTicketIndex,
    Object? claimPage = _stateUnset,
    Object? draws = _stateUnset,
    bool? isSubmittingClaim,
    Object? selectedClaimPattern = _stateUnset,
    Object? pendingClaimIdempotencyKey = _stateUnset,
    Object? infoMessage = _stateUnset,
    Object? errorMessage = _stateUnset,
  }) {
    return LiveGameState(
      isLoading: isLoading ?? this.isLoading,
      connectionState: connectionState ?? this.connectionState,
      session: session ?? this.session,
      ticketPage: ticketPage == _stateUnset ? this.ticketPage : ticketPage as PageResult<TicketResponse>?,
      ticketPageIndex: ticketPageIndex ?? this.ticketPageIndex,
      selectedTicketIndex: selectedTicketIndex ?? this.selectedTicketIndex,
      claimPage: claimPage == _stateUnset ? this.claimPage : claimPage as PageResult<WinClaimRecord>?,
      draws: draws == _stateUnset ? this.draws : draws as List<LiveDrawEvent>,
      isSubmittingClaim: isSubmittingClaim ?? this.isSubmittingClaim,
      selectedClaimPattern: selectedClaimPattern == _stateUnset
          ? this.selectedClaimPattern
          : selectedClaimPattern as String?,
      pendingClaimIdempotencyKey: pendingClaimIdempotencyKey == _stateUnset
          ? this.pendingClaimIdempotencyKey
          : pendingClaimIdempotencyKey as String?,
      infoMessage: infoMessage == _stateUnset ? this.infoMessage : infoMessage as String?,
      errorMessage: errorMessage == _stateUnset ? this.errorMessage : errorMessage as String?,
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
  })  : _repository = repository,
        _readAuthSession = readAuthSession,
        _clearAuthSession = clearAuthSession,
        _appConfig = appConfig,
        _transport = transport,
        _timeline = LiveGameTimeline(),
        super(const LiveGameState(
          isLoading: true,
          connectionState: LiveGameConnectionState.idle,
          ticketPageIndex: 0,
          selectedTicketIndex: 0,
        ));

  final int sessionId;
  final LiveGameRepository _repository;
  final AuthSessionState Function() _readAuthSession;
  final Future<void> Function({String? reason}) _clearAuthSession;
  final AppConfig _appConfig;
  final LiveGameTransport _transport;
  final LiveGameTimeline _timeline;
  final Map<int, LiveDrawEvent> _bufferedDraws = <int, LiveDrawEvent>{};
  final Uuid _uuid = const Uuid();

  bool _disposed = false;
  bool _pauseRequested = false;
  bool _recoveringGap = false;
  bool _reconnectInFlight = false;

  Future<void> start() async {
    if (_disposed) {
      return;
    }
    _pauseRequested = false;
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
    _pauseRequested = true;
    state = state.copyWith(connectionState: LiveGameConnectionState.paused, infoMessage: null);
    await _transport.disconnect();
  }

  Future<void> refresh() async {
    if (_disposed) {
      return;
    }
    await _connectAndSync(isResume: true);
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
    state = state.copyWith(selectedTicketIndex: index, pendingClaimIdempotencyKey: null, errorMessage: null);
  }

  void selectClaimPattern(String pattern) {
    state = state.copyWith(selectedClaimPattern: pattern, pendingClaimIdempotencyKey: null, errorMessage: null);
  }

  Future<void> submitClaim() async {
    final selectedTicket = state.selectedTicket;
    final selectedPattern = state.selectedClaimPattern;
    if (selectedTicket == null || selectedPattern == null) {
      return;
    }

    final idempotencyKey = state.pendingClaimIdempotencyKey ?? _uuid.v4();
    state = state.copyWith(
      isSubmittingClaim: true,
      pendingClaimIdempotencyKey: idempotencyKey,
      infoMessage: null,
      errorMessage: null,
    );

    try {
      await _repository.submitWinClaim(selectedTicket.id, selectedPattern, idempotencyKey);
      await _refreshClaims();
      state = state.copyWith(
        isSubmittingClaim: false,
        pendingClaimIdempotencyKey: null,
        infoMessage: 'CLAIM_SUBMITTED',
      );
    } catch (error) {
      if (_isAuthError(error)) {
        await _expireSession();
        return;
      }
      state = state.copyWith(
        isSubmittingClaim: false,
        errorMessage: error.toString(),
      );
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _transport.disconnect();
    super.dispose();
  }

  Future<void> _connectAndSync({required bool isResume}) async {
    if (_disposed) {
      return;
    }
    final authSession = _readAuthSession().session;
    final wsUri = _appConfig.liveGameWebSocketUri;
    if (authSession == null || authSession.isExpired) {
      await _expireSession();
      return;
    }
    if (wsUri == null) {
      state = state.copyWith(
        isLoading: false,
        connectionState: LiveGameConnectionState.disconnected,
        errorMessage: 'API_BASE_URL is missing or invalid.',
      );
      return;
    }

    state = state.copyWith(
      isLoading: state.session == null,
      connectionState: isResume ? LiveGameConnectionState.reconnecting : LiveGameConnectionState.connecting,
      errorMessage: null,
      infoMessage: null,
    );

    try {
      await _transport.connect(
        uri: wsUri,
        accessToken: authSession.accessToken,
        sessionId: sessionId,
        onDraw: _bufferIncomingDraw,
        onError: _handleTransportError,
        onDisconnected: _handleTransportDisconnected,
      );
      await _loadSnapshotAndHistory();
      await _drainBufferedDraws();
      if (_disposed) {
        return;
      }
      state = state.copyWith(
        isLoading: false,
        connectionState: state.isTerminalSession ? LiveGameConnectionState.finished : LiveGameConnectionState.connected,
      );
      if (state.isTerminalSession) {
        await _transport.disconnect();
      }
    } catch (error) {
      if (_isAuthError(error)) {
        await _expireSession();
        return;
      }
      state = state.copyWith(
        isLoading: false,
        connectionState: LiveGameConnectionState.disconnected,
        errorMessage: error.toString(),
      );
    }
  }

  Future<void> _loadSnapshotAndHistory() async {
    final session = await _repository.getSession(sessionId);
    final ticketPage = await _repository.getMyTickets(sessionId, page: state.ticketPageIndex, size: _ticketPageSize);
    final claimPage = await _repository.getMyWinClaims(page: 0, size: _claimPageSize);
    final missingDraws = await _repository.getDraws(sessionId, afterSequence: _timeline.lastSequence);

    state = state.copyWith(
      session: session,
      ticketPage: ticketPage,
      selectedTicketIndex: _clampSelectedTicketIndex(ticketPage),
      claimPage: claimPage,
      selectedClaimPattern: state.selectedClaimPattern ?? _defaultClaimPattern(session),
      infoMessage: null,
      errorMessage: null,
    );

    for (final draw in missingDraws) {
      _bufferedDraws[draw.sequenceNo] = draw;
    }
  }

  Future<void> _loadTicketPage(int pageIndex) async {
    try {
      final ticketPage = await _repository.getMyTickets(sessionId, page: pageIndex, size: _ticketPageSize);
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
      state = state.copyWith(errorMessage: error.toString());
    }
  }

  Future<void> _refreshClaims() async {
    final claimPage = await _repository.getMyWinClaims(page: 0, size: _claimPageSize);
    if (_disposed) {
      return;
    }
    state = state.copyWith(claimPage: claimPage);
  }

  void _bufferIncomingDraw(LiveDrawEvent event) {
    if (_disposed || event.sessionId != sessionId) {
      return;
    }
    if (_timeline.containsSequence(event.sequenceNo) || _bufferedDraws.containsKey(event.sequenceNo)) {
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
      final missingDraws = await _repository.getDraws(sessionId, afterSequence: _timeline.lastSequence);
      for (final draw in missingDraws) {
        _bufferedDraws.putIfAbsent(draw.sequenceNo, () => draw);
      }
    } catch (error) {
      if (_isAuthError(error)) {
        await _expireSession();
        return;
      }
      state = state.copyWith(errorMessage: error.toString());
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
      state = state.copyWith(draws: _timeline.orderedDraws, infoMessage: null, errorMessage: null);
    }
  }

  Future<void> _handleTransportError(Object error) async {
    if (_disposed || _pauseRequested || _reconnectInFlight) {
      return;
    }
    if (_isAuthError(error)) {
      await _expireSession();
      return;
    }
    state = state.copyWith(connectionState: LiveGameConnectionState.reconnecting, errorMessage: error.toString());
    await _attemptReconnect();
  }

  Future<void> _handleTransportDisconnected() async {
    if (_disposed || _pauseRequested || state.isTerminalSession || _reconnectInFlight) {
      return;
    }
    state = state.copyWith(connectionState: LiveGameConnectionState.reconnecting);
    await _attemptReconnect();
  }

  Future<void> _attemptReconnect() async {
    if (_disposed || _pauseRequested || _reconnectInFlight) {
      return;
    }
    _reconnectInFlight = true;
    try {
      await _connectAndSync(isResume: true);
    } finally {
      _reconnectInFlight = false;
    }
  }

  Future<void> _expireSession() async {
    _pauseRequested = true;
    await _transport.disconnect();
    await _clearAuthSession(reason: 'SESSION_EXPIRED');
    if (_disposed) {
      return;
    }
    state = state.copyWith(connectionState: LiveGameConnectionState.expired, isLoading: false);
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

  String? _defaultClaimPattern(GameSession session) {
    if (session.configuredPatterns.isEmpty) {
      return null;
    }
    return session.configuredPatterns.first;
  }

  bool _isAuthError(Object error) {
    final text = error.toString().toLowerCase();
    return text.contains('401') || text.contains('unauthorized') || text.contains('forbidden') || text.contains('token') || text.contains('expired');
  }
}
