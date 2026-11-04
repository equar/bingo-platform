import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/features/community/data/community_repository.dart';
import 'package:bingo_app/src/features/community/domain/community_models.dart';
import 'package:bingo_app/src/features/community/presentation/private_games_screen.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('private-game load failures never display raw backend details', (
    tester,
  ) async {
    final request = RequestOptions(path: '/api/v1/private-games');
    final repository = _FailingCommunityRepository(
      DioException(
        requestOptions: request,
        response: Response(
          requestOptions: request,
          statusCode: 500,
          data: {'message': 'SELECT secret_column FROM private_users'},
        ),
      ),
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [communityRepositoryProvider.overrideWithValue(repository)],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const PrivateGamesScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.textContaining('secret_column'), findsNothing);
    expect(find.textContaining('private_users'), findsNothing);
    expect(find.text('Try again'), findsOneWidget);
  });
}

class _FailingCommunityRepository extends CommunityRepository {
  _FailingCommunityRepository(this.error) : super(Dio());

  final DioException error;

  @override
  Future<List<PrivateGame>> privateGames() => Future.error(error);
}
