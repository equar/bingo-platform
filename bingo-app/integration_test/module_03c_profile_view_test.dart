import 'package:bingo_app/src/app.dart';
import 'package:bingo_app/src/core/storage/session_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _phone = String.fromEnvironment('BINGO_M03B_PHONE');
const _pin = String.fromEnvironment('BINGO_M03B_PIN');
const _name = String.fromEnvironment('BINGO_M03B_NAME');
var _surfaceConverted = false;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'Module 03C authenticated player profile loads and refreshes from server',
    (tester) async {
      expect(_phone, isNotEmpty);
      expect(_pin, hasLength(6));
      expect(_name, isNotEmpty);

      await const SessionStorage().clear();
      await tester.pumpWidget(const BingoApp());
      final authButton = find.byKey(const ValueKey('auth-submit-button'));
      await _waitFor(tester, authButton);
      await tester.enterText(
        find.byKey(const ValueKey('auth-phone-field')),
        _phone,
      );
      await tester.enterText(
        find.byKey(const ValueKey('auth-password-field')),
        _pin,
      );
      await tester.tap(authButton);
      await _waitFor(tester, find.byType(NavigationBar));

      await tester.tap(find.text('Profile'));
      await tester.pump();
      expect(
        find.byKey(const ValueKey('profile-loading-indicator')),
        findsOneWidget,
      );
      await _waitFor(tester, find.text(_name));
      await _waitFor(tester, find.text('251930300001'));
      await _waitForRefreshComplete(tester);
      expect(find.text('PLAYER'), findsOneWidget);
      expect(find.textContaining('Member since:'), findsOneWidget);
      expect(find.byKey(const ValueKey('profile-retry-button')), findsNothing);

      await tester.tap(find.byKey(const ValueKey('profile-refresh-button')));
      await _waitForRefreshComplete(tester);
      expect(find.text(_name), findsOneWidget);
      expect(find.text('251930300001'), findsOneWidget);
      await _captureScreenshot(tester, 'module-03c-profile-loaded');
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(seconds: 15)),
      );
    },
  );
}

Future<void> _waitFor(WidgetTester tester, Finder finder) async {
  final deadline = DateTime.now().add(const Duration(seconds: 40));
  while (DateTime.now().isBefore(deadline) && finder.evaluate().isEmpty) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  expect(finder, findsOneWidget);
}

Future<void> _waitForRefreshComplete(WidgetTester tester) async {
  final finder = find.byKey(const ValueKey('profile-refresh-button'));
  final deadline = DateTime.now().add(const Duration(seconds: 40));
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
