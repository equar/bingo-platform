import 'package:bingo_app/src/app.dart';
import 'package:bingo_app/src/core/storage/session_storage.dart';
import 'package:bingo_app/src/core/widgets/section_card.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _phone = String.fromEnvironment('BINGO_E2E_PRIMARY_PHONE');
const _pin = String.fromEnvironment('BINGO_E2E_PRIMARY_PIN');
const _ticketCode = String.fromEnvironment('BINGO_E2E_TICKET_CODE');
const _sessionId = int.fromEnvironment('BINGO_E2E_SESSION_ID');
var _surfaceConverted = false;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('06A-9 real player authentication, lobby and safe gameplay browse', (
    tester,
  ) async {
    expect(
      _phone,
      isNotEmpty,
      reason: 'Secure QA player phone was not supplied.',
    );
    expect(
      _pin,
      hasLength(6),
      reason: 'Secure QA player PIN was not supplied.',
    );
    expect(_ticketCode, startsWith('QA06A9'));
    expect(_sessionId, greaterThan(0));

    // Make the app begin in the real unauthenticated state on this emulator.
    await const SessionStorage().clear();
    await tester.pumpWidget(const BingoApp());
    await _waitFor(tester, find.byKey(const ValueKey('auth-submit-button')));
    expect(find.text('Sign in'), findsOneWidget);
    await _capture(tester, 'module-06a9-login-screen');

    // Client-side required/format validation exercises no server account state.
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await tester.pump();
    expect(find.text('Enter your phone number.'), findsOneWidget);
    expect(find.text('Enter your 6-digit PIN.'), findsOneWidget);
    await tester.enterText(
      find.byKey(const ValueKey('auth-phone-field')),
      '09x11-000-003',
    );
    await tester.enterText(
      find.byKey(const ValueKey('auth-password-field')),
      _pin,
    );
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await tester.pump();
    expect(find.text('Enter a valid phone number.'), findsOneWidget);
    expect(find.byType(NavigationBar), findsNothing);

    // One known-invalid PIN attempt is followed immediately by the valid login;
    // this confirms the server rejects credentials without locking the account.
    await tester.enterText(
      find.byKey(const ValueKey('auth-phone-field')),
      _phone,
    );
    await tester.enterText(
      find.byKey(const ValueKey('auth-password-field')),
      '999999',
    );
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await _waitFor(
      tester,
      find.textContaining('AUTH_INVALID_CREDENTIALS'),
      timeout: const Duration(seconds: 45),
    );
    expect(find.byType(NavigationBar), findsNothing);

    // Valid credentials authenticate through the actual Android app and API.
    await tester.enterText(
      find.byKey(const ValueKey('auth-phone-field')),
      _phone,
    );
    await tester.enterText(
      find.byKey(const ValueKey('auth-password-field')),
      _pin,
    );
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await _waitFor(
      tester,
      find.byType(NavigationBar),
      timeout: const Duration(seconds: 60),
    );
    await _waitFor(tester, find.byKey(const ValueKey('lobby-rooms-list')));
    expect(find.text('Rooms'), findsOneWidget);
    await _capture(tester, 'module-06a9-lobby-loaded');

    await tester.tap(find.byKey(const ValueKey('lobby-refresh-button')));
    await _waitFor(tester, find.byKey(const ValueKey('lobby-rooms-list')));

    // Explore rooms read-only. An OPEN session is optional; absence is reported
    // as BLOCKED BY DATA rather than changing retained session state.
    final roomButtons = find.byWidgetPredicate((widget) {
      final key = widget.key;
      return key is ValueKey<String> && key.value.startsWith('room-details-');
    });
    expect(
      roomButtons,
      findsWidgets,
      reason: 'No existing QA rooms are available.',
    );
    var openedRoom = false;
    var openedSession = false;
    final roomCount = roomButtons.evaluate().length;
    for (
      var roomIndex = 0;
      roomIndex < roomCount && !openedSession;
      roomIndex++
    ) {
      final button = roomButtons.at(roomIndex);
      final roomKey = (tester.widget(button)).key as ValueKey<String>;
      final roomId = roomKey.value.substring('room-details-'.length);
      await tester.ensureVisible(button);
      await tester.tap(button);
      await _waitFor(tester, find.byKey(const ValueKey('room-detail-name')));
      openedRoom = true;

      final roomPriceFinder = find.textContaining('Price:').first;
      await _waitFor(tester, roomPriceFinder);
      final roomPriceLine =
          (roomPriceFinder.evaluate().single.widget as Text).data!;
      // ignore: avoid_print
      print('E2E_UI_ROOM|id=$roomId|$roomPriceLine');

      final expectedLiveButton = find.byKey(ValueKey('live-game-$_sessionId'));
      final openStatus = find.text('Status: OPEN');
      if (expectedLiveButton.evaluate().isNotEmpty &&
          openStatus.evaluate().isNotEmpty) {
        final sessionCard = find
            .ancestor(
              of: expectedLiveButton,
              matching: find.byType(SectionCard),
            )
            .first;
        expect(
          find.descendant(of: sessionCard, matching: find.text('Status: OPEN')),
          findsOneWidget,
        );
        expect(
          find.descendant(
            of: sessionCard,
            matching: find.textContaining('Price:'),
          ),
          findsOneWidget,
        );
        final liveButton = find.descendant(
          of: sessionCard,
          matching: find.byKey(ValueKey('live-game-$_sessionId')),
        );
        expect(liveButton, findsOneWidget);
        final sessionId = '$_sessionId';
        final sessionPriceFinder = find
            .descendant(
              of: sessionCard,
              matching: find.textContaining('Price:'),
            )
            .first;
        final sessionPriceLine =
            (sessionPriceFinder.evaluate().single.widget as Text).data!;
        // ignore: avoid_print
        print(
          'E2E_UI_OPEN_SESSION|id=$sessionId|status=OPEN|$sessionPriceLine',
        );
        await tester.ensureVisible(liveButton);
        await tester.tap(liveButton);
        await _waitFor(tester, find.text('OPEN'));
        final purchaseButton = tester.widget<FilledButton>(
          find.byKey(const ValueKey('buy-ticket-button')),
        );
        expect(purchaseButton.onPressed, isNull);
        expect(find.text(_ticketCode), findsWidgets);
        expect(
          find
              .byWidgetPredicate((widget) {
                final key = widget.key;
                return key is ValueKey<String> &&
                    key.value.startsWith('ticket-cell-');
              })
              .evaluate()
              .length,
          25,
        );
        expect(
          find.text(
            'Purchase is disabled in this QA preview. No ticket will be created and no wallet will be charged.',
          ),
          findsOneWidget,
        );
        await _capture(tester, 'module-06a9-open-session-purchase-disabled');
        await tester.pageBack();
        await _waitFor(tester, find.byKey(const ValueKey('room-detail-name')));
        final sameSession = find.descendant(
          of: sessionCard,
          matching: find.byKey(ValueKey('live-game-$_sessionId')),
        );
        await tester.ensureVisible(sameSession);
        await tester.tap(sameSession);
        await _waitFor(tester, find.text('OPEN'));
        await tester.pageBack();
        await _waitFor(tester, find.byKey(const ValueKey('room-detail-name')));
        openedSession = true;
      }
      if (!openedSession) {
        await tester.pageBack();
        await _waitFor(tester, find.byKey(const ValueKey('lobby-rooms-list')));
      }
    }
    if (!openedSession) {
      // This is a truthful soft blocker: the UI may still have verified rooms,
      // but no existing OPEN session was available for the detail journey.
      // ignore: avoid_print
      print('BLOCKED_BY_DATA: no existing OPEN session was displayed.');
    }
    if (openedSession) {
      await tester.pageBack();
      await _waitFor(tester, find.byKey(const ValueKey('lobby-rooms-list')));
    }
    if (!openedRoom) {
      // ignore: avoid_print
      print('BLOCKED_BY_DATA: no room detail could be opened.');
    }

    // Logout is local and must return the protected area to the auth screen.
    await tester.tap(find.text('Profile'));
    await _waitFor(tester, find.text('Logout'));
    await tester.tap(find.text('Logout'));
    await _waitFor(tester, find.byKey(const ValueKey('auth-submit-button')));
    expect(find.byType(NavigationBar), findsNothing);
    expect(find.byKey(const ValueKey('lobby-rooms-list')), findsNothing);
    await _capture(tester, 'module-06a9-logout-auth-required');
  });
}

Future<void> _waitFor(
  WidgetTester tester,
  Finder finder, {
  Duration timeout = const Duration(seconds: 30),
}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline) && finder.evaluate().isEmpty) {
    await tester.pump(const Duration(milliseconds: 150));
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
