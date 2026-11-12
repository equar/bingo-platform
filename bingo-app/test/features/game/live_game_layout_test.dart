import 'dart:io';
import 'dart:ui' as ui;

import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/core/config/app_config.dart';
import 'package:bingo_app/src/core/models/page_result.dart';
import 'package:bingo_app/src/core/theme/app_theme.dart';
import 'package:bingo_app/src/features/auth/data/auth_session_controller.dart';
import 'package:bingo_app/src/features/game/application/live_game_controller.dart';
import 'package:bingo_app/src/features/game/application/live_game_transport.dart';
import 'package:bingo_app/src/features/game/data/game_repository.dart';
import 'package:bingo_app/src/features/game/domain/live_game_models.dart';
import 'package:bingo_app/src/features/game/domain/ticket_models.dart';
import 'package:bingo_app/src/features/game/presentation/session_detail_screen.dart';
import 'package:bingo_app/src/features/lobby/domain/lobby_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

const output = String.fromEnvironment('LIVE_LAYOUT_SCREENSHOT_DIR');
const captureKey = ValueKey('live-layout-capture');

GameSession game({String status = 'DRAWING'}) => GameSession(
  id: 42,
  roomId: 8,
  roomName: 'Main Hall',
  sessionName: 'Evening Bingo',
  state: status,
  scheduledStartAt: DateTime.utc(2026, 11, 11),
  ticketPrice: 5,
  currency: 'ETB',
  sessionCapacity: 50,
  ticketCount: 1,
  drawCount: 21,
  configuredPatterns: const ['LINE'],
  createdAt: DateTime.utc(2026, 11, 11),
  updatedAt: DateTime.utc(2026, 11, 11),
);

TicketResponse ticket() => TicketResponse(
  id: 101,
  sessionId: 42,
  ownerId: 7,
  publicTicketCode: 'TK-42-101',
  eligibleClaimPatterns: const ['LINE'],
  operationId: 'fixture',
  grid: const [
    [1, 16, 31, 46, 61],
    [2, 17, 32, 47, 62],
    [3, 18, 0, 48, 63],
    [4, 19, 33, 49, 64],
    [5, 20, 34, 50, 65],
  ],
  purchasedAt: DateTime.utc(2026, 11, 11),
);

List<LiveDrawEvent> calls(int count) => List.generate(
  count,
  (index) => LiveDrawEvent(
    sessionId: 42,
    sequenceNo: index + 1,
    numberDrawn: (index * 17 + 21) % 75 + 1,
    drawnAt: DateTime.now().subtract(
      Duration(seconds: (count - index) * 10 - 5),
    ),
  ),
);

LiveGameState state(int count) => LiveGameState(
  isLoading: false,
  connectionState: LiveGameConnectionState.connected,
  ticketPageIndex: 0,
  selectedTicketIndex: 0,
  session: game(),
  draws: calls(count),
  ticketPage: PageResult(
    content: [ticket()],
    page: 0,
    size: 10,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
  ),
  selectedClaimPattern: 'LINE',
);

class _UnusedRepository implements LiveGameRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw StateError('Layout fixtures must not access the backend');
}

class _Transport implements LiveGameTransport {
  @override
  bool get isConnected => true;
  @override
  Future<void> disconnect() async {}
  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw StateError('Layout fixtures must not connect');
}

Future<LiveGameController> mount(
  WidgetTester tester, {
  Size size = const Size(390, 844),
  int count = 21,
  Locale locale = const Locale('en'),
  double scale = 1,
  Brightness brightness = Brightness.light,
}) async {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = size;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  final controller = LiveGameController(
    sessionId: 42,
    repository: _UnusedRepository(),
    transport: _Transport(),
    readAuthSession: () => const AuthSessionState(isLoading: false),
    clearAuthSession: ({String? reason}) async {},
    appConfig: const AppConfig(
      apiBaseUrl: 'https://example.invalid',
      apiBaseUrlLabel: 'API_BASE_URL',
    ),
  )..state = state(count);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        liveGameControllerProvider(42).overrideWith((ref) => controller),
        appConfigProvider.overrideWithValue(
          const AppConfig(
            apiBaseUrl: 'https://example.invalid',
            apiBaseUrlLabel: 'API_BASE_URL',
            playerTicketPurchasesEnabled: true,
          ),
        ),
      ],
      child: RepaintBoundary(
        key: captureKey,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: buildAppTheme(brightness: brightness),
          locale: locale,
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: TextScaler.linear(scale)),
            child: child!,
          ),
          home: SessionDetailScreen(session: game()),
        ),
      ),
    ),
  );
  await tester.pump(const Duration(milliseconds: 300));
  return controller;
}

Future<void> capture(WidgetTester tester, String name) async {
  if (output.isEmpty) return;
  await tester.pump(const Duration(milliseconds: 250));
  final boundary = tester.renderObject<RenderRepaintBoundary>(
    find.byKey(captureKey),
  );
  await tester.runAsync(() async {
    final image = await boundary.toImage(pixelRatio: 2);
    final data = await image.toByteData(format: ui.ImageByteFormat.png);
    final file = File('$output/$name.png');
    await file.parent.create(recursive: true);
    await file.writeAsBytes(data!.buffer.asUint8List());
    image.dispose();
  });
}

void main() {
  setUpAll(() async {
    for (final font in [
      ('Roboto', const String.fromEnvironment('LIVE_LAYOUT_FONT')),
      ('MaterialIcons', const String.fromEnvironment('LIVE_LAYOUT_ICONS')),
    ]) {
      if (font.$2.isEmpty) continue;
      final loader = FontLoader(font.$1)
        ..addFont(File(font.$2).readAsBytes().then(ByteData.sublistView));
      await loader.load();
    }
  });

  testWidgets(
    'ticket position stays fixed as the call history grows from zero to 75',
    (tester) async {
      final controller = await mount(tester, count: 0);
      final grid = find.byKey(const ValueKey('live-ticket-grid'));
      final initial = tester.getRect(grid);
      for (final count in [1, 5, 21, 75]) {
        controller.state = state(count);
        await tester.pump(const Duration(milliseconds: 300));
        expect(tester.getRect(grid), initial);
        expect(
          find.byWidgetPredicate(
            (widget) =>
                widget.key is ValueKey<String> &&
                (widget.key! as ValueKey<String>).value.startsWith(
                  'recent-call-',
                ),
          ),
          findsNWidgets(count < 5 ? count : 5),
        );
        expect(tester.takeException(), isNull);
      }
      expect(initial.bottom, lessThanOrEqualTo(844));
      controller.state = state(21);
      await tester.pump(const Duration(milliseconds: 300));
      await capture(tester, 'live-game-phone');
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'full history opens in a scrollable sheet without shifting the ticket',
    (tester) async {
      final controller = await mount(tester, count: 21);
      final grid = find.byKey(const ValueKey('live-ticket-grid'));
      final initial = tester.getRect(grid);
      expect(find.byKey(const ValueKey('history-call-1')), findsNothing);
      await tester.tap(find.byKey(const ValueKey('view-all-calls')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
      expect(find.byKey(const ValueKey('full-call-history')), findsOneWidget);
      controller.state = state(75);
      await tester.pump();
      await tester.pump();
      expect(
        find.byWidgetPredicate(
          (widget) =>
              widget.key is ValueKey<String> &&
              (widget.key! as ValueKey<String>).value.startsWith(
                'history-call-',
              ),
        ),
        findsNWidgets(75),
      );
      expect(
        find.byTooltip('Close call history').hitTestable(),
        findsOneWidget,
      );
      await tester.ensureVisible(find.byKey(const ValueKey('history-call-1')));
      await tester.pump();
      expect(tester.takeException(), isNull);
      await capture(tester, 'call-history-sheet');
      await tester.tap(find.byTooltip('Close call history'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
      expect(find.byKey(const ValueKey('full-call-history')), findsNothing);
      expect(tester.getRect(grid), initial);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'countdown is an observed estimate and stops during reconnection',
    (tester) async {
      final controller = await mount(tester, count: 1);
      expect(find.text('Waiting for the next call'), findsOneWidget);
      controller.state = state(21);
      await tester.pump();
      expect(find.textContaining('Next call in about'), findsOneWidget);
      controller.state = state(
        21,
      ).copyWith(connectionState: LiveGameConnectionState.reconnecting);
      await tester.pump();
      expect(find.textContaining('Next call in about'), findsNothing);
      expect(
        tester
            .widget<LinearProgressIndicator>(
              find.byKey(const ValueKey('call-progress')),
            )
            .value,
        0,
      );
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets('pregame purchase stays above the ticket and remains available', (
    tester,
  ) async {
    final controller = await mount(tester, count: 0);
    controller.state = state(0).copyWith(session: game(status: 'OPEN'));
    await tester.pump();
    final buy = find.byKey(const ValueKey('buy-ticket-button'));
    expect(
      tester.getTopLeft(buy).dy,
      lessThan(
        tester.getTopLeft(find.byKey(const ValueKey('live-tickets'))).dy,
      ),
    );
    expect(tester.widget<FilledButton>(buy).onPressed, isNotNull);
    expect(find.byKey(const ValueKey('live-call-panel')), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('BINGO header colors differ from marked and latest numbers', (
    tester,
  ) async {
    await mount(tester, count: 21);
    final header = tester.widget<Container>(
      find.byKey(const ValueKey('bingo-header-B')),
    );
    final color = (header.decoration! as BoxDecoration).color;
    final context = tester.element(
      find.byKey(const ValueKey('live-ticket-grid')),
    );
    expect(color, isNot(Theme.of(context).colorScheme.primaryContainer));
    expect(color, isNot(Theme.of(context).colorScheme.secondaryContainer));
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets(
    'server-confirmed winners see their celebration once with exact totals and OK',
    (tester) async {
      final controller = await mount(tester, count: 21);
      final winners = [
        for (final id in [1042, 1043, 1045])
          TicketResponse.fromJson({
            'id': id,
            'grid': ticket().grid,
            'eligibleClaimPatterns': ['LINE'],
            'winningPrizeAmounts': {'LINE': 200},
            'prizeCurrency': 'ETB',
          }),
      ];
      final winnerState = state(21).copyWith(
        winningTickets: winners,
        ticketPage: PageResult(
          content: [ticket()],
          page: 0,
          size: 10,
          totalElements: 5,
          totalPages: 1,
          first: true,
          last: true,
        ),
      );
      controller.state = winnerState;
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 350));
      expect(
        find.byKey(const ValueKey('winner-celebration-dialog')),
        findsOneWidget,
      );
      expect(find.text('3 Winning Tickets!'), findsOneWidget);
      expect(find.text('200 ETB'), findsNWidgets(3));
      expect(find.text('600 ETB'), findsNWidgets(2));
      expect(find.text('3 of 5 tickets won'), findsOneWidget);
      await capture(tester, 'winner-celebration');
      await tester.pump(const Duration(milliseconds: 350));
      await tester.tap(find.byKey(const ValueKey('winner-celebration-ok')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 350));
      controller.state = winnerState;
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 350));
      expect(
        find.byKey(const ValueKey('winner-celebration-dialog')),
        findsNothing,
      );
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets(
    'Claim is disabled before a server-confirmed win and activates for a winner',
    (tester) async {
      final controller = await mount(tester, count: 0);
      final losingTicket = TicketResponse.fromJson({
        'id': 101,
        'sessionId': 42,
        'grid': ticket().grid,
        'eligibleClaimPatterns': <String>[],
      });
      for (final count in [0, 5, 21]) {
        controller.state = state(count).copyWith(
          ticketPage: PageResult(
            content: [losingTicket],
            page: 0,
            size: 10,
            totalElements: 1,
            totalPages: 1,
            first: true,
            last: true,
          ),
        );
        await tester.pump();
        expect(
          tester
              .widget<FilledButton>(
                find.byKey(const ValueKey('claim-submit-button')),
              )
              .onPressed,
          isNull,
        );
        expect(
          find.byKey(const ValueKey('winning-ticket-message')),
          findsNothing,
        );
        expect(controller.state.canSubmitClaim, false);
        expect(
          await controller.submitClaim(),
          ClaimSubmissionOutcome.notSubmitted,
        );
        expect(controller.state.apiError, isNull);
      }
      controller.state = state(21);
      await tester.pump();
      expect(
        tester
            .widget<FilledButton>(
              find.byKey(const ValueKey('claim-submit-button')),
            )
            .onPressed,
        isNotNull,
      );
      expect(find.text('You won LINE! Claim your prize.'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );

  testWidgets('repeat claim opens a small dialog without another submission', (
    tester,
  ) async {
    final controller = await mount(tester, count: 21);
    final claim = WinClaimRecord.fromJson({
      'id': 900,
      'ticketId': 101,
      'sessionId': 42,
      'claimedPattern': 'LINE',
      'status': 'PENDING_REVIEW',
      'serverValidationPassed': true,
      'drawSequenceSnapshot': 20,
    });
    controller.state = state(21).copyWith(
      claimPage: PageResult(
        content: [claim],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      ),
    );
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('claim-submit-button')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(
      find.byKey(const ValueKey('claim-already-submitted-dialog')),
      findsOneWidget,
    );
    expect(find.text('Your claim has already been submitted.'), findsOneWidget);
    expect(controller.state.apiError, isNull);
    await capture(tester, 'claim-already-submitted-dialog');
    await tester.tap(find.text('OK'));
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('live play fills the viewport and keeps details in a sheet', (
    tester,
  ) async {
    await mount(tester, count: 21);
    final scroll = tester.widget<SingleChildScrollView>(
      find.byKey(const ValueKey('live-play-screen')),
    );
    expect(scroll.child, isA<SizedBox>());
    expect(
      tester
          .getBottomLeft(find.byKey(const ValueKey('claim-submit-button')))
          .dy,
      lessThanOrEqualTo(844),
    );
    expect(find.text('Main Hall'), findsNothing);
    await tester.tap(find.byKey(const ValueKey('live-game-details')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.textContaining('Main Hall'), findsWidgets);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  for (final language in ['en', 'am']) {
    testWidgets(
      'celebration OK remains reachable with large $language text on a short phone',
      (tester) async {
        final controller = await mount(
          tester,
          count: 21,
          size: const Size(320, 640),
          scale: 2,
          locale: Locale(language),
        );
        final win = TicketResponse.fromJson({
          'id': 1042,
          'grid': ticket().grid,
          'eligibleClaimPatterns': ['LINE'],
          'winningPrizeAmounts': {'LINE': 5.01},
          'prizeCurrency': 'ETB',
        });
        controller.state = state(21).copyWith(winningTickets: [win]);
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 350));
        expect(
          find.byKey(const ValueKey('winner-celebration-dialog')),
          findsOneWidget,
        );
        await tester.ensureVisible(
          find.byKey(const ValueKey('winner-celebration-ok')),
        );
        await tester.pump(const Duration(milliseconds: 300));
        await tester.pump(const Duration(milliseconds: 350));
        await tester.tap(find.byKey(const ValueKey('winner-celebration-ok')));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 350));
        expect(
          find.byKey(const ValueKey('winner-celebration-dialog')),
          findsNothing,
        );
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox.shrink());
      },
    );
  }

  for (final locale in [const Locale('en'), const Locale('am')]) {
    testWidgets(
      'narrow large-text layout stays stable in ${locale.languageCode}',
      (tester) async {
        final controller = await mount(
          tester,
          size: const Size(320, 800),
          locale: locale,
          scale: 2,
        );
        final grid = find.byKey(const ValueKey('live-ticket-grid'));
        final initial = tester.getRect(grid);
        controller.state = state(75);
        await tester.pump(const Duration(milliseconds: 300));
        expect(tester.getRect(grid), initial);
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox.shrink());
      },
    );
  }

  testWidgets('tablet dark theme retains the same bounded call area', (
    tester,
  ) async {
    final controller = await mount(
      tester,
      size: const Size(1024, 1366),
      brightness: Brightness.dark,
    );
    final initial = tester.getRect(
      find.byKey(const ValueKey('live-ticket-grid')),
    );
    controller.state = state(75);
    await tester.pump(const Duration(milliseconds: 300));
    expect(
      tester.getRect(find.byKey(const ValueKey('live-ticket-grid'))),
      initial,
    );
    expect(tester.takeException(), isNull);
    await capture(tester, 'live-game-tablet-dark');
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
