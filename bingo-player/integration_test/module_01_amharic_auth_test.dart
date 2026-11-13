import 'package:bingo_player/src/app.dart';
import 'package:bingo_player/src/core/storage/session_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _phone = String.fromEnvironment('BINGO_QA_PLAYER_001_PHONE');
const _pin = String.fromEnvironment('BINGO_QA_PLAYER_001_PIN');
const _invalidCredentialsMessage = 'የስልክ ቁጥሩ ወይም ፒኑ ትክክል አይደለም።';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Player renders the real invalid-login error in Amharic', (
    tester,
  ) async {
    expect(_phone, isNotEmpty);
    expect(_pin, hasLength(6));

    const storage = SessionStorage();
    final previousSession = await storage.readSession();
    final previousValues = previousSession.values.toList();
    final hadSession = previousValues.any((value) => value != null);
    final canRestoreSession =
        previousSession['token'] != null &&
        previousSession['expiresAt'] != null &&
        previousSession['userJson'] != null;
    if (hadSession && !canRestoreSession) {
      fail('Refusing to alter an incomplete stored Player session.');
    }
    addTearDown(() async {
      await storage.clear();
      if (hadSession) {
        await storage.saveSession(
          accessToken: previousSession['token']!,
          expiresAtIso: previousSession['expiresAt']!,
          userJson: previousSession['userJson']!,
        );
      }
    });
    await storage.clear();
    await tester.pumpWidget(const BingoApp());
    final submitButton = find.byKey(const ValueKey('auth-submit-button'));
    await _waitFor(tester, submitButton);
    final phoneField = find.byKey(const ValueKey('auth-phone-field'));
    final pinField = find.byKey(const ValueKey('auth-password-field'));
    await tester.enterText(phoneField, _phone);
    await tester.enterText(pinField, _pin == '000000' ? '111111' : '000000');
    await tester.tap(submitButton);
    await _waitFor(
      tester,
      find.text(_invalidCredentialsMessage),
      timeout: const Duration(seconds: 45),
    );

    await tester.enterText(phoneField, '');
    await tester.enterText(pinField, '');
    FocusManager.instance.primaryFocus?.unfocus();
    await tester.pumpAndSettle();
    expect(find.text(_invalidCredentialsMessage), findsOneWidget);
    expect(find.byType(NavigationBar), findsNothing);

    final binding = IntegrationTestWidgetsFlutterBinding.instance;
    await binding.convertFlutterSurfaceToImage();
    await tester.pump();
    await binding.takeScreenshot('module-01-player-amharic-invalid-login');
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
