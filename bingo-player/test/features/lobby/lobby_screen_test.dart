import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/core/config/app_config.dart';
import 'package:bingo_player/src/features/lobby/domain/lobby_models.dart';
import 'package:bingo_player/src/features/lobby/presentation/lobby_screen.dart';
import 'package:bingo_player/src/features/lobby/presentation/ticket_preview_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('renders active sessions with ticket totals', (tester) async {
    await tester.pumpWidget(_app((ref) async => [_session]));
    await tester.pumpAndSettle();

    expect(find.text('QA Scheduled Session'), findsOneWidget);
    expect(find.text('Tickets'), findsOneWidget);
    expect(find.text('Buy Tickets'), findsOneWidget);
  });

  testWidgets('details action opens ticket information', (tester) async {
    await tester.pumpWidget(_app((ref) async => [_session]));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Details & Rules'));
    await tester.pumpAndSettle();

    expect(find.byType(TicketPreviewScreen), findsOneWidget);
  });

  testWidgets('empty active sessions show an empty state', (tester) async {
    await tester.pumpWidget(_app((ref) async => const []));
    await tester.pumpAndSettle();

    expect(find.text('No rooms available'), findsOneWidget);
  });

  testWidgets('active-session request errors offer retry', (tester) async {
    var failed = true;
    await tester.pumpWidget(_app((ref) async {
      if (failed) throw Exception('offline');
      return [_session];
    }));
    await tester.pumpAndSettle();

    expect(find.text('Try again'), findsOneWidget);
    failed = false;
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    expect(find.text('QA Scheduled Session'), findsOneWidget);
  });
}

Widget _app(Future<List<GameSession>> Function(Ref ref) sessions) {
  return ProviderScope(
    overrides: [
      activeSessionsProvider.overrideWith(sessions),
      appConfigProvider.overrideWithValue(
        const AppConfig(
          apiBaseUrl: 'http://example.test',
          apiBaseUrlLabel: 'API_BASE_URL',
          playerTicketPurchasesEnabled: true,
        ),
      ),
    ],
    child: MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: const LobbyScreen(),
    ),
  );
}

final _session = GameSession(
  id: 12,
  roomId: 6,
  roomName: 'QA Main Room',
  sessionName: 'QA Scheduled Session',
  state: 'OPEN',
  scheduledStartAt: DateTime.utc(2030),
  ticketPrice: 3.25,
  currency: 'ETB',
  sessionCapacity: 30,
  maxTicketsPerPlayer: 5,
  ticketCount: 2,
  drawCount: 0,
  createdAt: DateTime.utc(2029),
  updatedAt: DateTime.utc(2029),
  configuredPatterns: const ['LINE'],
  configuredPrizes: const [],
);
