import 'dart:async';

import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/core/config/app_config.dart';
import 'package:bingo_player/src/features/auth/data/auth_session_controller.dart';
import 'package:bingo_player/src/features/game/application/live_game_controller.dart';
import 'package:bingo_player/src/features/game/application/live_game_transport.dart';
import 'package:bingo_player/src/features/game/data/game_repository.dart';
import 'package:bingo_player/src/features/game/domain/live_game_models.dart';
import 'package:bingo_player/src/features/game/presentation/session_detail_screen.dart';
import 'package:bingo_player/src/features/lobby/data/lobby_api.dart';
import 'package:bingo_player/src/features/lobby/data/lobby_repository.dart';
import 'package:bingo_player/src/features/lobby/domain/lobby_models.dart';
import 'package:bingo_player/src/features/lobby/presentation/lobby_screen.dart';
import 'package:flutter/material.dart';
import 'package:dio/dio.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('lobby lists active sessions', (tester) async {
    final repository = _LobbyRepository();
    await tester.pumpWidget(_app(repository));
    await tester.pumpAndSettle();
    await _openUpcomingGames(tester);

    expect(find.text('QA Scheduled Session'), findsOneWidget);
    expect(find.byKey(const ValueKey('session-details-12')), findsOneWidget);
    expect(find.byKey(const ValueKey('ticket-preview-12')), findsOneWidget);
  });

  testWidgets('cancelled sessions are shown as not playable', (tester) async {
    final repository = _LobbyRepository(sessionState: 'CANCELLED');
    await tester.pumpWidget(
      _app(repository, home: const RoomDetailsScreen(roomId: 6)),
    );
    await tester.pumpAndSettle();

    expect(find.text('Not playable: CANCELLED'), findsOneWidget);
    expect(find.byKey(const ValueKey('buy-ticket-button')), findsNothing);
  });

  testWidgets('active sessions open the live-game screen', (tester) async {
    final repository = _LobbyRepository(sessionState: 'OPEN');
    final navigatorObserver = _RecordingNavigatorObserver();
    final liveController = LiveGameController(
      sessionId: 12,
      repository: _UnusedLiveGameRepository(),
      readAuthSession: () => const AuthSessionState(isLoading: true),
      clearAuthSession: ({String? reason}) async {},
      appConfig: const AppConfig(
        apiBaseUrl: 'http://127.0.0.1:8080',
        apiBaseUrlLabel: 'API_BASE_URL',
      ),
      transport: _UnusedLiveGameTransport(),
    );
    await tester.pumpWidget(
      _app(
        repository,
        liveGameController: liveController,
        navigatorObserver: navigatorObserver,
      ),
    );
    await tester.pumpAndSettle();
    await _openUpcomingGames(tester);
    await tester.tap(find.byKey(const ValueKey('session-details-12')));
    await tester.pumpAndSettle();

    expect(navigatorObserver.pushes, hasLength(2));
    expect(navigatorObserver.pushes.last.isCurrent, isTrue);
    expect(
      find.byType(SessionDetailScreen, skipOffstage: false),
      findsOneWidget,
    );
    expect(find.text('Live game'), findsOneWidget);
  });

  testWidgets(
    'ticket information shows server snapshot and keeps purchase disabled',
    (tester) async {
      final repository = _LobbyRepository();
      await tester.pumpWidget(_app(repository));
      await tester.pumpAndSettle();
      await _openUpcomingGames(tester);
      await tester.tap(find.byKey(const ValueKey('ticket-preview-12')));
      await tester.pumpAndSettle();

      expect(find.textContaining('3.25 ETB'), findsOneWidget);
      expect(find.textContaining('30 / 30'), findsOneWidget);
      expect(
        find.textContaining('This is the card format, not an assigned ticket.'),
        findsOneWidget,
      );
      expect(find.text('1–15'), findsOneWidget);
      expect(find.text('16–30'), findsOneWidget);
      expect(find.text('31–45'), findsOneWidget);
      expect(find.text('46–60'), findsOneWidget);
      expect(find.text('61–75'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('ticket-preview-purchase-disabled')),
        240,
        scrollable: find.byType(Scrollable).last,
      );
      final purchase = tester.widget<FilledButton>(
        find.byKey(const ValueKey('ticket-preview-purchase-disabled')),
      );
      expect(purchase.onPressed, isNull);
      expect(repository.previewRequests, 1);

      await tester.tap(find.byKey(const ValueKey('ticket-preview-refresh')));
      await tester.pumpAndSettle();
      expect(repository.previewRequests, 2);
    },
  );

  testWidgets(
    'ticket information retry recovers and reflects session eligibility',
    (tester) async {
      final repository = _LobbyRepository(
        failPreview: true,
        sessionState: 'DRAFT',
      );
      await tester.pumpWidget(_app(repository));
      await tester.pumpAndSettle();
      await _openUpcomingGames(tester);
      await tester.tap(find.byKey(const ValueKey('ticket-preview-12')));
      await tester.pumpAndSettle();
      expect(
        find.text(
          "Couldn't load ticket information. Check your connection and retry.",
        ),
        findsOneWidget,
      );

      repository.failPreview = false;
      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text('This session is not open for ticket sales (DRAFT).'),
        240,
        scrollable: find.byType(Scrollable).last,
      );
      expect(
        find.text('This session is not open for ticket sales (DRAFT).'),
        findsOneWidget,
      );
      final purchase = tester.widget<FilledButton>(
        find.byKey(const ValueKey('ticket-preview-purchase-disabled')),
      );
      expect(purchase.onPressed, isNull);
    },
  );

  testWidgets('closed and settled sessions are shown as not playable', (
    tester,
  ) async {
    for (final state in ['CLOSED', 'SETTLED']) {
      final repository = _LobbyRepository(sessionState: state);
      await tester.pumpWidget(
        _app(repository, home: const RoomDetailsScreen(roomId: 6)),
      );
      await tester.pumpAndSettle();

      expect(find.text('Not playable: $state'), findsOneWidget);
      expect(find.byKey(const ValueKey('buy-ticket-button')), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpAndSettle();
    }
  });

  testWidgets(
    'a closed session remains accessible while its claim window is open',
    (tester) async {
      final repository = _LobbyRepository(
        sessionState: 'CLOSED',
        endedAt: DateTime.now().toUtc(),
        claimWindowSeconds: 3600,
      );
      await tester.pumpWidget(
        _app(repository, home: const RoomDetailsScreen(roomId: 6)),
      );
      await tester.pumpAndSettle();

      expect(find.byKey(const ValueKey('live-game-12')), findsOneWidget);
    },
  );

  testWidgets('empty active sessions display an appropriate message', (
    tester,
  ) async {
    final repository = _LobbyRepository(emptySessions: true);
    await tester.pumpWidget(_app(repository));
    await tester.pumpAndSettle();
    await _openUpcomingGames(tester);

    expect(find.text('No upcoming games are scheduled.'), findsOneWidget);
  });

  testWidgets('active session load error offers retry', (tester) async {
    final repository = _LobbyRepository(failSessions: true);
    await tester.pumpWidget(_app(repository));
    await tester.pumpAndSettle();
    expect(
      find.text("Couldn't load rooms. Check your connection and retry."),
      findsOneWidget,
    );
    expect(find.text('Try again'), findsOneWidget);

    repository.failSessions = false;
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    await _openUpcomingGames(tester);
    expect(find.text('QA Scheduled Session'), findsOneWidget);
  });

  testWidgets('pending retry cannot be submitted again', (tester) async {
    final repository = _LobbyRepository(failSessions: true);
    await tester.pumpWidget(_app(repository));
    await tester.pumpAndSettle();
    expect(find.text('Try again'), findsOneWidget);

    repository.failSessions = false;
    repository.pendingSessions = Completer<List<GameSession>>();
    await tester.tap(find.text('Try again'));
    await tester.pump();

    expect(repository.sessionRequests, 2);
    expect(find.text('Try again'), findsNothing);
    expect(find.text('Loading...'), findsOneWidget);

    repository.pendingSessions!.complete(
      await repository.getRoomSessions(_room.id),
    );
    await tester.pumpAndSettle();
    await _openUpcomingGames(tester);
    expect(repository.sessionRequests, 2);
    expect(find.text('QA Scheduled Session'), findsOneWidget);
    expect(find.text('Try again'), findsNothing);
  });
}

Widget _app(
  _LobbyRepository repository, {
  LiveGameController? liveGameController,
  NavigatorObserver? navigatorObserver,
  Widget home = const LobbyScreen(),
}) => ProviderScope(
  overrides: [
    lobbyRepositoryProvider.overrideWithValue(repository),
    if (liveGameController != null)
      liveGameControllerProvider(12).overrideWith((ref) => liveGameController),
  ],
  child: MaterialApp(
    navigatorObservers: [if (navigatorObserver != null) navigatorObserver],
    localizationsDelegates: const [
      AppLocalizations.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: AppLocalizations.supportedLocales,
    home: home,
  ),
);

Future<void> _openUpcomingGames(WidgetTester tester) async {
  await tester.ensureVisible(find.text('Upcoming games'));
  await tester.tap(find.text('Upcoming games'));
  await tester.pumpAndSettle();
}

class _LobbyRepository extends LobbyRepository {
  _LobbyRepository({
    this.failPreview = false,
    this.failSessions = false,
    this.emptySessions = false,
    this.sessionState = 'OPEN',
    this.endedAt,
    this.claimWindowSeconds,
  }) : super(_UnusedLobbyApi());

  bool failPreview;
  bool failSessions;
  final bool emptySessions;
  final String sessionState;
  final DateTime? endedAt;
  final int? claimWindowSeconds;
  Completer<List<GameSession>>? pendingSessions;
  int sessionRequests = 0;
  int previewRequests = 0;

  @override
  Future<List<GameSession>> getSessions() async {
    sessionRequests++;
    if (pendingSessions != null) return pendingSessions!.future;
    if (failSessions) throw Exception('offline');
    return emptySessions ? [] : await getRoomSessions(_room.id);
  }

  @override
  Future<BingoRoom> getRoom(int roomId) async => _room;

  @override
  Future<GameSession> getSession(int sessionId) async {
    previewRequests++;
    if (failPreview) throw Exception('offline');
    return (await getRoomSessions(_room.id)).single;
  }

  @override
  Future<List<GameSession>> getRoomSessions(int roomId) async => [
    GameSession(
      id: 12,
      roomId: roomId,
      roomName: _room.name,
      sessionName: 'QA Scheduled Session',
      state: sessionState,
      scheduledStartAt: DateTime.utc(2030),
      endedAt: endedAt,
      claimWindowSeconds: claimWindowSeconds,
      ticketPrice: 3.25,
      currency: 'ETB',
      sessionCapacity: 30,
      ticketCount: 0,
      drawCount: 0,
      createdAt: DateTime.utc(2029),
      updatedAt: DateTime.utc(2029),
      configuredPatterns: const ['LINE'],
    ),
  ];
}

final _room = BingoRoom(
  id: 6,
  name: 'QA Main Room',
  description: 'Test room',
  ticketPrice: 3.25,
  currency: 'ETB',
  timezone: 'Africa/Addis_Ababa',
  scheduleRule: 'daily',
  sessionCapacity: 30,
  maxTicketsPerPlayer: 2,
  ticketSalesCloseOnSessionStart: true,
  allowMultipleWinnersPerPrize: true,
  active: true,
  configuredPatterns: ['LINE'],
  configuredPrizes: [],
  createdAt: DateTime.utc(2029),
  updatedAt: DateTime.utc(2029),
);

class _UnusedLobbyApi extends LobbyApi {
  _UnusedLobbyApi() : super(Dio());
}

class _UnusedLiveGameRepository implements LiveGameRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _UnusedLiveGameTransport implements LiveGameTransport {
  @override
  bool get isConnected => false;

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

class _RecordingNavigatorObserver extends NavigatorObserver {
  final List<Route<dynamic>> pushes = <Route<dynamic>>[];

  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) {
    pushes.add(route);
    super.didPush(route, previousRoute);
  }
}
