import 'package:bingo_app/src/app.dart';
import 'package:bingo_app/src/core/storage/session_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _displayName = String.fromEnvironment('BINGO_M03A_NAME');
var _surfaceConverted = false;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Module 03A registered account session restores after process restart', (tester) async {
    await tester.pumpWidget(const BingoApp());
    await _waitFor(tester, find.byType(NavigationBar));
    expect((await const SessionStorage().readSession())['token'], isNotEmpty);

    await tester.tap(find.text('Profile'));
    await tester.pumpAndSettle();
    await _waitFor(tester, find.text(_displayName));
    await _captureScreenshot(tester, 'module-03a-player-process-restart');

    await tester.tap(find.text('Logout'));
    await _waitFor(tester, find.byKey(const ValueKey('auth-submit-button')));
    expect((await const SessionStorage().readSession())['token'], isNull);
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
