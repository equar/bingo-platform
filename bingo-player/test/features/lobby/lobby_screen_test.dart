import 'dart:async';

import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/features/lobby/data/lobby_api.dart';
import 'package:bingo_player/src/features/lobby/data/lobby_repository.dart';
import 'package:bingo_player/src/features/lobby/domain/lobby_models.dart';
import 'package:bingo_player/src/features/lobby/presentation/lobby_screen.dart';
import 'package:bingo_player/src/features/auth/data/auth_models.dart';
import 'package:bingo_player/src/features/auth/data/auth_session_controller.dart';
import 'package:bingo_player/src/core/storage/session_storage.dart';
import 'package:bingo_player/src/core/config/app_config.dart';
import 'package:bingo_player/src/core/models/page_result.dart';
import 'package:bingo_player/src/core/network/api_provider.dart';
import 'package:bingo_player/src/features/game/application/live_game_transport.dart';
import 'package:bingo_player/src/features/lobby/presentation/ticket_preview_screen.dart';
import 'package:bingo_player/src/features/player_content/data/player_content_api.dart';
import 'package:bingo_player/src/features/player_content/domain/player_content_models.dart';
import 'package:flutter/material.dart';
import 'package:dio/dio.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('My Games shows owned active games ordered by start time', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1000, 1800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final repository = _LobbyRepository(
      sessions: [
        _session(1, 'OPEN', 4),
        _session(2, 'DRAWING', 1),
        _session(3, 'OPEN', 2),
        _session(4, 'STARTED', 3),
        _session(5, 'SETTLED', 0),
        _session(6, 'OPEN', 5),
      ],
    );
    final history = _PlayerContentApi([
      [_historyEntry(1), _historyEntry(2), _historyEntry(2)],
      [_historyEntry(4), _historyEntry(5)],
    ]);
    await tester.pumpWidget(_app(repository, history: history));
    await tester.pumpAndSettle();

    expect(find.text('My Games'), findsOneWidget);
    expect(find.text('Previous games'), findsNothing);
    expect(find.text('Game 2'), findsOneWidget);
    expect(find.text('Game 4'), findsOneWidget);
    expect(find.text('Game 1'), findsOneWidget);
    expect(find.text('Game 3'), findsNothing);
    expect(find.text('Game 5'), findsNothing);
    expect(find.text('Game 6'), findsNothing);
    expect(
      tester.getTopLeft(find.text('Game 2')).dy,
      lessThan(tester.getTopLeft(find.text('Game 4')).dy),
    );
    expect(
      tester.getTopLeft(find.text('Game 4')).dy,
      lessThan(tester.getTopLeft(find.text('Game 1')).dy),
    );
    expect(find.byKey(const ValueKey('live-game-1')), findsOneWidget);
    expect(history.pagesRequested, [0, 1]);

    await _openUpcomingGames(tester);
    expect(find.text('Game 3'), findsOneWidget);
    expect(find.text('Game 6'), findsOneWidget);
  });

  testWidgets('My Games reports ticket loading errors and retries', (
    tester,
  ) async {
    final history = _PlayerContentApi([])..fail = true;
    await tester.pumpWidget(_app(_LobbyRepository(), history: history));
    await tester.pumpAndSettle();
    expect(find.text('Try again'), findsOneWidget);
    await _openUpcomingGames(tester);
    expect(find.text('QA Scheduled Session'), findsOneWidget);
    await tester.tap(find.text('My Games'));
    await tester.pumpAndSettle();
    history.fail = false;
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    expect(find.text('You have no upcoming or live games.'), findsOneWidget);
  });

  testWidgets('refresh updates ticket ownership in My Games', (tester) async {
    final history = _PlayerContentApi([]);
    await tester.pumpWidget(_app(_LobbyRepository(), history: history));
    await tester.pumpAndSettle();
    expect(find.text('You have no upcoming or live games.'), findsOneWidget);

    history.pages.add([_historyEntry(12)]);
    await tester.tap(find.byKey(const ValueKey('lobby-refresh-button')));
    await tester.pumpAndSettle();
    expect(find.text('QA Scheduled Session'), findsOneWidget);
    expect(find.text('My tickets'), findsOneWidget);
  });

  testWidgets('lobby lists active sessions', (tester) async {
    final repository = _LobbyRepository();
    await tester.pumpWidget(_app(repository));
    await tester.pumpAndSettle();
    await _openUpcomingGames(tester);

    expect(find.text('QA Scheduled Session'), findsOneWidget);
    expect(find.byKey(const ValueKey('session-details-12')), findsOneWidget);
    expect(find.byKey(const ValueKey('buy-tickets-12')), findsOneWidget);
    expect(find.text('Game patterns: LINE'), findsOneWidget);
    expect(find.text('Prize: LINE 100 ETB'), findsOneWidget);
  });

  testWidgets('drawing sessions provide a live-game entry action', (
    tester,
  ) async {
    final repository = _LobbyRepository(sessionState: 'DRAWING');
    await tester.pumpWidget(_app(repository));
    await tester.pumpAndSettle();

    expect(find.text('QA Scheduled Session'), findsOneWidget);
    expect(find.byKey(const ValueKey('live-game-12')), findsOneWidget);
    expect(find.byKey(const ValueKey('buy-tickets-12')), findsNothing);
  });

  testWidgets('newly started sessions appear after a WebSocket update', (
    tester,
  ) async {
    final repository = _LobbyRepository(sessionState: 'OPEN');
    final transport = _FakeLobbySessionUpdatesTransport();
    await tester.pumpWidget(
      _app(repository, authenticated: true, sessionUpdatesTransport: transport),
    );
    await tester.pumpAndSettle();

    expect(find.text('QA Scheduled Session'), findsOneWidget);
    expect(transport.isConnected, isTrue);

    repository.sessionState = 'STARTED';
    transport.emitSessionChanged();
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('live-game-12')), findsOneWidget);
    expect(repository.sessionRequests, greaterThan(1));
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

  testWidgets('game rules returns to the upcoming games tab', (tester) async {
    final repository = _LobbyRepository(sessionState: 'OPEN');
    final navigatorObserver = _RecordingNavigatorObserver();
    await tester.pumpWidget(
      _app(repository, navigatorObserver: navigatorObserver),
    );
    await tester.pumpAndSettle();
    await _openUpcomingGames(tester);
    await tester.tap(find.byKey(const ValueKey('session-details-12')));
    await tester.pumpAndSettle();

    expect(navigatorObserver.pushes, hasLength(2));
    expect(navigatorObserver.pushes.last.isCurrent, isTrue);
    expect(find.byType(GameRulesScreen, skipOffstage: false), findsOneWidget);
    expect(find.text('Game Rules'), findsOneWidget);
    expect(find.text('General Game Rule'), findsOneWidget);

    await tester.pageBack();
    await tester.pumpAndSettle();

    expect(find.text('Upcoming games'), findsOneWidget);
    expect(find.text('QA Scheduled Session'), findsOneWidget);
  });

  testWidgets(
    'ticket information shows server snapshot and keeps purchase disabled',
    (tester) async {
      final repository = _LobbyRepository();
      await tester.pumpWidget(
        _app(
          repository,
          home: const TicketPreviewScreen(sessionId: 12, roomId: 6),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.textContaining('3.25 ETB'), findsOneWidget);
      expect(find.textContaining('30 / 30'), findsOneWidget);
      expect(find.text('How to play'), findsOneWidget);
      expect(find.text('Winning patterns'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('winning-pattern-horizontal-10')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('winning-pattern-vertical-2')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('winning-pattern-diagonal-0')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('winning-pattern-fullHouse-24')),
        findsOneWidget,
      );
      expect(find.text('Prize sharing when there is a tie'), findsOneWidget);
      expect(find.text('Important rules'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.text('1–15'),
        240,
        scrollable: find.descendant(
          of: find.byKey(const ValueKey('game-rules-scroll')),
          matching: find.byType(Scrollable),
        ),
      );
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
        scrollable: find.descendant(
          of: find.byKey(const ValueKey('game-rules-scroll')),
          matching: find.byType(Scrollable),
        ),
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
      await tester.pumpWidget(
        _app(
          repository,
          home: const TicketPreviewScreen(sessionId: 12, roomId: 6),
        ),
      );
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
        scrollable: find.descendant(
          of: find.byKey(const ValueKey('game-rules-scroll')),
          matching: find.byType(Scrollable),
        ),
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
  NavigatorObserver? navigatorObserver,
  Widget home = const LobbyScreen(),
  bool authenticated = false,
  LobbySessionUpdatesTransport? sessionUpdatesTransport,
  _PlayerContentApi? history,
}) => ProviderScope(
  overrides: [
    lobbyRepositoryProvider.overrideWithValue(repository),
    playerContentApiProvider.overrideWithValue(
      history ??
          _PlayerContentApi([
            [_historyEntry(12)],
          ]),
    ),
    appConfigProvider.overrideWithValue(
      const AppConfig(
        apiBaseUrl: 'http://localhost:8080',
        apiBaseUrlLabel: 'test',
      ),
    ),
    if (authenticated)
      authSessionControllerProvider.overrideWith(
        (ref) => _AuthenticatedAuthSessionController(),
      ),
    if (sessionUpdatesTransport != null)
      lobbySessionUpdatesTransportProvider.overrideWithValue(
        sessionUpdatesTransport,
      ),
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
    this.sessions,
  }) : super(_UnusedLobbyApi());

  bool failPreview;
  bool failSessions;
  final bool emptySessions;
  String sessionState;
  final DateTime? endedAt;
  final int? claimWindowSeconds;
  final List<GameSession>? sessions;
  Completer<List<GameSession>>? pendingSessions;
  int sessionRequests = 0;
  int previewRequests = 0;

  @override
  Future<List<GameSession>> getSessions() async {
    sessionRequests++;
    if (pendingSessions != null) return pendingSessions!.future;
    if (failSessions) throw Exception('offline');
    return emptySessions ? [] : sessions ?? await getRoomSessions(_room.id);
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
      configuredPrizes: const [
        BingoPrizeConfig(
          prizeType: 'LINE',
          amount: 100,
          settlementPath: 'WALLET',
        ),
      ],
    ),
  ];
}

GameSession _session(int id, String state, int day) => GameSession(
  id: id,
  roomId: 6,
  roomName: 'Room',
  sessionName: 'Game $id',
  state: state,
  scheduledStartAt: DateTime.utc(2030, 1, day + 1),
  ticketPrice: 1,
  currency: 'ETB',
  sessionCapacity: 30,
  ticketCount: 1,
  drawCount: 0,
  createdAt: DateTime.utc(2029),
  updatedAt: DateTime.utc(2029),
);

PlayerGameHistoryItem _historyEntry(int sessionId) => PlayerGameHistoryItem(
  ticketId: sessionId,
  sessionId: sessionId,
  roomName: 'Room',
  sessionName: 'Game $sessionId',
  sessionState: 'OPEN',
  ticketCode: 'ticket',
  purchasedAt: DateTime.utc(2029),
);

class _PlayerContentApi extends PlayerContentApi {
  _PlayerContentApi(this.pages) : super(Dio());

  final List<List<PlayerGameHistoryItem>> pages;
  final List<int> pagesRequested = [];
  bool fail = false;

  @override
  Future<PageResult<PlayerGameHistoryItem>> getGameHistory({
    required int page,
    required int size,
  }) async {
    pagesRequested.add(page);
    if (fail) throw Exception('offline');
    final content = page < pages.length
        ? pages[page]
        : <PlayerGameHistoryItem>[];
    return PageResult(
      content: content,
      page: page,
      size: size,
      totalElements: pages.fold(0, (sum, entries) => sum + entries.length),
      totalPages: pages.length,
      first: page == 0,
      last: page >= pages.length - 1,
    );
  }
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

class _FakeLobbySessionUpdatesTransport
    implements LobbySessionUpdatesTransport {
  @override
  bool isConnected = false;
  void Function()? _onSessionChanged;

  @override
  Future<void> connectToSessionUpdates({
    required Uri uri,
    required String accessToken,
    required void Function() onSessionChanged,
    required void Function(Object error) onError,
    required void Function() onDisconnected,
  }) async {
    _onSessionChanged = onSessionChanged;
    isConnected = true;
  }

  @override
  Future<void> disconnect() async {
    isConnected = false;
    _onSessionChanged = null;
  }

  void emitSessionChanged() => _onSessionChanged?.call();
}

class _AuthenticatedAuthSessionController extends AuthSessionController {
  _AuthenticatedAuthSessionController()
    : super(const SessionStorage(), restoreOnInit: false) {
    state = AuthSessionState(
      isLoading: false,
      session: AuthSession(
        accessToken: 'test-token',
        expiresAt: DateTime.now().toUtc().add(const Duration(hours: 1)),
        user: UserProfile(
          id: 1,
          displayName: 'Player',
          phoneNumber: '+251900000000',
          roles: const ['PLAYER'],
          status: 'ACTIVE',
          createdAt: DateTime.utc(2029),
          updatedAt: DateTime.utc(2029),
        ),
      ),
    );
  }
}

class _RecordingNavigatorObserver extends NavigatorObserver {
  final List<Route<dynamic>> pushes = <Route<dynamic>>[];

  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) {
    pushes.add(route);
    super.didPush(route, previousRoute);
  }
}
