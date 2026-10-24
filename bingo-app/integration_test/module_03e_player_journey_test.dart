import 'package:bingo_app/src/app.dart';
import 'package:bingo_app/src/core/storage/session_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _displayName = String.fromEnvironment('BINGO_M03E_NAME');
const _phone = String.fromEnvironment('BINGO_M03E_PHONE');
const _pin = String.fromEnvironment('BINGO_M03E_PIN');
var _surfaceConverted = false;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Module 03E player account journey and local logout', (tester) async {
    expect(_displayName, isNotEmpty);
    expect(_phone, isNotEmpty);
    expect(_pin, hasLength(6));

    await const SessionStorage().clear();
    await tester.pumpWidget(const BingoApp());
    await _waitFor(tester, find.byKey(const ValueKey('auth-submit-button')));

    // J01: Register through the actual Android application UI.
    await tester.tap(find.byKey(const ValueKey('auth-toggle-button')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const ValueKey('auth-display-name-field')), _displayName);
    await tester.enterText(find.byKey(const ValueKey('auth-phone-field')), _phone);
    await tester.enterText(find.byKey(const ValueKey('auth-password-field')), _pin);
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await _waitFor(tester, find.byType(NavigationBar));

    // J02-J04 and J16: check the account opens with an unfunded wallet.
    await tester.tap(find.text('Wallet'));
    await _waitFor(tester, find.text('0.0 ETB'));
    await _waitFor(tester, find.textContaining('Available: 0.0 ETB'));
    await _waitFor(tester, find.textContaining('Promotional: 0.0 ETB'));
    expect(find.textContaining('25.0 ETB'), findsNothing);

    // J05: Sign out locally, then exercise incorrect and valid PIN login.
    await tester.tap(find.text('Profile'));
    await _waitFor(tester, find.text(_displayName));
    await tester.tap(find.text('Logout'));
    await _waitFor(tester, find.byKey(const ValueKey('auth-submit-button')));
    expect(find.byType(NavigationBar), findsNothing);

    // J14: Incorrect PIN must not establish a session.
    await tester.enterText(find.byKey(const ValueKey('auth-phone-field')), _phone);
    await tester.enterText(find.byKey(const ValueKey('auth-password-field')), '999999');
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await _waitFor(tester, find.textContaining('AUTH_INVALID_CREDENTIALS'));
    expect(find.byType(NavigationBar), findsNothing);

    // J06-J09: Log in, verify identity and refresh it from the server.
    await tester.enterText(find.byKey(const ValueKey('auth-phone-field')), _phone);
    await tester.enterText(find.byKey(const ValueKey('auth-password-field')), _pin);
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await _waitFor(tester, find.byType(NavigationBar));
    await tester.tap(find.text('Profile'));
    await _waitFor(tester, find.text(_displayName));
    await _waitFor(tester, find.text(_phone));
    expect(find.text('PLAYER'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('profile-refresh-button')));
    await _waitForRefreshComplete(tester);
    expect(find.text(_displayName), findsOneWidget);
    expect(find.text(_phone), findsOneWidget);
    await _captureScreenshot(tester, 'module-03e-player-profile');

    // Leave the authenticated session in secure storage for the process-restart run.
    await tester.runAsync(() => Future<void>.delayed(const Duration(seconds: 15)));
  });
}

Future<void> _waitFor(WidgetTester tester, Finder finder) async {
  final deadline = DateTime.now().add(const Duration(seconds: 45));
  while (DateTime.now().isBefore(deadline) && finder.evaluate().isEmpty) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  expect(finder, findsOneWidget);
}

Future<void> _waitForRefreshComplete(WidgetTester tester) async {
  final finder = find.byKey(const ValueKey('profile-refresh-button'));
  final deadline = DateTime.now().add(const Duration(seconds: 45));
  while (DateTime.now().isBefore(deadline) &&
      tester.widget<IconButton>(finder).onPressed == null) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  expect(tester.widget<IconButton>(finder).onPressed, isNotNull);
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
