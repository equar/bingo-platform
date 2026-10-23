import 'package:bingo_app/src/app.dart';
import 'package:bingo_app/src/core/config/app_config.dart';
import 'package:bingo_app/src/core/storage/session_storage.dart';
import 'package:bingo_app/src/features/auth/data/auth_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _apiUrl = String.fromEnvironment('API_BASE_URL');
const _phone = String.fromEnvironment('BINGO_QA_PLAYER_001_PHONE');
const _pin = String.fromEnvironment('BINGO_QA_PLAYER_001_PIN');
var _surfaceConverted = false;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Module 01 player authentication on Android', (tester) async {
    expect(_apiUrl, isNotEmpty);
    expect(_phone, isNotEmpty);
    expect(_pin.length, 6);

    final storage = const SessionStorage();
    await tester.pumpWidget(const BingoApp(key: ValueKey('player-initial')));
    await tester.pumpAndSettle();
    await _waitForEither(
      tester,
      find.byKey(const ValueKey('auth-submit-button')),
      find.byType(NavigationBar),
    );
    if (find.byType(NavigationBar).evaluate().isNotEmpty) {
      await tester.tap(find.text('Profile'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Logout'));
      await tester.pumpAndSettle();
    }
    await _waitFor(tester, find.byKey(const ValueKey('auth-submit-button')));
    expect(find.byKey(const ValueKey('auth-submit-button')), findsOneWidget);
    await _captureScreenshot(tester, 'module-01-player-login');

    await tester.tap(find.byKey(const ValueKey('auth-toggle-button')));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('auth-display-name-field')),
      findsOneWidget,
    );
    await tester.tap(find.byKey(const ValueKey('auth-toggle-button')));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await tester.pumpAndSettle();
    expect(find.text('Enter your phone number.'), findsOneWidget);
    expect(find.text('Enter your 6-digit PIN.'), findsOneWidget);

    await tester.enterText(
      find.byKey(const ValueKey('auth-phone-field')),
      _phone,
    );
    await tester.enterText(
      find.byKey(const ValueKey('auth-password-field')),
      '12345',
    );
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await tester.pumpAndSettle();
    expect(find.text('PIN must be exactly 6 digits.'), findsOneWidget);

    await tester.enterText(
      find.byKey(const ValueKey('auth-phone-field')),
      'invalid-phone',
    );
    await tester.enterText(
      find.byKey(const ValueKey('auth-password-field')),
      '123456',
    );
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await tester.pumpAndSettle();
    expect(find.text('Enter a valid phone number.'), findsOneWidget);

    await tester.pumpWidget(
      BingoApp(
        key: const ValueKey('player-offline'),
        config: const AppConfig(
          apiBaseUrl: 'http://10.0.2.2:1',
          apiBaseUrlLabel: 'API_BASE_URL',
        ),
      ),
    );
    await tester.pumpAndSettle();
    await _waitFor(tester, find.byKey(const ValueKey('auth-submit-button')));
    await tester.enterText(
      find.byKey(const ValueKey('auth-phone-field')),
      _phone,
    );
    await tester.enterText(
      find.byKey(const ValueKey('auth-password-field')),
      _pin,
    );
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await _waitFor(tester, find.textContaining('HTTP_'));
    await _captureScreenshot(tester, 'module-01-player-network-error');

    await tester.pumpWidget(const BingoApp(key: ValueKey('player-recovered')));
    await tester.pumpAndSettle();
    await _waitFor(tester, find.byKey(const ValueKey('auth-submit-button')));
    await tester.enterText(
      find.byKey(const ValueKey('auth-phone-field')),
      '+251 900 000 004',
    );
    await tester.enterText(
      find.byKey(const ValueKey('auth-password-field')),
      '000000',
    );
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await _waitFor(tester, find.textContaining('AUTH_'));
    await tester.enterText(
      find.byKey(const ValueKey('auth-password-field')),
      _pin,
    );
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await _waitFor(tester, find.byType(NavigationBar));
    await _captureScreenshot(tester, 'module-01-player-authenticated');

    // Rebuild the app root to verify session restoration from secure storage.
    await tester.pumpWidget(const BingoApp(key: ValueKey('player-restored')));
    await tester.pumpAndSettle();
    await _waitFor(tester, find.byType(NavigationBar));
    await tester.tap(find.text('Profile'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Logout'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('auth-submit-button')), findsOneWidget);
    expect((await storage.readSession())['token'], isNull);

    final expiredUser = UserProfile(
      id: 4,
      displayName: 'QA Player',
      phoneNumber: '251900000004',
      roles: const ['PLAYER'],
      status: 'ACTIVE',
      createdAt: DateTime.utc(2026),
      updatedAt: DateTime.utc(2026),
    );
    await storage.saveSession(
      accessToken: 'expired-token',
      expiresAtIso: DateTime.now()
          .toUtc()
          .subtract(const Duration(minutes: 1))
          .toIso8601String(),
      userJson: expiredUser.toPersistedJson(),
    );
    await tester.pumpWidget(const BingoApp(key: ValueKey('player-expired')));
    await tester.pumpAndSettle();
    await _waitFor(
      tester,
      find.text('Your session expired. Please sign in again.'),
    );
    expect((await storage.readSession())['token'], isNull);
    await _captureScreenshot(tester, 'module-01-player-expired-session');

    await storage.saveSession(
      accessToken: 'invalid-token',
      expiresAtIso: DateTime.now()
          .toUtc()
          .add(const Duration(minutes: 10))
          .toIso8601String(),
      userJson: expiredUser.toPersistedJson(),
    );
    await tester.pumpWidget(
      const BingoApp(key: ValueKey('player-invalid-token')),
    );
    await tester.pumpAndSettle();
    await _waitFor(
      tester,
      find.text('Your session expired. Please sign in again.'),
    );
    expect((await storage.readSession())['token'], isNull);
    await _captureScreenshot(tester, 'module-01-player-invalid-token');

    final syntheticPhone =
        '251900${(DateTime.now().millisecondsSinceEpoch % 1000000).toString().padLeft(6, '0')}';
    for (var attempt = 0; attempt < 6; attempt++) {
      await tester.enterText(
        find.byKey(const ValueKey('auth-phone-field')),
        syntheticPhone,
      );
      await tester.enterText(
        find.byKey(const ValueKey('auth-password-field')),
        '000000',
      );
      await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
      await _waitFor(tester, find.textContaining('AUTH_'));
      if (attempt == 5) {
        expect(find.textContaining('AUTH_RATE_LIMITED'), findsOneWidget);
      }
    }
    expect(find.textContaining('AUTH_RATE_LIMITED'), findsOneWidget);
    await _captureScreenshot(tester, 'module-01-player-rate-limited');
    await tester.enterText(
      find.byKey(const ValueKey('auth-phone-field')),
      _phone,
    );
    await tester.enterText(
      find.byKey(const ValueKey('auth-password-field')),
      _pin,
    );
    await tester.tap(find.byKey(const ValueKey('auth-submit-button')));
    await _waitFor(tester, find.byType(NavigationBar));
    final storedToken = (await storage.readSession())['token'];
    expect(storedToken, isNotEmpty);
    final displayedText = tester
        .widgetList<Text>(find.byType(Text))
        .map((widget) => widget.data)
        .whereType<String>();
    expect(displayedText.contains(storedToken), isFalse);
    await _captureScreenshot(tester, 'module-01-player-session-persisted');
  });
}

Future<void> _waitFor(WidgetTester tester, Finder finder) async {
  await tester.pump(const Duration(milliseconds: 100));
  final deadline = DateTime.now().add(const Duration(seconds: 15));
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

Future<void> _waitForEither(
  WidgetTester tester,
  Finder first,
  Finder second,
) async {
  final deadline = DateTime.now().add(const Duration(seconds: 15));
  while (DateTime.now().isBefore(deadline) &&
      first.evaluate().isEmpty &&
      second.evaluate().isEmpty) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  expect(first.evaluate().isNotEmpty || second.evaluate().isNotEmpty, isTrue);
}
