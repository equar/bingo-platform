import 'package:bingo_app/src/app.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

class PlayerQaTestHelpers {
  PlayerQaTestHelpers(this.tester);

  final WidgetTester tester;

  Future<void> launch() async {
    await tester.pumpWidget(const BingoApp());
    await tester.pumpAndSettle();
  }

  Future<void> signIn({required String phone, required String pin}) async {
    await enter(const ValueKey('auth-phone-field'), phone);
    await enter(const ValueKey('auth-password-field'), pin);
    await tap(const ValueKey('auth-submit-button'));
    await waitFor(find.byType(NavigationBar));
  }

  Future<void> enter(Key key, String value) async {
    await tester.enterText(find.byKey(key), value);
    await tester.pump();
  }

  Future<void> tap(Key key) async {
    await tester.tap(find.byKey(key));
    await tester.pump();
  }

  Future<void> navigateByLabel(String label) async {
    await tester.tap(find.text(label));
    await tester.pumpAndSettle();
  }

  Future<void> waitFor(Finder finder, {Duration timeout = const Duration(seconds: 15)}) async {
    final deadline = DateTime.now().add(timeout);
    while (DateTime.now().isBefore(deadline)) {
      if (finder.evaluate().isNotEmpty) return;
      await tester.pump(const Duration(milliseconds: 200));
    }
    fail('Timed out waiting for $finder');
  }

  Future<void> expectVisible(Finder finder) async {
    expect(finder, findsOneWidget);
  }

  Future<void> captureScreenshot(String name) async {
    final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();
    await binding.takeScreenshot(name);
  }
}
