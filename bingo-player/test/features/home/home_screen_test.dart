import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:dio/dio.dart';
import 'package:bingo_player/src/features/community/data/community_repository.dart';
import 'package:bingo_player/src/features/community/domain/community_models.dart';
import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/core/theme/app_theme.dart';
import 'package:bingo_player/src/features/home/presentation/home_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';

Widget _home({
  required VoidCallback onGames,
  VoidCallback? onSupport,
  VoidCallback? onOpenMenu,
  double textScale = 1,
  Brightness brightness = Brightness.light,
}) => ProviderScope(
  overrides: [
    communityRepositoryProvider.overrideWithValue(_CommunityRepository()),
  ],
  child: MaterialApp(
    theme: buildAppTheme(brightness: brightness),
    localizationsDelegates: const [
      AppLocalizations.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
    ],
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(textScaler: TextScaler.linear(textScale)),
      child: child!,
    ),
    home: Scaffold(
      body: HomeScreen(
        onGames: onGames,
        onOpenMenu: onOpenMenu ?? () {},
        onSupport: onSupport ?? () {},
      ),
    ),
  ),
);

class _CommunityRepository extends CommunityRepository {
  _CommunityRepository() : super(Dio());
  @override
  Future<List<PrivateGame>> privateGames() async => [];
  @override
  Future<DailyProgress> daily() async => DailyProgress(
    businessDate: '2026-10-29',
    timezone: 'Africa/Addis_Ababa',
    resetsAt: DateTime.utc(2026, 10, 29, 21),
    totalPoints: 20,
    challenges: const [
      DailyChallenge(
        code: 'FINISH_GAME',
        target: 1,
        progress: 1,
        points: 20,
        completed: true,
        claimed: true,
      ),
    ],
  );
  @override
  Future<Ranking> leaderboard(String period, int page) async => const Ranking(
    entries: [RankingEntry(rank: 1, alias: 'BingoStar', points: 20, me: true)],
    totalPlayers: 1,
    page: 0,
    size: 25,
    myPoints: 20,
    myRank: 1,
    alias: 'BingoStar',
    visible: true,
    timezone: 'Africa/Addis_Ababa',
  );
}

void main() {
  testWidgets('navy header background covers the status bar safe area', (
    tester,
  ) async {
    tester.view.viewPadding = const FakeViewPadding(top: 24);
    addTearDown(tester.view.resetViewPadding);
    await tester.pumpWidget(_home(onGames: () {}));
    await tester.pumpAndSettle();

    final header = find.byKey(const ValueKey('home-header-background'));
    expect(tester.getTopLeft(header).dy, 0);
    final decoration =
        tester.widget<Container>(header).decoration! as BoxDecoration;
    expect(decoration.gradient, isA<LinearGradient>());
    expect(decoration.borderRadius, isNull);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Play Bingo opens the existing games flow', (tester) async {
    var opened = false;
    await tester.pumpWidget(_home(onGames: () => opened = true));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('home-play-bingo')));
    expect(opened, isTrue);
  });

  testWidgets('Private Game opens the private games flow', (tester) async {
    await tester.pumpWidget(_home(onGames: () {}));
    await tester.pumpAndSettle();
    expect(find.text('Coming soon'), findsNothing);
    await tester.ensureVisible(find.text('Private Game'));
    await tester.tap(find.text('Private Game'));
    await tester.pumpAndSettle();
    expect(find.byType(HomeScreen), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Daily Challenge opens server progress', (tester) async {
    await tester.pumpWidget(_home(onGames: () {}));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Daily Challenge'));
    await tester.tap(find.text('Daily Challenge'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Africa/Addis_Ababa'), findsWidgets);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Leaderboard displays the public alias', (tester) async {
    await tester.pumpWidget(_home(onGames: () {}));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Leaderboard'));
    await tester.tap(find.text('Leaderboard'));
    await tester.pumpAndSettle();
    expect(find.textContaining('BingoStar'), findsWidgets);
    expect(tester.takeException(), isNull);
  });

  testWidgets('home menu button opens the shared navigation menu', (
    tester,
  ) async {
    var opened = false;
    await tester.pumpWidget(
      _home(onGames: () {}, onOpenMenu: () => opened = true),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('home-menu')));
    expect(opened, isTrue);
  });

  testWidgets('home stays scrollable on narrow screens with larger text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(_home(onGames: () {}, textScale: 1.5));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('home-scroll')), findsOneWidget);
    await tester.ensureVisible(find.text('Play & Win'));
    expect(tester.takeException(), isNull);
  });

  testWidgets('home scrolls when the status inset reduces card space', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 680);
    tester.view.devicePixelRatio = 1;
    tester.view.viewPadding = const FakeViewPadding(top: 80);
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetViewPadding);

    await tester.pumpWidget(_home(onGames: () {}));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('home-scroll')), findsOneWidget);
    expect(find.byKey(const ValueKey('home-feature-scroll')), findsNothing);
    await tester.ensureVisible(find.text('Play & Win'));
    expect(tester.takeException(), isNull);
  });

  for (final size in [const Size(390, 844), const Size(1024, 768)]) {
    testWidgets(
      'home expands feature cards to fill ${size.width.toInt()}x${size.height.toInt()}',
      (tester) async {
        tester.view.physicalSize = size;
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);

        await tester.pumpWidget(_home(onGames: () {}));
        await tester.pumpAndSettle();

        expect(find.byKey(const ValueKey('home-scroll')), findsNothing);
        final liveGamesCard = find
            .ancestor(
              of: find.text('Live Games'),
              matching: find.byType(Material),
            )
            .first;
        expect(tester.getSize(liveGamesCard).height, greaterThan(140));
        final icon = find.descendant(
          of: liveGamesCard,
          matching: find.byIcon(Icons.emoji_events_rounded),
        );
        final iconTile = find
            .ancestor(of: icon, matching: find.byType(Container))
            .first;
        final subtitle = find.descendant(
          of: liveGamesCard,
          matching: find.text('Play with others'),
        );
        final contentCenter =
            (tester.getTopLeft(iconTile).dy +
                tester.getBottomRight(subtitle).dy) /
            2;
        expect(
          contentCenter,
          closeTo(tester.getRect(liveGamesCard).center.dy, 1),
        );
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets('home cards render with the dark color scheme', (tester) async {
    await tester.pumpWidget(_home(onGames: () {}, brightness: Brightness.dark));
    await tester.pumpAndSettle();

    expect(find.text('Play Bingo'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
