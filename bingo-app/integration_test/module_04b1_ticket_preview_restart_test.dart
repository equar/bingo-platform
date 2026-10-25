import 'package:bingo_app/src/app.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _roomId = int.fromEnvironment('BINGO_M04A_ROOM_ID', defaultValue: 6);
const _phone = String.fromEnvironment('BINGO_M03E_PHONE');
const _pin = String.fromEnvironment('BINGO_M03E_PIN');

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'Module 04B-1 app relaunch reloads preview without local card state',
    (tester) async {
      await tester.pumpWidget(const BingoApp());
    await _waitForEither(
      tester,
      find.byType(NavigationBar),
      find.byKey(const ValueKey('auth-submit-button')),
    );
    final sessionRestored = find.byType(NavigationBar).evaluate().isNotEmpty;
    debugPrint('SESSION_RESTORED_AFTER_FORCE_STOP=$sessionRestored');
    if (!sessionRestored) {
        await _waitFor(
          tester,
          find.byKey(const ValueKey('auth-submit-button')),
        );
        await tester.enterText(
          find.byKey(const ValueKey('auth-phone-field')),
          _phone,
        );
        await tester.enterText(
          find.byKey(const ValueKey('auth-password-field')),
          _pin,
        );
        await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
      }
      await _waitFor(tester, find.byType(NavigationBar));

      final roomFinder = find.byKey(ValueKey('room-details-$_roomId'));
      final lobbyList = find.byKey(const ValueKey('lobby-rooms-list'));
      await _waitFor(tester, lobbyList);
      for (
        var attempt = 0;
        roomFinder.evaluate().isEmpty && attempt < 20;
        attempt++
      ) {
        await tester.drag(lobbyList, const Offset(0, -400));
        await tester.pumpAndSettle();
      }
      expect(roomFinder, findsOneWidget);
      await tester.ensureVisible(roomFinder);
      await tester.pumpAndSettle();
      await tester.tap(roomFinder);
      await _waitFor(tester, find.byKey(const ValueKey('room-detail-name')));

      final previewButton = find.byWidgetPredicate(
        (widget) =>
            widget.key is ValueKey<String> &&
            (widget.key! as ValueKey<String>).value.startsWith(
              'ticket-preview-',
            ),
      );
      await _waitFor(tester, previewButton.first);
      await tester.ensureVisible(previewButton.first);
      await tester.pumpAndSettle();
      await tester.tap(previewButton.first);
      await _waitFor(
        tester,
        find.byKey(const ValueKey('ticket-preview-price')),
      );
      expect(find.textContaining('ETB'), findsWidgets);
      final purchase = tester.widget<FilledButton>(
        find.byKey(const ValueKey('ticket-preview-purchase-disabled')),
      );
      expect(purchase.onPressed, isNull);
    },
  );
}

Future<void> _waitFor(WidgetTester tester, Finder finder) async {
  final deadline = DateTime.now().add(const Duration(seconds: 60));
  while (DateTime.now().isBefore(deadline) && finder.evaluate().isEmpty) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  expect(finder, findsOneWidget);
}

Future<void> _waitForEither(
  WidgetTester tester,
  Finder first,
  Finder second,
) async {
  final deadline = DateTime.now().add(const Duration(seconds: 60));
  while (DateTime.now().isBefore(deadline) &&
      first.evaluate().isEmpty &&
      second.evaluate().isEmpty) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  expect(first.evaluate().isNotEmpty || second.evaluate().isNotEmpty, isTrue);
}
