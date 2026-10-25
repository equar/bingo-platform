import 'package:bingo_app/src/app.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _firstRoomName = 'M02-1792735981360 QA Room';
const _roomName = 'QA M02E Session Room 1792770588090-443bf1';
const _sessionName = 'QA M02E DRAFT 1792770588090-443bf1';
var _surfaceConverted = false;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Module 04A recovers lobby after a real Android network failure',
      (tester) async {
    await tester.pumpWidget(const BingoApp());
    await _waitFor(tester, find.byType(NavigationBar));
    await _waitFor(tester, find.byKey(const ValueKey('lobby-rooms-list')));
    await _waitFor(tester, find.text(_firstRoomName));

    // The host test runner blocks this package after it observes this marker.
    debugPrint('M04AR_LOBBY_READY');
    await tester.runAsync(
        () => Future<void>.delayed(const Duration(seconds: 5)));
    await tester.tap(find.byKey(const ValueKey('lobby-refresh-button')));
    await _waitFor(tester,
        find.text("Couldn't load rooms. Check your connection and retry."));
    await _captureScreenshot(tester, 'module-04a-r-network-error');
    debugPrint('M04AR_OFFLINE_ERROR_CAPTURED');

    // Confirm the first retry enters loading and removes the retry action while
    // its request is pending. The host restores package networking after the
    // second offline failure, then the explicit retry below must recover.
    await tester.tap(find.text('Try again'));
    await tester.pump(const Duration(milliseconds: 250));
    expect(find.text('Loading...'), findsOneWidget);
    expect(find.text('Try again'), findsNothing);
    await _waitFor(tester,
        find.text("Couldn't load rooms. Check your connection and retry."));
    debugPrint('M04AR_RETRY_BLOCKED_CONFIRMED');

    // The host restores the package network and checks backend reachability
    // after this marker. Retry is tapped in Flutter, without restarting app.
    await tester.runAsync(
        () => Future<void>.delayed(const Duration(seconds: 5)));
    await tester.tap(find.text('Try again'));
    await tester.pump(const Duration(milliseconds: 250));
    expect(find.text('Loading...'), findsOneWidget);
    expect(find.text('Try again'), findsNothing);
    await _waitFor(tester, find.byKey(const ValueKey('lobby-rooms-list')));
    await _waitFor(tester, find.text(_firstRoomName));
    expect(
        find.text("Couldn't load rooms. Check your connection and retry."),
        findsNothing);
    expect(find.text('Loading...'), findsNothing);
    debugPrint('M04AR_RETRY_SUCCEEDED');

    final roomFinder = find.byKey(const ValueKey('room-details-6'));
    final lobbyList = find.byKey(const ValueKey('lobby-rooms-list'));
    for (var attempt = 0;
        roomFinder.evaluate().isEmpty && attempt < 20;
        attempt++) {
      await tester.drag(lobbyList, const Offset(0, -400));
      await tester.pumpAndSettle();
    }
    expect(roomFinder, findsOneWidget);
    await tester.ensureVisible(roomFinder);
    await tester.pumpAndSettle();
    await tester.tap(roomFinder);
    await _waitFor(tester, find.text(_roomName));
    await _waitFor(tester, find.text(_sessionName));
    expect(find.textContaining('3.25 ETB'), findsWidgets);
    expect(find.textContaining('10'), findsWidgets);
    expect(find.byKey(const ValueKey('buy-ticket-button')), findsNothing);
    await _captureScreenshot(tester, 'module-04a-r-recovered-room-session');
  });
}

Future<void> _waitFor(WidgetTester tester, Finder finder) async {
  final deadline = DateTime.now().add(const Duration(seconds: 70));
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
