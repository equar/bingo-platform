import 'package:bingo_app/src/app.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _name = String.fromEnvironment('BINGO_M03B_NAME');
var _surfaceConverted = false;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'Module 03C restored session displays server profile after app relaunch',
    (tester) async {
      await tester.pumpWidget(const BingoApp());
      await _waitFor(tester, find.byType(NavigationBar));
      await tester.tap(find.text('Profile'));
      await _waitFor(tester, find.text(_name));
      await _waitFor(tester, find.text('251930300001'));
      await _waitForRefreshComplete(tester);
      expect(find.text('PLAYER'), findsOneWidget);
      await _captureScreenshot(tester, 'module-03c-profile-restored');
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
