import 'package:bingo_agent/src/app.dart';
import 'package:bingo_agent/src/core/config/app_config.dart';
import 'package:bingo_agent/src/core/storage/session_storage.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _phone = String.fromEnvironment('BINGO_QA_APPROVED_AGENT_PHONE');
const _pin = String.fromEnvironment('BINGO_QA_APPROVED_AGENT_PIN');
const _preserveSessionForRestart = bool.fromEnvironment(
  'PRESERVE_AGENT_SESSION',
);

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'authenticated Agent can switch and restore the selected language',
    (tester) async {
      expect(_phone, isNotEmpty);
      expect(_pin, isNotEmpty);

      const storage = SessionStorage();
      final previousSession = await storage.readSession();
      final hadSession = previousSession.values.any((value) => value != null);
      if (hadSession) {
        fail('Refusing to replace an existing authenticated Agent session.');
      }

      final preferences = await SharedPreferences.getInstance();
      final previousLocale = preferences.getString('agent.locale');
      var completed = false;
      addTearDown(() async {
        if (!completed || !_preserveSessionForRestart) {
          await storage.clear();
          if (previousLocale == null) {
            await preferences.remove('agent.locale');
          } else {
            await preferences.setString('agent.locale', previousLocale);
          }
        }
      });

      await storage.clear();
      await preferences.setString('agent.locale', 'en');
      await tester.pumpWidget(
        const BingoAgentApp(
          config: AppConfig(
            apiBaseUrl: 'http://10.0.2.2:8080',
            apiBaseUrlLabel: 'API_BASE_URL',
          ),
        ),
      );
      await _waitFor(tester, find.byKey(const ValueKey('agent-login-screen')));

      final english = await AppLocalizations.delegate.load(const Locale('en'));
      final amharic = await AppLocalizations.delegate.load(const Locale('am'));
      await _selectLanguage(tester, amharic.languageAmharic);
      expect(find.text(amharic.loginTitle), findsOneWidget);

      final fields = find.byType(TextFormField);
      await tester.enterText(fields.at(0), _phone);
      await tester.enterText(fields.at(1), '0000');
      await tester.tap(find.byType(FilledButton));
      await _waitFor(tester, find.text(amharic.errorInvalidCredentials));
      expect(find.byType(NavigationBar), findsNothing);
      expect((await storage.readSession())['token'], isNull);

      await tester.enterText(fields.at(0), _phone);
      await tester.enterText(fields.at(1), _pin);
      await tester.tap(find.byType(FilledButton));
      await _waitFor(tester, find.byType(NavigationBar));

      expect(find.text(amharic.dashboardTitle), findsWidgets);
      expect(find.byKey(const ValueKey('agent-home-screen')), findsOneWidget);
      expect((await storage.readSession())['token'], isNotEmpty);

      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpWidget(
        const BingoAgentApp(
          config: AppConfig(
            apiBaseUrl: 'http://10.0.2.2:8080',
            apiBaseUrlLabel: 'API_BASE_URL',
          ),
        ),
      );
      await _waitFor(tester, find.byKey(const ValueKey('agent-home-screen')));
      expect(find.text(amharic.dashboardTitle), findsWidgets);
      expect((await storage.readSession())['token'], isNotEmpty);

      await _selectLanguage(tester, english.languageEnglish);
      expect(find.text(english.dashboardTitle), findsWidgets);
      expect((await storage.readSession())['token'], isNotEmpty);

      await _selectLanguage(tester, amharic.languageAmharic);
      expect(find.text(amharic.dashboardTitle), findsWidgets);
      expect((await storage.readSession())['token'], isNotEmpty);
      completed = true;
    },
  );
}

Future<void> _selectLanguage(WidgetTester tester, String label) async {
  await tester.tap(find.byKey(const ValueKey('agent-language-selector')));
  await tester.pumpAndSettle();
  await tester.tap(find.text(label).last);
  await tester.pumpAndSettle();
}

Future<void> _waitFor(WidgetTester tester, Finder finder) async {
  final deadline = DateTime.now().add(const Duration(seconds: 30));
  while (DateTime.now().isBefore(deadline) && finder.evaluate().isEmpty) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  expect(finder, findsOneWidget);
}
