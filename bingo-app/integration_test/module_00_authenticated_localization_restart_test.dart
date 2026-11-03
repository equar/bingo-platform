import 'dart:convert';

import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/app.dart';
import 'package:bingo_app/src/core/config/app_config.dart';
import 'package:bingo_app/src/core/storage/session_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _apiUrl = String.fromEnvironment('API_BASE_URL');
const _phone = String.fromEnvironment('BINGO_QA_PLAYER_001_PHONE');
const _pin = String.fromEnvironment('BINGO_QA_PLAYER_001_PIN');
const _preserveSession = bool.fromEnvironment('PRESERVE_PLAYER_SESSION');

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'Player persists Amharic preference and authenticated session for restart',
    (tester) async {
      expect(_apiUrl, isNotEmpty);
      expect(_phone, isNotEmpty);
      expect(_pin, hasLength(6));

      const storage = SessionStorage();
      final previousSession = await storage.readSession();
      expect(
        previousSession.values.every((value) => value == null),
        isTrue,
        reason: 'Refusing to replace an existing Player session.',
      );

      final preferences = await SharedPreferences.getInstance();
      final previousLocale = preferences.getString('player.locale');
      var authenticated = false;
      addTearDown(() async {
        if (_preserveSession && authenticated) return;

        await storage.clear();
        if (previousLocale == null) {
          await preferences.remove('player.locale');
        } else {
          await preferences.setString('player.locale', previousLocale);
        }
      });

      expect(await preferences.setString('player.locale', 'en'), isTrue);
      await tester.pumpWidget(
        BingoApp(
          config: AppConfig(
            apiBaseUrl: _apiUrl,
            apiBaseUrlLabel: 'API_BASE_URL',
          ),
        ),
      );
      await _waitFor(tester, find.byKey(const ValueKey('auth-phone-field')));

      final amharic = await AppLocalizations.delegate.load(const Locale('am'));
      await tester.tap(find.byKey(const ValueKey('player-language-selector')));
      await tester.pumpAndSettle();
      await tester.tap(find.text(amharic.languageAmharic).last);
      await tester.pumpAndSettle();
      expect(find.text(amharic.loginSubtitle), findsOneWidget);

      await tester.enterText(
        find.byKey(const ValueKey('auth-phone-field')),
        _phone,
      );
      await tester.enterText(
        find.byKey(const ValueKey('auth-password-field')),
        _pin,
      );
      await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
      await _waitFor(tester, find.byType(NavigationBar));

      final session = await storage.readSession();
      expect(session['token'], isNotEmpty);
      final expiresAt = DateTime.tryParse(session['expiresAt'] ?? '');
      expect(expiresAt?.isAfter(DateTime.now().toUtc()), isTrue);
      final user = jsonDecode(session['userJson']!) as Map<String, dynamic>;
      expect(user['status'], 'ACTIVE');
      expect((user['roles'] as List? ?? const []).contains('PLAYER'), isTrue);
      expect(preferences.getString('player.locale'), 'am');
      expect(find.text(amharic.homeTitle), findsWidgets);

      await _captureScreenshot(
        tester,
        'module-00-player-amharic-authenticated',
      );
      authenticated = true;
    },
  );
}

Future<void> _waitFor(WidgetTester tester, Finder finder) async {
  final deadline = DateTime.now().add(const Duration(seconds: 15));
  while (DateTime.now().isBefore(deadline) && finder.evaluate().isEmpty) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  expect(finder, findsOneWidget);
}

Future<void> _captureScreenshot(WidgetTester tester, String name) async {
  final binding = IntegrationTestWidgetsFlutterBinding.instance;
  await binding.convertFlutterSurfaceToImage();
  await tester.pump();
  await binding.takeScreenshot(
    '$name-${DateTime.now().toUtc().microsecondsSinceEpoch}',
  );
}
