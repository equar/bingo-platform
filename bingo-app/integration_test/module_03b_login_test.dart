import 'package:bingo_app/src/app.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _phone = String.fromEnvironment('BINGO_M03B_PHONE');
const _pin = String.fromEnvironment('BINGO_M03B_PIN');
const _name = String.fromEnvironment('BINGO_M03B_NAME');
var _surfaceConverted = false;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Module 03B login validates credentials, normalizes phone and opens session', (tester) async {
    expect(_phone, isNotEmpty);
    expect(_pin, hasLength(6));
    expect(_name, isNotEmpty);

    await tester.pumpWidget(const BingoApp());
    await _waitFor(tester, find.byKey(const ValueKey('auth-submit-button')));
    expect(find.text('Sign in'), findsOneWidget);
    expect(find.text('Use your phone number and six-digit PIN.'), findsOneWidget);
    expect(find.byKey(const ValueKey('auth-phone-field')), findsOneWidget);
    expect(find.byKey(const ValueKey('auth-password-field')), findsOneWidget);
    expect(tester.widget<TextField>(find.byType(TextField).last).obscureText, isTrue);

    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await tester.pumpAndSettle();
    expect(find.text('Enter your phone number.'), findsOneWidget);
    expect(find.text('Enter your 6-digit PIN.'), findsOneWidget);

    await tester.enterText(find.byKey(const ValueKey('auth-phone-field')), '09x11-000-003');
    await tester.enterText(find.byKey(const ValueKey('auth-password-field')), _pin);
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await _waitFor(tester, find.text('Enter a valid phone number.'));
    expect(find.byType(NavigationBar), findsNothing);

    await tester.enterText(find.byKey(const ValueKey('auth-phone-field')), _phone);
    await tester.enterText(find.byKey(const ValueKey('auth-password-field')), '999999');
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await _waitFor(tester, find.textContaining('AUTH_INVALID_CREDENTIALS'));
    expect(find.byType(NavigationBar), findsNothing);
    expect(find.text('Your session expired. Please sign in again.'), findsNothing);

    await tester.enterText(find.byKey(const ValueKey('auth-phone-field')), '+251 930-300-001');
    await tester.enterText(find.byKey(const ValueKey('auth-password-field')), _pin);
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await _waitFor(tester, find.byType(NavigationBar));
    expect(find.text('Your session expired. Please sign in again.'), findsNothing);
    await tester.tap(find.text('Profile'));
    await tester.pumpAndSettle();
    await _waitFor(tester, find.text(_name));
    await _captureScreenshot(tester, 'module-03b-login-success');
    await tester.runAsync(() => Future<void>.delayed(const Duration(seconds: 15)));
  });
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
