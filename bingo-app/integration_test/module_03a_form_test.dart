import 'package:bingo_app/src/app.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

var _surfaceConverted = false;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Module 03A registration form shows required fields and errors', (tester) async {
    await tester.pumpWidget(const BingoApp());
    await _waitFor(tester, find.byKey(const ValueKey('auth-submit-button')));
    await tester.tap(find.byKey(const ValueKey('auth-toggle-button')));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('auth-display-name-field')), findsOneWidget);
    expect(find.byKey(const ValueKey('auth-phone-field')), findsOneWidget);
    expect(find.byKey(const ValueKey('auth-password-field')), findsOneWidget);
    await _captureScreenshot(tester, 'module-03a-registration-form');

    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await tester.pumpAndSettle();
    expect(find.text('Enter a display name.'), findsOneWidget);
    expect(find.text('Enter your phone number.'), findsOneWidget);
    expect(find.text('Enter your 6-digit PIN.'), findsOneWidget);
    await _captureScreenshot(tester, 'module-03a-required-fields');
  });
}

Future<void> _waitFor(WidgetTester tester, Finder finder) async {
  final deadline = DateTime.now().add(const Duration(seconds: 30));
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
