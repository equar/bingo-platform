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

TicketResponse _ticket() {
  return TicketResponse(
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
  );
}

void main() {
  test(
    'retains the pending claim idempotency key after a failed submit and reuses it on retry',
    () async {
      final repo = _FakeRepo();
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
      expect(controller.state.errorMessage, isNotNull);
      expect(repo.submittedKeys, hasLength(1));
      expect(repo.submittedKeys.first, firstKey);

      await controller.submitClaim();

      expect(repo.submittedKeys, hasLength(2));
      expect(repo.submittedKeys[1], firstKey);
      expect(controller.state.pendingClaimIdempotencyKey, isNull);
      expect(controller.state.infoMessage, 'CLAIM_SUBMITTED');
    },
  );
}

class _FakeRepo implements LiveGameRepository {
  final List<String> submittedKeys = <String>[];
  var _failNext = true;

  @override
  Future<GameSession> getSession(int sessionId) async =>
      throw UnimplementedError();

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
    throw UnimplementedError();
  }

  @override
  Future<TicketPurchaseResponse> purchaseTicket(
    int sessionId,
    String idempotencyKey,
  ) async {
    throw UnimplementedError();
  }

  @override
  Future<WinClaimRecord> submitWinClaim(
    int ticketId,
    String claimedPattern,
    String idempotencyKey,
  ) async {
    submittedKeys.add(idempotencyKey);
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
