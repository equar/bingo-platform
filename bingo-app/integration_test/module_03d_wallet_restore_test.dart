import 'package:bingo_app/src/app.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

var _surfaceConverted = false;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Module 03D wallet remains zero after app session restoration', (tester) async {
    await tester.pumpWidget(const BingoApp());
    await _waitFor(tester, find.byType(NavigationBar));
    await tester.tap(find.text('Wallet'));
    await _waitFor(tester, find.text('0.0 ETB'));
    await _waitFor(tester, find.textContaining('Available: 0.0 ETB'));
    await _waitFor(tester, find.textContaining('Promotional: 0.0 ETB'));
    expect(find.textContaining('25.0 ETB'), findsNothing);
    await _captureScreenshot(tester, 'module-03d-wallet-zero-after-restart');
    await tester.runAsync(() => Future<void>.delayed(const Duration(seconds: 30)));
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
