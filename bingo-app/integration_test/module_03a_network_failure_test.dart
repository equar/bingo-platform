import 'package:bingo_app/src/app.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _displayName = String.fromEnvironment('BINGO_M03A_NAME');
const _phone = String.fromEnvironment('BINGO_M03A_PHONE');
const _pin = String.fromEnvironment('BINGO_M03A_PIN');
var _surfaceConverted = false;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Module 03A network failure leaves registration retryable without a session', (tester) async {
    expect(_displayName, isNotEmpty);
    expect(_phone, isNotEmpty);
    expect(_pin, hasLength(6));

    await tester.pumpWidget(const BingoApp());
    await _waitFor(tester, find.byKey(const ValueKey('auth-submit-button')));
    await tester.tap(find.byKey(const ValueKey('auth-toggle-button')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const ValueKey('auth-display-name-field')), _displayName);
    await tester.enterText(find.byKey(const ValueKey('auth-phone-field')), _phone);
    await tester.enterText(find.byKey(const ValueKey('auth-password-field')), _pin);
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));

    await _waitFor(tester, find.textContaining('HTTP_0'));
    expect(find.byType(NavigationBar), findsNothing);
    expect(find.byKey(const ValueKey('auth-submit-button')), findsOneWidget);
    await _captureScreenshot(tester, 'module-03a-network-failure-retry-ready');
  });
}

Future<void> _waitFor(WidgetTester tester, Finder finder) async {
  final deadline = DateTime.now().add(const Duration(seconds: 40));
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
