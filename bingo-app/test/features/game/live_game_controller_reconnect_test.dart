import 'package:bingo_app/src/core/config/app_config.dart';
import 'package:bingo_app/src/core/models/page_result.dart';
import 'package:bingo_app/src/features/auth/data/auth_models.dart';
import 'package:bingo_app/src/features/auth/data/auth_session_controller.dart';
import 'package:bingo_app/src/features/game/application/live_game_controller.dart';
import 'package:bingo_app/src/features/game/application/live_game_transport.dart';
import 'package:bingo_app/src/features/game/data/game_repository.dart';
import 'package:bingo_app/src/features/game/domain/live_game_models.dart';
import 'package:bingo_app/src/features/game/domain/ticket_models.dart';
import 'package:bingo_app/src/features/lobby/domain/lobby_models.dart';
import 'package:flutter_test/flutter_test.dart';

LiveDrawEvent _draw({required int sequenceNo, required int numberDrawn}) {
  return LiveDrawEvent(
    sessionId: 42,
    sequenceNo: sequenceNo,
    numberDrawn: numberDrawn,
    drawnAt: DateTime.utc(2026, 1, 1),
    id: sequenceNo,
    operationId: 'op-$sequenceNo',
  );
}

PageResult<TicketResponse> _ticketPage() {
  return PageResult<TicketResponse>(
    content: [
      TicketResponse(
        id: 101,
        sessionId: 42,
        ownerId: 7,
        publicTicketCode: 'TICKET-101',
        operationId: 'ticket-op-101',
        grid: const [
          [1, 16, 31, 46, 61],
          [2, 17, 32, 47, 62],
          [3, 18, 0, 48, 63],
          [4, 19, 33, 49, 64],
          [5, 20, 34, 50, 65],
        ],
        purchasedAt: DateTime.utc(2026, 1, 1),
      ),
    ],
    page: 0,
    size: 10,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
  );
}

PageResult<WinClaimRecord> _claimPage() {
  return PageResult<WinClaimRecord>(
    content: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  );
}

GameSession _session({required String state}) {
  return GameSession(
    id: 42,
    roomId: 8,
    roomName: 'Main Hall',
    sessionName: 'Friday Night',
    state: state,
    scheduledStartAt: DateTime.utc(2026, 1, 1, 12),
    startedAt: DateTime.utc(2026, 1, 1, 12),
    endedAt: null,
    ticketPrice: 100,
    currency: 'ETB',
    sessionCapacity: 20,
    maxTicketsPerPlayer: 5,
    ticketSalesCloseOnSessionStart: true,
    allowMultipleWinnersPerPrize: true,
    configuredPatterns: const ['LINE', 'FULL_HOUSE'],
    configuredPrizes: const [],
    ticketCount: 1,
    drawCount: 1,
    createdAt: DateTime.utc(2026, 1, 1),
    updatedAt: DateTime.utc(2026, 1, 1),
  );
}

AuthSessionState _authenticatedState({
  DateTime? expiresAt,
}) {
  return AuthSessionState(
    isLoading: false,
    session: AuthSession(
      accessToken: 'token',
      expiresAt: expiresAt ?? DateTime.utc(2030, 1, 2),
      user: UserProfile(
        id: 7,
        displayName: 'Player',
        phoneNumber: '+251900000000',
        roles: const ['PLAYER'],
        status: 'ACTIVE',
        createdAt: DateTime.utc(2026, 1, 1),
        updatedAt: DateTime.utc(2026, 1, 1),
      ),
    ),
  );
}

void main() {
  test(
    'replays missing draws after a sequence gap and ignores duplicates',
    () async {
      final repo = FakeLiveGameRepository(
        session: _session(state: 'STARTED'),
        ticketPage: _ticketPage(),
        claimPage: _claimPage(),
        drawResponses: {
          0: [_draw(sequenceNo: 1, numberDrawn: 7)],
          1: [
            _draw(sequenceNo: 2, numberDrawn: 18),
            _draw(sequenceNo: 3, numberDrawn: 44),
          ],
        },
      );
      final transport = FakeLiveGameTransport();
      final controller = LiveGameController(
        sessionId: 42,
        repository: repo,
        readAuthSession: () => _authenticatedState(),
        clearAuthSession: ({String? reason}) async {},
        appConfig: const AppConfig(
          apiBaseUrl: 'https://example.com',
          apiBaseUrlLabel: 'API_BASE_URL',
        ),
        transport: transport,
      );

      await controller.start();
      expect(controller.state.draws.map((event) => event.sequenceNo), [1]);

      transport.emitDraw(_draw(sequenceNo: 3, numberDrawn: 44));
      await Future<void>.delayed(Duration.zero);

      expect(controller.state.draws.map((event) => event.sequenceNo), [
        1,
        2,
        3,
      ]);
      expect(repo.drawRequests, [0, 1]);
    },
  );

  test(
    'reconnects after transport disconnect and preserves server claim outcomes',
    () async {
      final submittedClaims = <WinClaimRecord>[];
      final repo = FakeLiveGameRepository(
        session: _session(state: 'STARTED'),
        ticketPage: _ticketPage(),
        claimPage: _claimPage(),
        drawResponses: {
          0: [_draw(sequenceNo: 1, numberDrawn: 7)],
          1: const [],
        },
        onSubmitClaim: (claim) {
          submittedClaims.add(claim);
        },
      );
      final transport = FakeLiveGameTransport();
      final controller = LiveGameController(
        sessionId: 42,
        repository: repo,
        readAuthSession: () => _authenticatedState(),
        clearAuthSession: ({String? reason}) async {},
        appConfig: const AppConfig(
          apiBaseUrl: 'https://example.com',
          apiBaseUrlLabel: 'API_BASE_URL',
        ),
        transport: transport,
      );

      await controller.start();
      expect(transport.connectCount, 1);

      transport.triggerDisconnect();
      await Future<void>.delayed(Duration.zero);

      expect(transport.connectCount, 2);

      controller.selectClaimPattern('FULL_HOUSE');
      await controller.submitClaim();

      expect(submittedClaims, hasLength(1));
      expect(submittedClaims.first.status, 'REJECTED');
      expect(controller.state.claimPage?.content.first.status, 'REJECTED');
      expect(controller.state.pendingClaimIdempotencyKey, isNull);
    },
  );

  test('pauses the live connection in background and resyncs on resume', () async {
    final repo = FakeLiveGameRepository(
      session: _session(state: 'STARTED'),
      ticketPage: _ticketPage(),
      claimPage: _claimPage(),
      drawResponses: {0: const []},
    );
    final transport = FakeLiveGameTransport();
    final controller = LiveGameController(
      sessionId: 42,
      repository: repo,
      readAuthSession: _authenticatedState,
      clearAuthSession: ({String? reason}) async {},
      appConfig: const AppConfig(
        apiBaseUrl: 'https://example.com',
        apiBaseUrlLabel: 'API_BASE_URL',
      ),
      transport: transport,
    );
    addTearDown(controller.dispose);

    await controller.start();
    expect(transport.connectCount, 1);
    expect(transport.isConnected, isTrue);

    await controller.pause();
    expect(controller.state.connectionState, LiveGameConnectionState.paused);
    expect(transport.isConnected, isFalse);

    await controller.resume();
    expect(transport.connectCount, 2);
    expect(controller.state.connectionState, LiveGameConnectionState.connected);
  });

  test('expires the session instead of reconnecting with an expired token', () async {
    final repo = FakeLiveGameRepository(
      session: _session(state: 'STARTED'),
      ticketPage: _ticketPage(),
      claimPage: _claimPage(),
      drawResponses: {0: const []},
    );
    final transport = FakeLiveGameTransport();
    final clearedSessionReasons = <String?>[];
    var authState = _authenticatedState();
    final controller = LiveGameController(
      sessionId: 42,
      repository: repo,
      readAuthSession: () => authState,
      clearAuthSession: ({String? reason}) async {
        clearedSessionReasons.add(reason);
      },
      appConfig: const AppConfig(
        apiBaseUrl: 'https://example.com',
        apiBaseUrlLabel: 'API_BASE_URL',
      ),
      transport: transport,
    );
    addTearDown(controller.dispose);

    await controller.start();
    authState = _authenticatedState(expiresAt: DateTime.utc(2020));

    await controller.pause();
    await controller.resume();

    expect(controller.state.connectionState, LiveGameConnectionState.expired);
    expect(clearedSessionReasons, ['SESSION_EXPIRED']);
    expect(transport.connectCount, 1);
    expect(transport.isConnected, isFalse);
  });
}

class FakeLiveGameRepository implements LiveGameRepository {
  FakeLiveGameRepository({
    required this.session,
    required this.ticketPage,
    required this.claimPage,
    required this.drawResponses,
    this.onSubmitClaim,
  }) : _claims = List<WinClaimRecord>.from(claimPage.content);

  final GameSession session;
  final PageResult<TicketResponse> ticketPage;
  final PageResult<WinClaimRecord> claimPage;
  final Map<int?, List<LiveDrawEvent>> drawResponses;
  final void Function(WinClaimRecord claim)? onSubmitClaim;
  final List<int?> drawRequests = <int?>[];
  final List<WinClaimRecord> _claims;

  @override
  Future<GameSession> getSession(int sessionId) async => session;

  @override
  Future<List<LiveDrawEvent>> getDraws(
    int sessionId, {
    int? afterSequence,
  }) async {
    drawRequests.add(afterSequence);
    return drawResponses[afterSequence] ?? const [];
  }

  @override
  Future<PageResult<WinClaimRecord>> getMyWinClaims({
    required int page,
    required int size,
  }) async {
    return PageResult<WinClaimRecord>(
      content: List<WinClaimRecord>.from(_claims),
      page: page,
      size: size,
      totalElements: _claims.length,
      totalPages: _claims.isEmpty ? 0 : 1,
      first: true,
      last: true,
    );
  }

  @override
  Future<PageResult<TicketResponse>> getMyTickets(
    int sessionId, {
    required int page,
    required int size,
  }) async {
    return ticketPage;
  }

  @override
  Future<TicketPurchaseResponse> purchaseTicket(
    int sessionId,
    String idempotencyKey,
  ) {
    throw UnimplementedError();
  }

  @override
  Future<WinClaimRecord> submitWinClaim(
    int ticketId,
    String claimedPattern,
    String idempotencyKey,
  ) async {
    final claim = WinClaimRecord(
      id: 501,
      ticketId: ticketId,
      sessionId: session.id,
      claimantId: 7,
      claimedPattern: claimedPattern,
      status: 'REJECTED',
      serverValidationPassed: false,
      serverValidationCode: 'CLAIM_DOES_NOT_MATCH_DRAWN_NUMBERS',
      serverValidationMessage:
          'The ticket does not satisfy the claimed pattern at the recorded draw sequence',
      drawSequenceSnapshot: 1,
      calledNumbersSnapshot: const [7],
      evidenceJson: '{}',
      operationId: idempotencyKey,
      reviewNote: null,
      reviewedBy: null,
      reviewedAt: null,
      entitlementId: null,
      createdAt: DateTime.utc(2026, 1, 1),
      updatedAt: DateTime.utc(2026, 1, 1),
    );
    _claims.insert(0, claim);
    onSubmitClaim?.call(claim);
    return claim;
  }
}

class FakeLiveGameTransport implements LiveGameTransport {
  int connectCount = 0;
  bool _isConnected = false;
  void Function(LiveDrawEvent event)? _onDraw;
  void Function(Object error)? _onError;
  void Function()? _onDisconnected;

  @override
  bool get isConnected => _isConnected;

  @override
  Future<void> connect({
    required Uri uri,
    required String accessToken,
    required int sessionId,
    required void Function(LiveDrawEvent event) onDraw,
    required void Function(Object error) onError,
    required void Function() onDisconnected,
  }) async {
    connectCount += 1;
    _isConnected = true;
    _onDraw = onDraw;
    _onError = onError;
    _onDisconnected = onDisconnected;
  }

  @override
  Future<void> disconnect() async {
    _isConnected = false;
  }

  void emitDraw(LiveDrawEvent event) {
    _onDraw?.call(event);
  }

  void triggerDisconnect() {
    _isConnected = false;
    _onDisconnected?.call();
  }

  void triggerError(Object error) {
    _onError?.call(error);
  }
}
