import 'package:bingo_app/src/app.dart';
import 'package:bingo_app/src/core/storage/session_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _scenario = String.fromEnvironment('M04C3B_SCENARIO');
const _phone = String.fromEnvironment('M04C3B_PLAYER_PHONE');
const _pin = String.fromEnvironment('M04C3B_PLAYER_PIN');
const _roomId = int.fromEnvironment('M04C3B_ROOM_ID');
const _sessionId = int.fromEnvironment('M04C3B_SESSION_ID');
const _expectedDrawCount = int.fromEnvironment('M04C3B_EXPECTED_DRAW_COUNT');
const _terminalState = String.fromEnvironment('M04C3B_TERMINAL_STATE');
var _surfaceConverted = false;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Module 04C3B live game recovery: $_scenario', (tester) async {
    expect(_phone, isNotEmpty);
    expect(_pin, hasLength(6));
    expect(_roomId, greaterThan(0));
    expect(_sessionId, greaterThan(0));
    expect(_expectedDrawCount, greaterThan(0));

    if (_scenario != 'restart-restore') {
      await const SessionStorage().clear();
    }
    await tester.pumpWidget(const BingoApp());

    if (_scenario == 'restart-restore') {
      await _waitFor(tester, find.byType(NavigationBar));
      expect(find.byKey(const ValueKey('auth-submit-button')), findsNothing);
      expect((await const SessionStorage().readSession())['token'], isNotEmpty);
    } else {
      await _login(tester);
    }

    await _openLiveSession(tester);
    await _waitFor(tester, find.textContaining('Session status: DRAWING'));
    await _assertHistory(tester, 'DRAWING');
    expect(find.byKey(const ValueKey('buy-ticket-button')), findsNothing);

    switch (_scenario) {
      case 'restart-prepare':
        debugPrint('M04C3B_RESTART_PREPARED session=$_sessionId draws=$_expectedDrawCount');
        await _capture(tester, 'module-04c3b-restart-before');
        break;
      case 'restart-restore':
        debugPrint('M04C3B_RESTART_RESTORED session=$_sessionId draws=$_expectedDrawCount');
        await _capture(tester, 'module-04c3b-restart-after');
        break;
      case 'network':
        await _exerciseNetworkRecovery(tester);
        break;
      case 'terminal':
        await _exerciseTerminalRefresh(tester);
        break;
      default:
        fail('Unsupported scenario: $_scenario');
    }
  });
}

Future<void> _login(WidgetTester tester) async {
  await _waitFor(tester, find.byKey(const ValueKey('auth-submit-button')));
  await tester.enterText(find.byKey(const ValueKey('auth-phone-field')), _phone);
  await tester.enterText(find.byKey(const ValueKey('auth-password-field')), _pin);
  await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
  await _waitFor(tester, find.byType(NavigationBar));
  await _waitFor(tester, find.byKey(const ValueKey('lobby-rooms-list')));
}

Future<void> _openLiveSession(WidgetTester tester) async {
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
  await tester.dragUntilVisible(
    liveButton,
    find.byType(Scrollable).last,
    const Offset(0, -350),
  );
  await tester.pumpAndSettle();
  await tester.tap(liveButton);
  await _waitFor(tester, find.text('Live game').first);
}

Future<void> _assertHistory(WidgetTester tester, String state) async {
  await _waitFor(tester, find.textContaining('Session status: $state'));
  final countFinder = find.textContaining('Draws:');
  await _waitFor(tester, countFinder);
  final countText = tester.widget<Text>(countFinder.first).data!;
  expect(int.parse(countText.split(':').last.trim()), _expectedDrawCount);
  for (var sequence = 1; sequence <= _expectedDrawCount; sequence++) {
    final sequenceFinder = find.descendant(
      of: find.byType(CircleAvatar),
      matching: find.text(sequence.toString()),
    );
    await _waitFor(tester, sequenceFinder);
  }
}

Future<void> _exerciseNetworkRecovery(WidgetTester tester) async {
  debugPrint('M04C3B_NETWORK_LIVE_READY session=$_sessionId');
  await tester.runAsync(() => Future<void>.delayed(const Duration(seconds: 12)));

  await tester.tap(find.byTooltip('Refresh'));
  await tester.pump(const Duration(milliseconds: 300));
  await _waitFor(tester, find.text('Reconnecting...'), timeout: const Duration(seconds: 20));
  final transportError = find.byWidgetPredicate((widget) {
    if (widget is! Text || widget.data == null) return false;
    final text = widget.data!;
    return text.contains('DioException') ||
        text.contains('SocketException') ||
        text.contains('Connection refused') ||
        text.contains('Failed host lookup') ||
        text.contains('connection error');
  });
  await _waitFor(tester, transportError, timeout: const Duration(seconds: 20));
  await _assertHistory(tester, 'DRAWING');
  debugPrint('M04C3B_OFFLINE_ERROR_CAPTURED session=$_sessionId draws=$_expectedDrawCount');
  await tester.runAsync(() => Future<void>.delayed(const Duration(seconds: 15)));

  // The host restores emulator connectivity during this pause. Refresh is invoked
  // in the same process after that independent reachability check.
  await tester.runAsync(() => Future<void>.delayed(const Duration(seconds: 8)));
  await tester.tap(find.byTooltip('Refresh'));
  await tester.pump(const Duration(milliseconds: 300));
  await _waitFor(tester, find.text('Live'), timeout: const Duration(seconds: 30));
  expect(transportError, findsNothing);
  await _assertHistory(tester, 'DRAWING');
  debugPrint('M04C3B_NETWORK_RECOVERED session=$_sessionId draws=$_expectedDrawCount');
  await _capture(tester, 'module-04c3b-network-recovered');
}

Future<void> _exerciseTerminalRefresh(WidgetTester tester) async {
  expect(const {'CLOSED', 'CANCELLED'}, contains(_terminalState));
  debugPrint('M04C3B_TERMINAL_READY session=$_sessionId state=$_terminalState');
  await tester.runAsync(() => Future<void>.delayed(const Duration(seconds: 15)));
  await tester.tap(find.byTooltip('Refresh'));
  await tester.pump(const Duration(milliseconds: 300));
  await _waitFor(tester, find.textContaining('Session status: $_terminalState'), timeout: const Duration(seconds: 30));
  await _waitFor(tester, find.text('Finished').first);
  await _assertHistory(tester, _terminalState);
  debugPrint('M04C3B_TERMINAL_REFRESHED session=$_sessionId state=$_terminalState draws=$_expectedDrawCount');
  await _capture(tester, 'module-04c3b-terminal-${_terminalState.toLowerCase()}');
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
