import 'dart:convert';

import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/app.dart';
import 'package:bingo_player/src/core/storage/session_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _expectedLocale = String.fromEnvironment(
  'PLAYER_EXPECT_LOCALE',
  defaultValue: 'en',
);

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Player session and language restore after a new app process', (
    tester,
  ) async {
    expect(['en', 'am'], contains(_expectedLocale));
    final preferences = await SharedPreferences.getInstance();
    expect(preferences.getString('player.locale') ?? 'en', _expectedLocale);
    final localizations = await AppLocalizations.delegate.load(
      Locale(_expectedLocale),
    );

    await tester.pumpWidget(const BingoApp());
    await _waitFor(tester, find.byType(NavigationBar));
    expect(find.text(localizations.homeTitle), findsWidgets);

    final session = await const SessionStorage().readSession();
    final storedToken = session['token'];
    expect(storedToken, isNotEmpty);
    final expiresAt = DateTime.tryParse(session['expiresAt'] ?? '');
    expect(expiresAt?.isAfter(DateTime.now().toUtc()), isTrue);
    final user = jsonDecode(session['userJson']!) as Map<String, dynamic>;
    expect(user['status'], 'ACTIVE');
    expect((user['roles'] as List? ?? const []).contains('PLAYER'), isTrue);
    await _captureScreenshot(tester);

    await tester.tap(find.text(localizations.homeProfile).last);
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text(localizations.logout));
    await tester.pumpAndSettle();
    await tester.tap(find.text(localizations.logout));
    await _waitFor(tester, find.byKey(const ValueKey('auth-submit-button')));
    expect((await const SessionStorage().readSession())['token'], isNull);
  });
}

Future<void> _waitFor(WidgetTester tester, Finder finder) async {
  final deadline = DateTime.now().add(const Duration(seconds: 15));
  while (DateTime.now().isBefore(deadline) && finder.evaluate().isEmpty) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  expect(finder, findsOneWidget);
}

Future<void> _captureScreenshot(WidgetTester tester) async {
  final binding = IntegrationTestWidgetsFlutterBinding.instance;
  await binding.convertFlutterSurfaceToImage();
  await tester.pump();
  await binding.takeScreenshot(
    'module-01-player-process-restart-${DateTime.now().toUtc().microsecondsSinceEpoch}',
  );
}
