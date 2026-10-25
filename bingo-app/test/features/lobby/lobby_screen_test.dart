import 'dart:async';

import 'package:bingo_app/l10n/app_localizations.dart';
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
  testWidgets('lobby shows rooms and read-only room sessions', (tester) async {
    final repository = _LobbyRepository();
    await tester.pumpWidget(_app(repository));
    await tester.pumpAndSettle();

    expect(find.text('QA Main Room'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('room-details-6')));
    await tester.pumpAndSettle();

    expect(find.text('QA Main Room'), findsOneWidget);
    expect(find.text('QA Scheduled Session'), findsOneWidget);
    expect(find.textContaining('OPEN'), findsOneWidget);
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

  testWidgets('closed and settled sessions are shown as not playable',
      (tester) async {
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
    expect(find.text("Couldn't load rooms. Check your connection and retry."),
        findsOneWidget);
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

Widget _app(_LobbyRepository repository) => ProviderScope(
      overrides: [lobbyRepositoryProvider.overrideWithValue(repository)],
      child: MaterialApp(
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
    this.emptyRooms = false,
    this.sessionState = 'OPEN',
  })
      : super(_UnusedLobbyApi());

  bool failRooms;
  final bool emptyRooms;
  final String sessionState;
  Completer<List<BingoRoom>>? pendingRooms;
  int roomRequests = 0;

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
