import 'package:bingo_app/src/app.dart';
import 'package:bingo_app/src/core/storage/session_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _unknownPhone = '251930399996';
const _wrongPin = '999999';
var _surfaceConverted = false;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Module 03B repeated invalid attempts are rate limited without account creation', (tester) async {
    await const SessionStorage().clear();
    await tester.pumpWidget(const BingoApp());
    final authButton = find.byKey(const ValueKey('auth-submit-button'));
    await _waitFor(tester, authButton);
    final phoneField = find.byKey(const ValueKey('auth-phone-field'));
    final pinField = find.byKey(const ValueKey('auth-password-field'));
    for (var attempt = 0; attempt < 6; attempt++) {
      await tester.enterText(phoneField, _unknownPhone);
      await tester.enterText(pinField, _wrongPin);
      await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
      if (attempt < 5) {
        await _waitFor(tester, find.textContaining('AUTH_INVALID_CREDENTIALS'));
        await _waitForSubmitEnabled(tester);
      } else {
        await _waitForSubmitEnabled(tester);
        expect(find.byType(NavigationBar), findsNothing);
        expect(tester.widgetList<Text>(find.byType(Text)).where((text) {
          final value = text.data ?? '';
          return value.contains('AUTH_') || value.contains('HTTP_') || value.contains('Too many');
        }), isNotEmpty);
      }
    }
    expect(find.byType(NavigationBar), findsNothing);
    await _captureScreenshot(tester, 'module-03b-login-rate-limited');
    await tester.runAsync(() => Future<void>.delayed(const Duration(seconds: 15)));
  });
}

Future<void> _waitForSubmitEnabled(WidgetTester tester) async {
  final finder = find.byKey(const ValueKey('auth-submit-button'));
  final deadline = DateTime.now().add(const Duration(seconds: 35));
  while (DateTime.now().isBefore(deadline) && tester.widget<FilledButton>(finder).onPressed == null) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  expect(tester.widget<FilledButton>(finder).onPressed, isNotNull);
}

Future<void> _waitFor(WidgetTester tester, Finder finder) async {
  final deadline = DateTime.now().add(const Duration(seconds: 35));
  while (DateTime.now().isBefore(deadline) && finder.evaluate().isEmpty) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  expect(finder, findsOneWidget);
}

Future<void> _captureScreenshot(WidgetTester tester, String name) async {
  final binding = IntegrationTestWidgetsFlutterBinding.instance;
  if (!_surfaceConverted) {
    await binding.convertFlutterSurfaceToImage();
    _surfaceConverted = true;
  }
  await tester.pump();
  await binding.takeScreenshot(name);
}
