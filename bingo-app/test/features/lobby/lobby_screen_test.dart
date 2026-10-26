import 'dart:async';

import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/core/config/app_config.dart';
import 'package:bingo_app/src/features/auth/data/auth_session_controller.dart';
import 'package:bingo_app/src/features/game/application/live_game_controller.dart';
import 'package:bingo_app/src/features/game/application/live_game_transport.dart';
import 'package:bingo_app/src/features/game/data/game_repository.dart';
import 'package:bingo_app/src/features/game/domain/live_game_models.dart';
import 'package:bingo_app/src/features/game/presentation/session_detail_screen.dart';
import 'package:bingo_app/src/features/lobby/data/lobby_api.dart';
import 'package:bingo_app/src/features/lobby/data/lobby_repository.dart';
import 'package:bingo_app/src/features/lobby/domain/lobby_models.dart';
import 'package:bingo_app/src/features/lobby/presentation/lobby_screen.dart';
import 'package:flutter/material.dart';
import 'package:dio/dio.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('lobby exposes OPEN sessions through live detail', (tester) async {
    final repository = _LobbyRepository();
    await tester.pumpWidget(_app(repository));
    await tester.pumpAndSettle();

    expect(find.text('QA Main Room'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('room-details-6')));
    await tester.pumpAndSettle();

    expect(find.text('QA Main Room'), findsOneWidget);
    expect(find.text('QA Scheduled Session'), findsOneWidget);
    expect(find.textContaining('OPEN'), findsOneWidget);
    expect(find.byKey(const ValueKey('live-game-12')), findsOneWidget);
    expect(find.byKey(const ValueKey('buy-ticket-button')), findsNothing);
  });

  testWidgets('cancelled sessions are shown as not playable', (tester) async {
    final repository = _LobbyRepository(sessionState: 'CANCELLED');
    await tester.pumpWidget(_app(repository));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('room-details-6')));
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
        apiBaseUrl: 'http://127.0.0.1:8082',
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
    await tester.tap(find.byKey(const ValueKey('room-details-6')));
    await tester.pumpAndSettle();

    final liveGameButton = find.byKey(const ValueKey('live-game-12'));
    expect(liveGameButton, findsOneWidget);
    expect(tester.widget<FilledButton>(liveGameButton).onPressed, isNotNull);
    await tester.ensureVisible(liveGameButton);
    await tester.pumpAndSettle();
    tester.widget<FilledButton>(liveGameButton).onPressed!();
    await tester.pump(const Duration(milliseconds: 500));

    expect(navigatorObserver.pushes, hasLength(3));
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
      await tester.tap(find.byKey(const ValueKey('room-details-6')));
      await tester.pumpAndSettle();
      await tester.ensureVisible(
        find.byKey(const ValueKey('ticket-preview-12')),
      );
      await tester.pumpAndSettle();
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
      await tester.tap(find.byKey(const ValueKey('room-details-6')));
      await tester.pumpAndSettle();
      await tester.ensureVisible(
        find.byKey(const ValueKey('ticket-preview-12')),
      );
      await tester.pumpAndSettle();
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
      await tester.pumpWidget(_app(repository));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('room-details-6')));
      await tester.pumpAndSettle();

      expect(find.text('Not playable: $state'), findsOneWidget);
      expect(find.byKey(const ValueKey('buy-ticket-button')), findsNothing);
      await tester.pageBack();
      await tester.pumpAndSettle();
    }
  });

  testWidgets('empty lobby displays an appropriate message', (tester) async {
    final repository = _LobbyRepository(emptyRooms: true);
    await tester.pumpWidget(_app(repository));
    await tester.pumpAndSettle();

    expect(find.text('No rooms available'), findsOneWidget);
  });

  testWidgets('room listing network error offers retry', (tester) async {
    final repository = _LobbyRepository(failRooms: true);
    await tester.pumpWidget(_app(repository));
    await tester.pumpAndSettle();
    expect(
      find.text("Couldn't load rooms. Check your connection and retry."),
      findsOneWidget,
    );
    expect(find.text('Try again'), findsOneWidget);

    repository.failRooms = false;
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    expect(find.text('QA Main Room'), findsOneWidget);
  });

  testWidgets('pending retry cannot be submitted again', (tester) async {
    final repository = _LobbyRepository(failRooms: true);
    await tester.pumpWidget(_app(repository));
    await tester.pumpAndSettle();
    expect(find.text('Try again'), findsOneWidget);

    repository.failRooms = false;
    repository.pendingRooms = Completer<List<BingoRoom>>();
    await tester.tap(find.text('Try again'));
    await tester.pump();

    expect(repository.roomRequests, 2); // initial request and one retry
    expect(find.text('Try again'), findsNothing);
    expect(find.text('Loading...'), findsOneWidget);

    repository.pendingRooms!.complete([_room]);
    await tester.pumpAndSettle();
    expect(repository.roomRequests, 2);
    expect(find.text('QA Main Room'), findsOneWidget);
    expect(find.text('Try again'), findsNothing);
  });
}

Widget _app(
  _LobbyRepository repository, {
  LiveGameController? liveGameController,
  NavigatorObserver? navigatorObserver,
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
    home: const LobbyScreen(),
  ),
);

class _LobbyRepository extends LobbyRepository {
  _LobbyRepository({
    this.failRooms = false,
    this.failPreview = false,
    this.emptyRooms = false,
    this.sessionState = 'OPEN',
  }) : super(_UnusedLobbyApi());

  bool failRooms;
  bool failPreview;
  final bool emptyRooms;
  final String sessionState;
  Completer<List<BingoRoom>>? pendingRooms;
  int roomRequests = 0;
  int previewRequests = 0;

  @override
  Future<List<BingoRoom>> getRooms() async {
    roomRequests++;
    if (pendingRooms != null) return pendingRooms!.future;
    if (failRooms) throw Exception('offline');
    return emptyRooms ? [] : [_room];
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
