import 'package:bingo_app/src/app.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _displayName = String.fromEnvironment('BINGO_M03D_NAME');
const _phone = String.fromEnvironment('BINGO_M03D_PHONE');
const _pin = String.fromEnvironment('BINGO_M03D_PIN');
var _surfaceConverted = false;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Module 03D registration initializes an unfunded ETB wallet', (tester) async {
    expect(_displayName, isNotEmpty);
    expect(_phone, isNotEmpty);
    expect(_pin, hasLength(6));

    await tester.pumpWidget(const BingoApp());
    await _waitForAuthOrHome(tester);
    if (find.byType(NavigationBar).evaluate().isNotEmpty) {
      await tester.tap(find.text('Profile'));
      await _waitFor(tester, find.text('Logout'));
      await tester.tap(find.text('Logout'));
    }
    await _waitFor(tester, find.byKey(const ValueKey('auth-submit-button')));
    await tester.tap(find.byKey(const ValueKey('auth-toggle-button')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const ValueKey('auth-display-name-field')), _displayName);
    await tester.enterText(find.byKey(const ValueKey('auth-phone-field')), _phone);
    await tester.enterText(find.byKey(const ValueKey('auth-password-field')), _pin);
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await _waitFor(tester, find.byType(NavigationBar));

    await tester.tap(find.text('Wallet'));
    await _waitFor(tester, find.text('0.0 ETB'));
    await _waitFor(tester, find.textContaining('Available: 0.0 ETB'));
    await _waitFor(tester, find.textContaining('Promotional: 0.0 ETB'));
    expect(find.textContaining('25.0 ETB'), findsNothing);
    await _captureScreenshot(tester, 'module-03d-wallet-zero-balance');
    await tester.runAsync(() => Future<void>.delayed(const Duration(seconds: 15)));
  });
}

Future<void> _waitForAuthOrHome(WidgetTester tester) async {
  final deadline = DateTime.now().add(const Duration(seconds: 40));
  final loginButton = find.byKey(const ValueKey('auth-submit-button'));
  final navigationBar = find.byType(NavigationBar);
  while (DateTime.now().isBefore(deadline) &&
      loginButton.evaluate().isEmpty &&
      navigationBar.evaluate().isEmpty) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  expect(loginButton.evaluate().isNotEmpty || navigationBar.evaluate().isNotEmpty, isTrue);
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
