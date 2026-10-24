import 'package:bingo_app/src/app.dart';
import 'package:bingo_app/src/core/storage/session_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _displayName = String.fromEnvironment('BINGO_M03E_NAME');
const _phone = String.fromEnvironment('BINGO_M03E_PHONE');
var _surfaceConverted = false;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Module 03E restores the registered player after process restart', (tester) async {
    expect(_displayName, isNotEmpty);
    expect(_phone, isNotEmpty);
    await tester.pumpWidget(const BingoApp());
    await _waitFor(tester, find.byType(NavigationBar));
    await tester.tap(find.text('Profile'));
    await _waitFor(tester, find.text(_displayName));
    await _waitFor(tester, find.text(_phone));
    expect(find.text('PLAYER'), findsOneWidget);
    await _captureScreenshot(tester, 'module-03e-session-restored-profile');
    await tester.tap(find.text('Logout'));
    await _waitFor(tester, find.byKey(const ValueKey('auth-submit-button')));
    expect(find.byType(NavigationBar), findsNothing);
    expect(find.text(_displayName), findsNothing);
    expect((await const SessionStorage().readSession())['token'], isNull);
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

Future<void> _captureScreenshot(WidgetTester tester, String name) async {
  final binding = IntegrationTestWidgetsFlutterBinding.instance;
  if (!_surfaceConverted) {
    await binding.convertFlutterSurfaceToImage();
    _surfaceConverted = true;
  }
  await tester.pump();
  await binding.takeScreenshot(name);
}
