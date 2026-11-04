import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/features/community/data/community_repository.dart';
import 'package:bingo_app/src/features/community/domain/community_models.dart';
import 'package:bingo_app/src/features/community/presentation/daily_challenges_screen.dart';
import 'package:bingo_app/src/features/community/presentation/leaderboard_screen.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('daily challenge load errors are safe', (tester) async {
    final repository = _FakeCommunityRepository(
      dailyError: _serverError('/api/v1/challenges/daily'),
    );
    await tester.pumpWidget(_app(repository, const DailyChallengesScreen()));
    await tester.pumpAndSettle();

    expect(find.text('Something went wrong.'), findsOneWidget);
    expect(find.textContaining('SELECT secret_column'), findsNothing);
    expect(find.text('Try again'), findsOneWidget);
  });

  testWidgets('daily challenge claim errors are safe', (tester) async {
    final repository = _FakeCommunityRepository(
      dailyResult: DailyProgress(
        businessDate: '2026-01-01',
        timezone: 'Africa/Addis_Ababa',
        resetsAt: DateTime.utc(2026, 1, 2),
        totalPoints: 0,
        challenges: const [
          DailyChallenge(
            code: 'FINISH_GAME',
            target: 1,
            progress: 1,
            points: 10,
            completed: true,
            claimed: false,
          ),
        ],
      ),
      actionError: _serverError('/api/v1/challenges/FINISH_GAME/claim'),
    );
    await tester.pumpWidget(_app(repository, const DailyChallengesScreen()));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Claim points'));
    await tester.pumpAndSettle();

    expect(find.text('Something went wrong.'), findsOneWidget);
    expect(find.textContaining('SELECT secret_column'), findsNothing);
  });

  testWidgets('leaderboard load errors are safe', (tester) async {
    final repository = _FakeCommunityRepository(
      leaderboardError: _serverError('/api/v1/leaderboard'),
    );
    await tester.pumpWidget(_app(repository, const LeaderboardScreen()));
    await tester.pumpAndSettle();

    expect(find.text('Something went wrong.'), findsOneWidget);
    expect(find.textContaining('SELECT secret_column'), findsNothing);
  });

  testWidgets('leaderboard profile-save errors are safe', (tester) async {
    final repository = _FakeCommunityRepository(
      rankingResult: const Ranking(
        entries: [],
        totalPlayers: 0,
        page: 0,
        size: 25,
        myPoints: 0,
        myRank: null,
        alias: null,
        visible: false,
        timezone: 'Africa/Addis_Ababa',
      ),
      actionError: _serverError('/api/v1/leaderboard/profile'),
    );
    await tester.pumpWidget(_app(repository, const LeaderboardScreen()));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(TextButton, 'Public ranking profile'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Save'));
    await tester.pumpAndSettle();

    expect(find.text('Something went wrong.'), findsOneWidget);
    expect(find.textContaining('SELECT secret_column'), findsNothing);
  });
}

Widget _app(CommunityRepository repository, Widget home) => ProviderScope(
  overrides: [communityRepositoryProvider.overrideWithValue(repository)],
  child: MaterialApp(
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: home,
  ),
);

DioException _serverError(String path) {
  final request = RequestOptions(path: path);
  return DioException(
    requestOptions: request,
    response: Response(
      requestOptions: request,
      statusCode: 500,
      data: {'message': 'SELECT secret_column FROM private_users'},
    ),
  );
}

class _FakeCommunityRepository extends CommunityRepository {
  _FakeCommunityRepository({
    this.dailyError,
    this.leaderboardError,
    this.dailyResult,
    this.rankingResult,
    this.actionError,
  }) : super(Dio());

  final Object? dailyError;
  final Object? leaderboardError;
  final DailyProgress? dailyResult;
  final Ranking? rankingResult;
  final Object? actionError;

  @override
  Future<DailyProgress> daily() => dailyError != null
      ? Future.error(dailyError!)
      : Future.value(dailyResult!);

  @override
  Future<Ranking> leaderboard(String period, int page) =>
      leaderboardError != null
      ? Future.error(leaderboardError!)
      : Future.value(rankingResult!);

  @override
  Future<void> claim(String code, String day, String key) =>
      Future.error(actionError!);

  @override
  Future<void> saveProfile(String? alias, bool visible) =>
      Future.error(actionError!);
}
