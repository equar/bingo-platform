import 'package:bingo_app/src/app.dart';
import 'package:bingo_app/src/core/storage/session_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _phone = String.fromEnvironment('BINGO_M04C2_PHONE');
const _pin = String.fromEnvironment('BINGO_M04C2_PIN');
const _roomId = int.fromEnvironment('BINGO_M04C2_ROOM_ID');
const _sessionId = int.fromEnvironment('BINGO_M04C2_SESSION_ID');
var _surfaceConverted = false;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Module 04C2 player sees live server draws and refreshes history', (tester) async {
    expect(_phone, isNotEmpty);
    expect(_pin, hasLength(6));
    expect(_roomId, greaterThan(0));
    expect(_sessionId, greaterThan(0));

    await const SessionStorage().clear();
    await tester.pumpWidget(const BingoApp());
    await _waitFor(tester, find.byKey(const ValueKey('auth-submit-button')));
    await tester.enterText(find.byKey(const ValueKey('auth-phone-field')), _phone);
    await tester.enterText(find.byKey(const ValueKey('auth-password-field')), _pin);
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await _waitFor(tester, find.byType(NavigationBar));
    await _waitFor(tester, find.byKey(const ValueKey('lobby-rooms-list')));

    final roomFinder = find.byKey(ValueKey('room-details-$_roomId'));
    for (var attempt = 0; roomFinder.evaluate().isEmpty && attempt < 30; attempt++) {
      await tester.drag(find.byKey(const ValueKey('lobby-rooms-list')), const Offset(0, -450));
      await tester.pumpAndSettle();
    }
    expect(roomFinder, findsOneWidget);
    await tester.ensureVisible(roomFinder);
    await tester.tap(roomFinder);
    await _waitFor(tester, find.byKey(const ValueKey('room-detail-name')));

    final liveButton = find.byKey(ValueKey('live-game-$_sessionId'));
    await _waitFor(tester, liveButton);
    await tester.ensureVisible(liveButton);
    await tester.tap(liveButton);
    await _waitFor(tester, find.text('Live game').first);
    await _waitFor(tester, find.textContaining('Session status: DRAWING'));
    await _capture(tester, 'module-04c2-android-live-game-started');

    final sequenceOne = find.descendant(of: find.byType(CircleAvatar), matching: find.text('1'));
    final sequenceTwo = find.descendant(of: find.byType(CircleAvatar), matching: find.text('2'));
    await _waitFor(tester, sequenceOne, timeout: const Duration(minutes: 2));
    await _waitFor(tester, sequenceTwo, timeout: const Duration(minutes: 2));
    await _capture(tester, 'module-04c2-android-live-game-two-draws');

    await tester.tap(find.byTooltip('Refresh'));
    await tester.pumpAndSettle(const Duration(milliseconds: 500));
    await _waitFor(tester, sequenceOne);
    await _waitFor(tester, sequenceTwo);
    final drawCountText = tester.widget<Text>(find.textContaining('Draws:'));
    final drawCount = int.parse(drawCountText.data!.split(':').last.trim());
    expect(drawCount, greaterThanOrEqualTo(2));
    await _capture(tester, 'module-04c2-android-live-game-refreshed');
    await tester.runAsync(() => Future<void>.delayed(const Duration(seconds: 8)));
  });
}

Future<void> _waitFor(WidgetTester tester, Finder finder, {Duration timeout = const Duration(seconds: 60)}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline) && finder.evaluate().isEmpty) {
    await tester.pump(const Duration(milliseconds: 200));
  }
  expect(finder, findsOneWidget, reason: 'Timed out waiting for $finder');
}

Future<void> _capture(WidgetTester tester, String name) async {
  final binding = IntegrationTestWidgetsFlutterBinding.instance;
  if (!_surfaceConverted) {
    await binding.convertFlutterSurfaceToImage();
    _surfaceConverted = true;
  }
  await tester.pump();
  await binding.takeScreenshot(name);
}
