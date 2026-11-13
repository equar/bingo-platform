/// Optional capture of real app widgets; fixtures never reach the production API.
/// Run with --dart-define=STORE_SCREENSHOT_DIR=/absolute/output/path.
library;

import 'dart:io';
import 'dart:ui' as ui;

import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/core/theme/app_theme.dart';
import 'package:bingo_player/src/features/community/data/community_repository.dart';
import 'package:bingo_player/src/features/community/domain/community_models.dart';
import 'package:bingo_player/src/features/home/presentation/home_shell.dart';
import 'package:bingo_player/src/features/lobby/presentation/lobby_screen.dart';
import 'package:bingo_player/src/features/lobby/domain/lobby_models.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

const outputDirectory = String.fromEnvironment('STORE_SCREENSHOT_DIR');
const captureKey = ValueKey('store-capture');
const device = String.fromEnvironment('STORE_SCREENSHOT_DEVICE');
const tablet = device == 'tablet7' || device == 'tablet10';
const captureWidth = device == 'tablet10' ? 2560 : (tablet ? 1920 : 1080);
const captureHeight = device == 'tablet10' ? 1440 : (tablet ? 1080 : 1920);
const capturePixelRatio = tablet ? 2.0 : 2.5;

class _ScreenshotCommunity extends CommunityRepository {
  _ScreenshotCommunity() : super(Dio());

  @override
  Future<List<PrivateGame>> privateGames() async => [
    PrivateGame(
      roomId: 1,
      sessionId: 1,
      name: 'Friends’ Night',
      state: 'OPEN',
      host: true,
      active: true,
      memberCount: 6,
      capacity: 12,
      scheduledStartAt: DateTime.utc(2026, 11, 7, 16),
    ),
    PrivateGame(
      roomId: 2,
      sessionId: 2,
      name: 'Weekend Bingo',
      state: 'OPEN',
      host: false,
      active: true,
      memberCount: 8,
      capacity: 20,
      scheduledStartAt: DateTime.utc(2026, 11, 8, 15),
    ),
  ];

  @override
  Future<DailyProgress> daily() async => DailyProgress(
    businessDate: '2026-11-06',
    timezone: 'Africa/Addis_Ababa',
    resetsAt: DateTime.utc(2026, 11, 6, 21),
    totalPoints: 60,
    challenges: const [
      DailyChallenge(
        code: 'FINISH_GAME',
        target: 1,
        progress: 1,
        points: 20,
        completed: true,
        claimed: true,
      ),
      DailyChallenge(
        code: 'EXPLORE_ROOMS',
        target: 2,
        progress: 1,
        points: 40,
        completed: false,
        claimed: false,
      ),
      DailyChallenge(
        code: 'VERIFIED_BINGO',
        target: 1,
        progress: 0,
        points: 60,
        completed: false,
        claimed: false,
      ),
    ],
  );

  @override
  Future<Ranking> leaderboard(String period, int page) async => const Ranking(
    entries: [
      RankingEntry(rank: 1, alias: 'BingoStar', points: 100, me: false),
      RankingEntry(rank: 2, alias: 'BingoFan', points: 60, me: true),
      RankingEntry(rank: 3, alias: 'Sunrise', points: 40, me: false),
      RankingEntry(rank: 4, alias: 'BlueSky', points: 20, me: false),
    ],
    totalPlayers: 4,
    page: 0,
    size: 25,
    myPoints: 60,
    myRank: 2,
    alias: 'BingoFan',
    visible: true,
    timezone: 'Africa/Addis_Ababa',
  );
}

List<BingoRoom> rooms() => [
  for (final item in [
    (1, 'Classic Bingo', 'Play together in a classic bingo room.', 10),
    (2, 'Evening Bingo', 'Find your next game and follow every draw.', 20),
  ])
    BingoRoom.fromJson({
      'id': item.$1,
      'name': item.$2,
      'description': item.$3,
      'ticketPrice': item.$4,
      'currency': 'ETB',
      'timezone': 'Africa/Addis_Ababa',
      'scheduleRule': 'DAILY',
      'sessionCapacity': 100,
      'maxTicketsPerPlayer': 5,
      'ticketSalesCloseOnSessionStart': true,
      'allowMultipleWinnersPerPrize': true,
      'active': true,
      'configuredPatterns': ['LINE'],
      'configuredPrizes': [],
    }),
];

Widget app() => ProviderScope(
  overrides: [
    communityRepositoryProvider.overrideWithValue(_ScreenshotCommunity()),
    lobbyRoomsProvider.overrideWith((ref) async => rooms()),
  ],
  child: RepaintBoundary(
    key: captureKey,
    child: MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: buildAppTheme(),
      locale: const Locale('en'),
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      home: const HomeShell(),
    ),
  ),
);

Future<void> capture(WidgetTester tester, String filename) async {
  final images = find.byType(Image).evaluate().toList();
  for (final element in images) {
    final image = element.widget as Image;
    await tester.runAsync(() => precacheImage(image.image, element));
  }
  await tester.pumpAndSettle();
  expect(tester.takeException(), isNull);
  final boundary = tester.renderObject<RenderRepaintBoundary>(
    find.byKey(captureKey),
  );
  await tester.runAsync(() async {
    final image = await boundary.toImage(pixelRatio: capturePixelRatio);
    expect(image.width, captureWidth);
    expect(image.height, captureHeight);
    final data = await image.toByteData(format: ui.ImageByteFormat.png);
    final file = File('$outputDirectory/$filename.png');
    await file.parent.create(recursive: true);
    await file.writeAsBytes(data!.buffer.asUint8List());
    expect(await file.length(), lessThanOrEqualTo(8000000));
    image.dispose();
  });
}

void main() {
  setUpAll(() async {
    if (outputDirectory.isEmpty) return;
    for (final font in [
      ('Roboto', const String.fromEnvironment('STORE_TEXT_FONT')),
      ('MaterialIcons', const String.fromEnvironment('STORE_ICON_FONT')),
    ]) {
      if (font.$2.isEmpty) continue;
      final loader = FontLoader(font.$1);
      loader.addFont(File(font.$2).readAsBytes().then(ByteData.sublistView));
      await loader.load();
    }
  });

  for (final screen in [
    ('01-home', ''),
    ('02-games', 'Play Bingo'),
    ('03-private-games', 'Private Game'),
    ('04-daily-challenges', 'Daily Challenge'),
    ('05-leaderboard', 'Leaderboard'),
  ]) {
    testWidgets('capture ${screen.$1}', (tester) async {
      tester.view.physicalSize = Size(
        captureWidth.toDouble(),
        captureHeight.toDouble(),
      );
      tester.view.devicePixelRatio = capturePixelRatio;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(app());
      await tester.pumpAndSettle();
      if (screen.$2.isNotEmpty) {
        final target = screen.$2 == 'Play Bingo'
            ? find.byKey(const ValueKey('home-play-bingo'))
            : find.text(screen.$2);
        await tester.ensureVisible(target);
        await tester.tap(target);
        await tester.pumpAndSettle();
      }
      await capture(tester, screen.$1);
    }, skip: outputDirectory.isEmpty);
  }
}
