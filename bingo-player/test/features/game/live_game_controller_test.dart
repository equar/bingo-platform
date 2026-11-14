import 'dart:async';

import 'package:bingo_player/src/core/config/app_config.dart';
import 'package:bingo_player/src/core/network/api_error.dart';
import 'package:bingo_player/src/core/models/page_result.dart';
import 'package:bingo_player/src/features/auth/data/auth_models.dart';
import 'package:bingo_player/src/features/auth/data/auth_session_controller.dart';
import 'package:bingo_player/src/features/game/application/live_game_controller.dart';
import 'package:bingo_player/src/features/game/application/live_game_transport.dart';
import 'package:bingo_player/src/features/game/data/game_repository.dart';
import 'package:bingo_player/src/features/game/domain/live_game_models.dart';
import 'package:bingo_player/src/features/game/domain/ticket_models.dart';
import 'package:bingo_player/src/features/lobby/domain/lobby_models.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

GameSession _session() {
  return GameSession(
    id: 42,
    roomId: 8,
    roomName: 'Main Hall',
    sessionName: 'Friday Night',
    state: 'STARTED',
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

AuthSessionState _authState({int userId = 7}) => AuthSessionState(
  isLoading: false,
  session: AuthSession(
    accessToken: 'token',
    expiresAt: DateTime.utc(2030, 1, 1),
    user: UserProfile(
      id: userId,
      displayName: 'Player',
      phoneNumber: '+251900000000',
      roles: const ['PLAYER'],
      status: 'ACTIVE',
      createdAt: DateTime.utc(2026, 1, 1),
      updatedAt: DateTime.utc(2026, 1, 1),
    ),
  ),
);

LiveDrawEvent _draw({required int sequenceNo, required int numberDrawn}) =>
    LiveDrawEvent(
      sessionId: 42,
      sequenceNo: sequenceNo,
      numberDrawn: numberDrawn,
      drawnAt: DateTime.utc(2026, 1, 1),
      id: sequenceNo,
      operationId: 'draw-$sequenceNo',
    );

TicketResponse _ticket() {
  return TicketResponse(
    id: 101,
    sessionId: 42,
    ownerId: 7,
    publicTicketCode: 'TICKET-101',
    eligibleClaimPatterns: const ['LINE', 'FULL_HOUSE'],
    operationId: 'ticket-op-101',
    grid: const [
      [1, 16, 31, 46, 61],
      [2, 17, 32, 47, 62],
      [3, 18, 0, 48, 63],
      [4, 19, 33, 49, 64],
      [5, 20, 34, 50, 65],
    ],
    purchasedAt: DateTime.utc(2026, 1, 1),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => SharedPreferences.setMockInitialValues({}));

  for (final code in ['DUPLICATE_WIN_CLAIM', 'TICKET_PRIZE_ALREADY_AWARDED']) {
    test('server $code becomes an already-submitted result', () async {
      final repo = _FakeRepo()
        ..forcedError = ApiError(code: code, statusCode: 409);
      final controller = LiveGameController(
        sessionId: 42,
        repository: repo,
        readAuthSession: () => const AuthSessionState(isLoading: false),
        clearAuthSession: ({String? reason}) async {},
        appConfig: const AppConfig(
          apiBaseUrl: 'https://example.com',
          apiBaseUrlLabel: 'API_BASE_URL',
        ),
        transport: _NoopTransport(),
      );
      addTearDown(controller.dispose);
      controller.state = LiveGameState(
        isLoading: false,
        connectionState: LiveGameConnectionState.connected,
        session: _session(),
        ticketPageIndex: 0,
        selectedTicketIndex: 0,
        selectedClaimPattern: 'LINE',
        ticketPage: PageResult(
          content: [_ticket()],
          page: 0,
          size: 10,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        ),
      );
      final outcome = await controller.submitClaim();
      expect(outcome, ClaimSubmissionOutcome.alreadySubmitted);
      expect(controller.state.infoMessage, 'CLAIM_ALREADY_SUBMITTED');
      expect(controller.state.apiError, isNull);
      expect(controller.state.isSubmittingClaim, false);
    });
  }

  test(
    'retains the pending claim idempotency key after a failed submit and reuses it on retry',
    () async {
      final repo = _FakeRepo()..ownedTickets.add(_ticket());
      final transport = _NoopTransport();
      final controller = LiveGameController(
        sessionId: 42,
        repository: repo,
        readAuthSession: () => AuthSessionState(
          isLoading: false,
          session: AuthSession(
            accessToken: 'token',
            expiresAt: DateTime.utc(2026, 1, 2),
            user: UserProfile(
              id: 7,
              displayName: 'Player',
              phoneNumber: '+251911000000',
              roles: const ['PLAYER'],
              status: 'ACTIVE',
              createdAt: DateTime.utc(2026, 1, 1),
              updatedAt: DateTime.utc(2026, 1, 1),
            ),
          ),
        ),
        clearAuthSession: ({String? reason}) async {},
        appConfig: const AppConfig(
          apiBaseUrl: 'https://example.com',
          apiBaseUrlLabel: 'API_BASE_URL',
        ),
        transport: transport,
      );

      controller.state = LiveGameState(
        isLoading: false,
        connectionState: LiveGameConnectionState.connected,
        session: _session(),
        ticketPageIndex: 0,
        selectedTicketIndex: 0,
        ticketPage: PageResult<TicketResponse>(
          content: [_ticket()],
          page: 0,
          size: 10,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        ),
        selectedClaimPattern: 'LINE',
      );

      await controller.submitClaim();
      final firstKey = controller.state.pendingClaimIdempotencyKey;
      expect(firstKey, isNotNull);
      expect(controller.state.apiError, isNotNull);
      expect(repo.submittedKeys, hasLength(1));
      expect(repo.submittedKeys.first, firstKey);

      await controller.submitClaim();

      expect(repo.submittedKeys, hasLength(2));
      expect(repo.submittedKeys[1], firstKey);
      expect(controller.state.pendingClaimIdempotencyKey, isNull);
      expect(controller.state.infoMessage, 'CLAIM_SUBMITTED');
      await controller.submitClaim();
      expect(repo.submittedKeys, hasLength(2));
      expect(controller.state.infoMessage, 'CLAIM_ALREADY_SUBMITTED');
      expect(controller.state.apiError, isNull);
    },
  );

  test('returns the ticket-limit error for the purchase dialog', () async {
    final repo = _FakeRepo()
      ..purchaseError = ApiError(
        code: 'PLAYER_TICKET_LIMIT_REACHED',
        statusCode: 409,
      );
    final controller = LiveGameController(
      sessionId: 42,
      repository: repo,
      readAuthSession: () => AuthSessionState(
        isLoading: false,
        session: AuthSession(
          accessToken: 'token',
          expiresAt: DateTime.utc(2027, 1, 2),
          user: UserProfile(
            id: 7,
            displayName: 'Player',
            phoneNumber: '+251911000000',
            roles: const ['PLAYER'],
            status: 'ACTIVE',
            createdAt: DateTime.utc(2026, 1, 1),
            updatedAt: DateTime.utc(2026, 1, 1),
          ),
        ),
      ),
      clearAuthSession: ({String? reason}) async {},
      appConfig: const AppConfig(
        apiBaseUrl: 'https://example.com',
        apiBaseUrlLabel: 'API_BASE_URL',
      ),
      transport: _NoopTransport(),
    );
    addTearDown(controller.dispose);
    controller.state = LiveGameState(
      isLoading: false,
      connectionState: LiveGameConnectionState.connected,
      session: _session(),
      ticketPageIndex: 0,
      selectedTicketIndex: 0,
    );

    final error = await controller.purchaseTicket();

    expect(error?.code, 'PLAYER_TICKET_LIMIT_REACHED');
    expect(controller.state.apiError?.code, 'PLAYER_TICKET_LIMIT_REACHED');
    expect(controller.state.isPurchasingTicket, false);
  });

  test(
    'purchase batch stops on failure and reports confirmed partial success',
    () async {
      final repo = _FakeRepo()
        ..purchaseErrorOnCall = 2
        ..purchaseError = ApiError(
          code: 'INSUFFICIENT_BALANCE',
          statusCode: 409,
        );
      final controller = LiveGameController(
        sessionId: 42,
        repository: repo,
        readAuthSession: _authState,
        clearAuthSession: ({String? reason}) async {},
        appConfig: const AppConfig(
          apiBaseUrl: 'https://example.com',
          apiBaseUrlLabel: 'API_BASE_URL',
        ),
        transport: _NoopTransport(),
      );
      addTearDown(controller.dispose);
      controller.state = LiveGameState(
        isLoading: false,
        connectionState: LiveGameConnectionState.connected,
        session: _session(),
        ticketPageIndex: 0,
        selectedTicketIndex: 0,
      );

      final result = await controller.purchaseTickets(3);

      expect(result.purchasedCount, 1);
      expect(result.error?.code, 'INSUFFICIENT_BALANCE');
      expect(repo.ownedTickets, hasLength(1));
      expect(repo.purchaseKeys, hasLength(2));
      expect(controller.state.isPurchasingTicket, false);
    },
  );

  test(
    'uncertain purchase reconciles with the same backend idempotency key',
    () async {
      final repo = _FakeRepo()
        ..purchaseErrorOnCall = 1
        ..purchaseError = ApiError(
          code: 'OUTCOME_UNKNOWN',
          outcomeUnknown: true,
        )
        ..commitBeforeUncertainError = true;
      final controller = LiveGameController(
        sessionId: 42,
        repository: repo,
        readAuthSession: _authState,
        clearAuthSession: ({String? reason}) async {},
        appConfig: const AppConfig(
          apiBaseUrl: 'https://example.com',
          apiBaseUrlLabel: 'API_BASE_URL',
        ),
        transport: _NoopTransport(),
      );
      addTearDown(controller.dispose);
      controller.state = LiveGameState(
        isLoading: false,
        connectionState: LiveGameConnectionState.connected,
        session: _session(),
        ticketPageIndex: 0,
        selectedTicketIndex: 0,
      );

      final result = await controller.purchaseTickets(2);
      expect(result.purchasedCount, 0);
      expect(result.error?.outcomeUnknown, true);
      expect(controller.state.purchaseOutcomeUncertain, true);
      expect(repo.purchaseKeys, hasLength(1));

      final resolveError = await controller.resolvePendingPurchase();
      expect(resolveError, isNull);
      expect(repo.purchaseKeys, hasLength(2));
      expect(repo.purchaseKeys[1], repo.purchaseKeys.first);
      expect(repo.ownedTickets, hasLength(1));
      expect(controller.state.purchaseOutcomeUncertain, false);
      expect(
        (await SharedPreferences.getInstance()).getString(
          'player.pendingPurchase.7.42',
        ),
        isNull,
      );
    },
  );

  test('concurrent claim taps submit one individual claim', () async {
    final repo = _FakeRepo()
      ..ownedTickets.add(_ticket())
      .._failNext = false
      ..claimGate = Completer<void>();
    final controller = LiveGameController(
      sessionId: 42,
      repository: repo,
      readAuthSession: _authState,
      clearAuthSession: ({String? reason}) async {},
      appConfig: const AppConfig(
        apiBaseUrl: 'https://example.com',
        apiBaseUrlLabel: 'API_BASE_URL',
      ),
      transport: _NoopTransport(),
    );
    addTearDown(controller.dispose);
    controller.state = LiveGameState(
      isLoading: false,
      connectionState: LiveGameConnectionState.connected,
      session: _session(),
      ticketPageIndex: 0,
      selectedTicketIndex: 0,
      ticketPage: PageResult<TicketResponse>(
        content: [_ticket()],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      ),
      selectedClaimPattern: 'LINE',
      draws: [_draw(sequenceNo: 1, numberDrawn: 1)],
    );

    final first = controller.submitClaim();
    await Future<void>.delayed(Duration.zero);
    expect(controller.state.isSubmittingClaim, true);
    expect(await controller.submitClaim(), ClaimSubmissionOutcome.notSubmitted);
    repo.claimGate!.complete();
    expect(await first, ClaimSubmissionOutcome.submitted);
    expect(repo.submittedKeys, hasLength(1));
  });

  test(
    'restores uncertain purchase keys per account and session before purchase',
    () async {
      SharedPreferences.setMockInitialValues({
        'player.pendingPurchase.7.42': 'account-seven-key',
        'player.pendingPurchase.8.42': 'account-eight-key',
      });
      final repo = _FakeRepo();
      LiveGameController buildController(int accountId) => LiveGameController(
        sessionId: 42,
        repository: repo,
        readAuthSession: () => _authState(userId: accountId),
        clearAuthSession: ({String? reason}) async {},
        appConfig: const AppConfig(
          apiBaseUrl: 'https://example.com',
          apiBaseUrlLabel: 'API_BASE_URL',
        ),
        transport: _NoopTransport(),
      );
      final accountSeven = buildController(7);
      final accountEight = buildController(8);
      addTearDown(accountSeven.dispose);
      addTearDown(accountEight.dispose);

      await accountSeven.start();
      await accountEight.start();
      expect(
        accountSeven.state.pendingPurchaseIdempotencyKey,
        'account-seven-key',
      );
      expect(
        accountEight.state.pendingPurchaseIdempotencyKey,
        'account-eight-key',
      );
      expect(accountSeven.state.purchaseOutcomeUncertain, true);
      expect((await accountSeven.purchaseTickets(1)).purchasedCount, 0);
      expect(repo.purchaseKeys, isEmpty);
    },
  );
}

class _FakeRepo implements LiveGameRepository {
  final List<String> submittedKeys = <String>[];
  var _failNext = true;
  ApiError? forcedError;
  ApiError? purchaseError;
  int? purchaseErrorOnCall;
  bool commitBeforeUncertainError = false;
  int _purchaseCalls = 0;
  final List<String> purchaseKeys = [];
  final List<TicketResponse> ownedTickets = [];
  final Map<String, TicketPurchaseResponse> purchaseResponses = {};
  Completer<void>? claimGate;

  @override
  Future<GameSession> getSession(int sessionId) async => _session();

  @override
  Future<List<LiveDrawEvent>> getDraws(
    int sessionId, {
    int? afterSequence,
  }) async => const [];

  @override
  Future<PageResult<WinClaimRecord>> getMyWinClaims({
    required int page,
    required int size,
  }) async {
    return PageResult<WinClaimRecord>(
      content: const [],
      page: page,
      size: size,
      totalElements: 0,
      totalPages: 0,
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
    final start = page * size;
    final end = (start + size).clamp(0, ownedTickets.length);
    final content = start >= ownedTickets.length
        ? <TicketResponse>[]
        : ownedTickets.sublist(start, end);
    final pages = ownedTickets.isEmpty
        ? 0
        : (ownedTickets.length / size).ceil();
    return PageResult<TicketResponse>(
      content: content,
      page: page,
      size: size,
      totalElements: ownedTickets.length,
      totalPages: pages,
      first: page == 0,
      last: page >= pages - 1,
    );
  }

  @override
  Future<TicketPurchaseResponse> purchaseTicket(
    int sessionId,
    String idempotencyKey,
  ) async {
    purchaseKeys.add(idempotencyKey);
    _purchaseCalls++;
    final saved = purchaseResponses[idempotencyKey];
    if (saved != null) return saved;
    final shouldFail =
        purchaseError != null &&
        (purchaseErrorOnCall == null || purchaseErrorOnCall == _purchaseCalls);
    if (shouldFail && !commitBeforeUncertainError) throw purchaseError!;
    final id = 200 + ownedTickets.length;
    final ticket = TicketResponse(
      id: id,
      sessionId: 42,
      ownerId: 7,
      publicTicketCode: 'T-$id',
      operationId: 'operation-$idempotencyKey',
      grid: _ticket().grid,
      purchasedAt: DateTime.utc(2026, 1, 1),
    );
    final response = TicketPurchaseResponse(ticket: ticket);
    ownedTickets.add(ticket);
    purchaseResponses[idempotencyKey] = response;
    if (shouldFail) throw purchaseError!;
    return response;
  }

  @override
  Future<WinClaimRecord> submitWinClaim(
    int ticketId,
    String claimedPattern,
    String idempotencyKey,
  ) async {
    submittedKeys.add(idempotencyKey);
    await claimGate?.future;
    if (forcedError != null) throw forcedError!;
    if (_failNext) {
      _failNext = false;
      throw StateError('temporary failure');
    }
    return WinClaimRecord(
      id: 501,
      ticketId: ticketId,
      sessionId: 42,
      claimantId: 7,
      claimedPattern: claimedPattern,
      status: 'APPROVED',
      serverValidationPassed: true,
      serverValidationCode: null,
      serverValidationMessage: null,
      drawSequenceSnapshot: 1,
      calledNumbersSnapshot: const [1, 2, 3, 4, 5],
      evidenceJson: '{}',
      operationId: idempotencyKey,
      reviewNote: null,
      reviewedBy: null,
      reviewedAt: null,
      entitlementId: null,
      createdAt: DateTime.utc(2026, 1, 1),
      updatedAt: DateTime.utc(2026, 1, 1),
    );
  }
}

class _NoopTransport implements LiveGameTransport {
  @override
  bool get isConnected => true;

  @override
  Future<void> connect({
    required Uri uri,
    required String accessToken,
    required int sessionId,
    required void Function(LiveDrawEvent event) onDraw,
    required void Function(Object error) onError,
    required void Function() onDisconnected,
  }) async {}

  @override
  Future<void> disconnect() async {}
}
