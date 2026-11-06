import 'package:bingo_agent/src/app.dart';
import 'package:bingo_agent/src/core/storage/session_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _phone = String.fromEnvironment('BINGO_QA_APPROVED_AGENT_PHONE');
const _pin = String.fromEnvironment('BINGO_QA_APPROVED_AGENT_PIN');
const _invalidCredentialsMessage = 'የስልክ ቁጥሩ ወይም ፒኑ ትክክል አይደለም።';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Agent renders the real invalid-login error in Amharic', (
    tester,
  ) async {
    expect(_phone, isNotEmpty);
    expect(_pin, isNotEmpty);

    const storage = SessionStorage();
    final previousSession = await storage.readSession();
    final previousValues = previousSession.values.toList();
    final hadSession = previousValues.any((value) => value != null);
    final canRestoreSession =
        previousSession['token'] != null &&
        previousSession['sessionJson'] != null;
    if (hadSession && !canRestoreSession) {
      fail('Refusing to alter an incomplete stored Agent session.');
    }
    addTearDown(() async {
      await storage.clear();
      if (hadSession) {
        await storage.saveSession(
          accessToken: previousSession['token']!,
          sessionJson: previousSession['sessionJson']!,
        );
      }
    });
    await storage.clear();
    await tester.pumpWidget(const BingoAgentApp());
    await tester.pumpAndSettle();
    await _waitFor(tester, find.byKey(const ValueKey('agent-login-screen')));
    final fields = find.byType(TextFormField);
    expect(fields, findsNWidgets(2));
    await tester.enterText(fields.first, _phone);
    await tester.enterText(fields.last, _pin == '000000' ? '111111' : '000000');
    await tester.tap(find.byType(FilledButton).first);
    await _waitFor(
      tester,
      find.text(_invalidCredentialsMessage),
      timeout: const Duration(seconds: 45),
    );

    await tester.enterText(fields.first, '');
    await tester.enterText(fields.last, '');
    FocusManager.instance.primaryFocus?.unfocus();
    await tester.pumpAndSettle();
    expect(find.text(_invalidCredentialsMessage), findsOneWidget);
    expect(find.byType(NavigationBar), findsNothing);

    final binding = IntegrationTestWidgetsFlutterBinding.instance;
    await binding.convertFlutterSurfaceToImage();
    await tester.pump();
    await binding.takeScreenshot('module-01-agent-amharic-invalid-login');
  });
}

Future<void> _waitFor(
  WidgetTester tester,
  Finder finder, {
  Duration timeout = const Duration(seconds: 15),
}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline) && finder.evaluate().isEmpty) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  expect(finder, findsOneWidget);
}
