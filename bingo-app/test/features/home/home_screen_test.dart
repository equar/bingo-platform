import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:dio/dio.dart';
import 'package:bingo_app/src/features/community/data/community_repository.dart';
import 'package:bingo_app/src/features/community/domain/community_models.dart';
import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/features/home/presentation/home_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';

Widget _home({
  required VoidCallback onGames,
  VoidCallback? onSupport,
  double textScale = 1,
}) => ProviderScope(
  overrides: [
    communityRepositoryProvider.overrideWithValue(_CommunityRepository()),
  ],
  child: MaterialApp(
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
        onWallet: () {},
        onProfile: () {},
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

  testWidgets('support remains accessible from the menu', (tester) async {
    var opened = false;
    await tester.pumpWidget(
      _home(onGames: () {}, onSupport: () => opened = true),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('home-menu')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('support-tab')));
    await tester.pumpAndSettle();
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
    await tester.ensureVisible(find.text('Play & Win'));
    expect(tester.takeException(), isNull);
  });
}
