import 'package:bingo_agent/src/app.dart';
import 'package:bingo_agent/src/core/config/app_config.dart';
import 'package:bingo_agent/src/core/storage/session_storage.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Agent can switch and restore language before signing in', (
    tester,
  ) async {
    const storage = SessionStorage();
    final previousSession = await storage.readSession();
    final sessionValues = previousSession.values.toList();
    final hadSession = sessionValues.any((value) => value != null);
    final canRestoreSession =
        previousSession['token'] != null &&
        previousSession['sessionJson'] != null;
    if (hadSession && !canRestoreSession) {
      fail('Refusing to alter an incomplete stored Agent session.');
    }

    final preferences = await SharedPreferences.getInstance();
    final previousLocale = preferences.getString('agent.locale');
    addTearDown(() async {
      await storage.clear();
      if (hadSession) {
        await storage.saveSession(
          accessToken: previousSession['token']!,
          sessionJson: previousSession['sessionJson']!,
        );
      }
      if (previousLocale == null) {
        await preferences.remove('agent.locale');
      } else {
        await preferences.setString('agent.locale', previousLocale);
      }
    });

    await storage.clear();
    await preferences.setString('agent.locale', 'en');
    const config = AppConfig(
      apiBaseUrl: 'http://10.0.2.2:8082',
      apiBaseUrlLabel: 'API_BASE_URL',
    );
    await tester.pumpWidget(const BingoAgentApp(config: config));
    await _waitFor(tester, find.byKey(const ValueKey('agent-login-screen')));

    final fields = find.byType(TextFormField);
    await tester.enterText(fields.first, '0911000000');
    await tester.tap(find.byKey(const ValueKey('agent-language-selector')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('አማርኛ').last);
    await tester.pumpAndSettle();

    final amharic = await AppLocalizations.delegate.load(const Locale('am'));
    expect(find.text(amharic.loginTitle), findsOneWidget);
    expect(
      tester.widget<TextFormField>(fields.first).controller!.text,
      '0911000000',
    );

    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpWidget(const BingoAgentApp(config: config));
    await _waitFor(tester, find.byKey(const ValueKey('agent-login-screen')));
    expect(find.text(amharic.loginTitle), findsOneWidget);
  });
}

Future<void> _waitFor(WidgetTester tester, Finder finder) async {
  final deadline = DateTime.now().add(const Duration(seconds: 15));
  while (DateTime.now().isBefore(deadline) && finder.evaluate().isEmpty) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  expect(finder, findsOneWidget);
}
