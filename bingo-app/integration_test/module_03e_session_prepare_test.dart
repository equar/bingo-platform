import 'package:bingo_app/src/app.dart';
import 'package:bingo_app/src/core/storage/session_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _phone = String.fromEnvironment('BINGO_M03E_PHONE');
const _pin = String.fromEnvironment('BINGO_M03E_PIN');

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Module 03E restores an authenticated player session', (tester) async {
    expect(_phone, isNotEmpty);
    expect(_pin, hasLength(6));
    await const SessionStorage().clear();
    await tester.pumpWidget(const BingoApp());
    await _waitFor(tester, find.byKey(const ValueKey('auth-submit-button')));
    await tester.enterText(find.byKey(const ValueKey('auth-phone-field')), _phone);
    await tester.enterText(find.byKey(const ValueKey('auth-password-field')), _pin);
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await _waitFor(tester, find.byType(NavigationBar));
    expect((await const SessionStorage().readSession())['token'], isNotEmpty);

    await tester.tap(find.text('Profile'));
    await _waitFor(tester, find.byKey(const ValueKey('profile-refresh-button')));
    await tester.tap(find.text('Logout'));
    await _waitFor(tester, find.byKey(const ValueKey('auth-submit-button')));
    expect((await const SessionStorage().readSession())['token'], isNull);
    expect(find.byType(NavigationBar), findsNothing);

    await tester.enterText(find.byKey(const ValueKey('auth-phone-field')), _phone);
    await tester.enterText(find.byKey(const ValueKey('auth-password-field')), '999999');
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await _waitFor(tester, find.textContaining('AUTH_INVALID_CREDENTIALS'));
    expect((await const SessionStorage().readSession())['token'], isNull);

    await tester.enterText(find.byKey(const ValueKey('auth-phone-field')), _phone);
    await tester.enterText(find.byKey(const ValueKey('auth-password-field')), _pin);
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await _waitFor(tester, find.byType(NavigationBar));
    await tester.tap(find.text('Profile'));
    await _waitFor(tester, find.text(_phone));
    await tester.tap(find.byKey(const ValueKey('profile-refresh-button')));
    await _waitForRefreshComplete(tester);
    await tester.runAsync(() => Future<void>.delayed(const Duration(seconds: 15)));
  });
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

Future<void> _waitFor(WidgetTester tester, Finder finder) async {
  final deadline = DateTime.now().add(const Duration(seconds: 45));
  while (DateTime.now().isBefore(deadline) && finder.evaluate().isEmpty) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  expect(finder, findsOneWidget);
}
