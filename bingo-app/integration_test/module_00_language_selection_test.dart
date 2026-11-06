import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/app.dart';
import 'package:bingo_app/src/core/config/app_config.dart';
import 'package:bingo_app/src/core/storage/session_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Player can switch and restore language before signing in', (
    tester,
  ) async {
    const storage = SessionStorage();
    final previousSession = await storage.readSession();
    final sessionValues = previousSession.values.toList();
    final hadSession = sessionValues.any((value) => value != null);
    final canRestoreSession =
        previousSession['token'] != null &&
        previousSession['expiresAt'] != null &&
        previousSession['userJson'] != null;
    if (hadSession && !canRestoreSession) {
      fail('Refusing to alter an incomplete stored Player session.');
    }

    final preferences = await SharedPreferences.getInstance();
    final previousLocale = preferences.getString('player.locale');
    addTearDown(() async {
      await storage.clear();
      if (hadSession) {
        await storage.saveSession(
          accessToken: previousSession['token']!,
          expiresAtIso: previousSession['expiresAt']!,
          userJson: previousSession['userJson']!,
        );
      }
      if (previousLocale == null) {
        await preferences.remove('player.locale');
      } else {
        await preferences.setString('player.locale', previousLocale);
      }
    });

    await storage.clear();
    await preferences.setString('player.locale', 'en');
    const config = AppConfig(
      apiBaseUrl: 'http://10.0.2.2:8082',
      apiBaseUrlLabel: 'API_BASE_URL',
    );
    await tester.pumpWidget(const BingoApp(config: config));
    await _waitFor(tester, find.byKey(const ValueKey('auth-phone-field')));

    final phoneField = find.byKey(const ValueKey('auth-phone-field'));
    await tester.enterText(phoneField, '0911000000');
    await tester.tap(find.byKey(const ValueKey('player-language-selector')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('አማርኛ').last);
    await tester.pumpAndSettle();

    final amharic = await AppLocalizations.delegate.load(const Locale('am'));
    expect(find.text(amharic.loginSubtitle), findsOneWidget);
    expect(
      tester.widget<TextFormField>(phoneField).controller!.text,
      '0911000000',
    );

    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpWidget(const BingoApp(config: config));
    await _waitFor(tester, find.byKey(const ValueKey('auth-phone-field')));
    expect(find.text(amharic.loginSubtitle), findsOneWidget);
  });
}

Future<void> _waitFor(WidgetTester tester, Finder finder) async {
  final deadline = DateTime.now().add(const Duration(seconds: 15));
  while (DateTime.now().isBefore(deadline) && finder.evaluate().isEmpty) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  expect(finder, findsOneWidget);
}
