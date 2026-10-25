import 'package:bingo_app/src/app.dart';
import 'package:bingo_app/src/core/storage/session_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _phone = String.fromEnvironment('BINGO_M03E_PHONE');
const _pin = String.fromEnvironment('BINGO_M03E_PIN');
const _roomId = int.fromEnvironment('BINGO_M04A_ROOM_ID', defaultValue: 6);
var _surfaceConverted = false;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Module 04A player browses room and sessions without purchase',
      (tester) async {
    expect(_phone, isNotEmpty);
    expect(_pin, hasLength(6));
    await const SessionStorage().clear();
    await tester.pumpWidget(const BingoApp());
    await _waitFor(tester, find.byKey(const ValueKey('auth-submit-button')));
    await tester.enterText(find.byKey(const ValueKey('auth-phone-field')), _phone);
    await tester.enterText(find.byKey(const ValueKey('auth-password-field')), _pin);
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await _waitFor(tester, find.byType(NavigationBar));

    final roomFinder = find.byKey(ValueKey('room-details-$_roomId'));
    final lobbyList = find.byKey(const ValueKey('lobby-rooms-list'));
    await _waitFor(tester, lobbyList);
    for (var attempt = 0; roomFinder.evaluate().isEmpty && attempt < 20; attempt++) {
      await tester.drag(lobbyList, const Offset(0, -400));
      await tester.pumpAndSettle();
    }
    expect(roomFinder, findsOneWidget);
    await tester.ensureVisible(roomFinder);
    await tester.pumpAndSettle();
    await tester.tap(roomFinder);
    await _waitFor(tester, find.byKey(const ValueKey('room-detail-name')));
    await _waitFor(tester, find.textContaining('QA M02E DRAFT'));
    expect(find.textContaining('ETB'), findsWidgets);
    expect(find.byKey(const ValueKey('buy-ticket-button')), findsNothing);
    await _captureScreenshot(tester, 'module-04a-room-session-details');

    await tester.drag(find.byType(ListView).last, const Offset(0, 500));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('room-detail-name')), findsOneWidget);
    await tester.runAsync(() => Future<void>.delayed(const Duration(seconds: 15)));
  });
}

Future<void> _waitFor(WidgetTester tester, Finder finder) async {
  final deadline = DateTime.now().add(const Duration(seconds: 60));
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
