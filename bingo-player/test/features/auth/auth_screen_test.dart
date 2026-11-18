import 'dart:async';
import 'package:bingo_player/src/core/network/api_error.dart';

import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/core/storage/session_storage.dart';
import 'package:bingo_player/src/features/auth/data/auth_api.dart';
import 'package:bingo_player/src/features/auth/data/auth_models.dart';
import 'package:bingo_player/src/features/auth/data/auth_repository.dart';
import 'package:bingo_player/src/features/auth/data/auth_session_controller.dart';
import 'package:bingo_player/src/features/auth/presentation/auth_screen.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

class MemoryStorage extends SessionStorage {
  @override
  Future<Map<String, String?>> readSession() async => {};
  @override
  Future<void> saveSession({
    required String accessToken,
    required String expiresAtIso,
    required String userJson,
  }) async {}
}

class TestAuthRepository extends AuthRepository {
  TestAuthRepository() : super(AuthApi(Dio()));
  final logins = <LoginRequest>[];
  final registrations = <RegisterRequest>[];
  Completer<AuthResponse>? pending;
  AuthResponse get response => AuthResponse.fromJson({
    'accessToken': 'unit-test-only',
    'tokenType': 'Bearer',
    'expiresInSeconds': 3600,
    'user': {
      'id': 1,
      'displayName': 'Player',
      'roles': ['PLAYER'],
      'status': 'ACTIVE',
    },
  });
  @override
  Future<AuthResponse> login(LoginRequest request) async {
    logins.add(request);
    return pending?.future ?? response;
  }

  @override
  Future<AuthResponse> register(RegisterRequest request) async {
    registrations.add(request);
    return response;
  }
}

Future<ProviderContainer> mount(
  WidgetTester t,
  TestAuthRepository repo, {
  bool expired = false,
  bool suspended = false,
  bool narrow = false,
  Locale locale = const Locale('en'),
}) async {
  t.view.physicalSize = narrow ? const Size(320, 640) : const Size(500, 1000);
  t.view.devicePixelRatio = 1;
  addTearDown(t.view.resetPhysicalSize);
  addTearDown(t.view.resetDevicePixelRatio);
  final container = ProviderContainer(
    overrides: [
      authRepositoryProvider.overrideWithValue(repo),
      sessionStorageProvider.overrideWithValue(MemoryStorage()),
    ],
  );
  addTearDown(container.dispose);
  await t.pumpWidget(
    UncontrolledProviderScope(
      container: container,
      child: MaterialApp(
        locale: locale,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: TextScaler.linear(narrow ? 1.5 : 1)),
          child: child!,
        ),
        home: AuthScreen(sessionExpired: expired, accountSuspended: suspended),
      ),
    ),
  );
  await t.pumpAndSettle();
  return container;
}

Future<void> tapKey(WidgetTester t, String key) async {
  final finder = find.byKey(ValueKey(key));
  await t.ensureVisible(finder);
  await t.tap(finder);
  await t.pumpAndSettle();
}

Future<void> fill(WidgetTester t) async {
  await t.enterText(
    find.byKey(const ValueKey('auth-phone-field')),
    '0912345678',
  );
  await t.enterText(
    find.byKey(const ValueKey('auth-password-field')),
    '123456',
  );
}

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  testWidgets('remembers the most recently used sign-in phone', (t) async {
    final repo = TestAuthRepository();
    await mount(t, repo);
    await t.enterText(
      find.byKey(const ValueKey('auth-phone-field')),
      '0912345678',
    );
    await t.enterText(
      find.byKey(const ValueKey('auth-password-field')),
      '123456',
    );
    await tapKey(t, 'auth-submit-button');

    final preferences = await SharedPreferences.getInstance();
    expect(preferences.getString('player.last_login_phone'), '0912345678');
  });

  testWidgets('login and registration copy sits below the hero image', (
    t,
  ) async {
    await mount(t, TestAuthRepository());

    expect(
      t.getSize(find.byKey(const ValueKey('auth-hero-image'))),
      t.getSize(find.byKey(const ValueKey('auth-hero-header'))),
    );
    final heroHeader = find.byKey(const ValueKey('auth-hero-header'));
    final heroCopy = find.byKey(const ValueKey('auth-hero-copy'));
    expect(
      find.descendant(of: heroHeader, matching: find.text('Sign in')),
      findsNothing,
    );
    expect(
      find.descendant(of: heroCopy, matching: find.text('Sign in')),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: heroCopy,
        matching: find.text('Use your phone number and six-digit PIN.'),
      ),
      findsOneWidget,
    );
    expect(
      t.getTopLeft(heroCopy).dy,
      greaterThanOrEqualTo(t.getBottomLeft(heroHeader).dy),
    );

    await tapKey(t, 'auth-toggle-button');
    expect(
      find.descendant(of: heroCopy, matching: find.text('Create account')),
      findsOneWidget,
    );
  });

  testWidgets('language preference is available only after signing in', (
    t,
  ) async {
    await mount(t, TestAuthRepository());

    expect(
      find.byKey(const ValueKey('player-language-selector')),
      findsNothing,
    );

    await tapKey(t, 'auth-toggle-button');
    expect(
      find.byKey(const ValueKey('player-language-selector')),
      findsNothing,
    );
  });

  testWidgets('signed-out suspended users see the support notice', (t) async {
    await mount(t, TestAuthRepository(), suspended: true);
    expect(
      find.text(
        'Your account has been suspended, please reach out to Sunset Bingo support team at 0956047345',
      ),
      findsOneWidget,
    );
    expect(find.byType(AlertDialog), findsOneWidget);
    await t.tap(find.widgetWithText(TextButton, 'OK'));
    await t.pumpAndSettle();
    expect(find.byType(AlertDialog), findsNothing);
    await t.pump();
    expect(
      find.textContaining('Your account has been suspended'),
      findsNothing,
    );
    expect(
      find.text('Your session has expired. Please sign in again.'),
      findsNothing,
    );
  });

  testWidgets('suspended login opens an OK dialog without an inline error', (
    t,
  ) async {
    final repo = TestAuthRepository()..pending = Completer<AuthResponse>();
    await mount(t, repo);
    await fill(t);
    await t.tap(find.byKey(const ValueKey('auth-submit-button')));
    await t.pump();
    repo.pending!.completeError(
      ApiError(code: 'ACCOUNT_SUSPENDED', statusCode: 403),
    );
    await t.pumpAndSettle();
    expect(find.byType(AlertDialog), findsOneWidget);
    expect(
      find.textContaining('Your account has been suspended'),
      findsOneWidget,
    );
    await t.tap(find.widgetWithText(TextButton, 'OK'));
    await t.pumpAndSettle();
    expect(find.byType(AlertDialog), findsNothing);
    expect(
      find.textContaining('Your account has been suspended'),
      findsNothing,
    );
    expect(repo.logins, hasLength(1));
  });

  testWidgets('login retains Ethiopian phone and six-digit PIN validation', (
    t,
  ) async {
    final repo = TestAuthRepository();
    await mount(t, repo);
    await tapKey(t, 'auth-submit-button');
    expect(repo.logins, isEmpty);
    expect(find.text('Enter your phone number.'), findsOneWidget);
    await t.enterText(find.byKey(const ValueKey('auth-phone-field')), '123');
    await t.enterText(find.byKey(const ValueKey('auth-password-field')), '123');
    await tapKey(t, 'auth-submit-button');
    expect(repo.logins, isEmpty);
    expect(find.text('PIN must be exactly 6 digits.'), findsOneWidget);
  });

  testWidgets('login and registration field labels are black', (t) async {
    await mount(t, TestAuthRepository());

    void expectBlackLabel(String key) {
      final field = find.byKey(ValueKey(key));
      final decoration = t
          .widget<InputDecorator>(
            find.descendant(of: field, matching: find.byType(InputDecorator)),
          )
          .decoration;
      expect(decoration.labelStyle?.color, Colors.black);
      expect(decoration.floatingLabelStyle?.color, Colors.black);
    }

    expectBlackLabel('auth-phone-field');
    expectBlackLabel('auth-password-field');

    await tapKey(t, 'auth-toggle-button');
    expectBlackLabel('auth-display-name-field');
    expectBlackLabel('auth-referral-code-field');
    expectBlackLabel('auth-phone-field');
    expectBlackLabel('auth-password-field');
  });

  testWidgets(
    'login submits unchanged credentials and creates the player session',
    (t) async {
      final repo = TestAuthRepository();
      final container = await mount(t, repo);
      await fill(t);
      await tapKey(t, 'auth-submit-button');
      expect(repo.logins.single.phoneNumber, '0912345678');
      expect(repo.logins.single.password, '123456');
      expect(
        container.read(authSessionControllerProvider).isAuthenticated,
        isTrue,
      );
    },
  );
  testWidgets('PIN visibility toggles without changing the entered PIN', (
    t,
  ) async {
    await mount(t, TestAuthRepository());
    await fill(t);
    final f = find.descendant(
      of: find.byKey(const ValueKey('auth-password-field')),
      matching: find.byType(EditableText),
    );
    expect(t.widget<EditableText>(f).obscureText, isTrue);
    await tapKey(t, 'auth-pin-visibility');
    expect(t.widget<EditableText>(f).obscureText, isFalse);
    expect(t.widget<EditableText>(f).controller.text, '123456');
    await tapKey(t, 'auth-pin-visibility');
    expect(t.widget<EditableText>(f).obscureText, isTrue);
  });
  testWidgets(
    'registration toggle keeps the name requirement and correct action',
    (t) async {
      final repo = TestAuthRepository();
      await mount(t, repo);
      await tapKey(t, 'auth-toggle-button');
      await fill(t);
      await tapKey(t, 'auth-submit-button');
      expect(repo.registrations, isEmpty);
      await t.enterText(
        find.byKey(const ValueKey('auth-display-name-field')),
        'Bingo player',
      );
      await t.enterText(
        find.byKey(const ValueKey('auth-referral-code-field')),
        'B12345',
      );
      await tapKey(t, 'auth-submit-button');
      expect(repo.registrations.single.displayName, 'Bingo player');
      expect(repo.registrations.single.referralCode, 'B12345');
      expect(repo.logins, isEmpty);
    },
  );
  testWidgets('registration inputs enforce referral, phone, and PIN limits', (
    t,
  ) async {
    await mount(t, TestAuthRepository());
    await tapKey(t, 'auth-toggle-button');
    await t.enterText(
      find.byKey(const ValueKey('auth-referral-code-field')),
      'AB12CD345',
    );
    await t.enterText(
      find.byKey(const ValueKey('auth-phone-field')),
      '09a123456789',
    );
    await t.enterText(
      find.byKey(const ValueKey('auth-password-field')),
      '12a3456789',
    );

    expect(
      t
          .widget<TextFormField>(
            find.byKey(const ValueKey('auth-referral-code-field')),
          )
          .controller!
          .text,
      'AB12CD',
    );
    expect(
      t
          .widget<TextFormField>(find.byKey(const ValueKey('auth-phone-field')))
          .controller!
          .text,
      '0912345678',
    );
    expect(
      t
          .widget<TextFormField>(
            find.byKey(const ValueKey('auth-password-field')),
          )
          .controller!
          .text,
      '123456',
    );
  });
  testWidgets('pending login disables fields and prevents duplicate submit', (
    t,
  ) async {
    final repo = TestAuthRepository()..pending = Completer<AuthResponse>();
    await mount(t, repo);
    await fill(t);
    await t.ensureVisible(find.byKey(const ValueKey('auth-submit-button')));
    await t.tap(find.byKey(const ValueKey('auth-submit-button')));
    await t.pump();
    expect(
      t
          .widget<FilledButton>(
            find.byKey(const ValueKey('auth-submit-button')),
          )
          .onPressed,
      isNull,
    );
    expect(
      t
          .widget<TextFormField>(find.byKey(const ValueKey('auth-phone-field')))
          .enabled,
      isFalse,
    );
    expect(repo.logins, hasLength(1));
    repo.pending!.complete(repo.response);
    await t.pumpAndSettle();
    expect(t.takeException(), isNull);
  });
  for (final locale in [const Locale('en'), const Locale('am')]) {
    testWidgets(
      'login is scrollable at 320px with large ${locale.languageCode} text',
      (t) async {
        await mount(
          t,
          TestAuthRepository(),
          expired: true,
          narrow: true,
          locale: locale,
        );
        await t.ensureVisible(find.byKey(const ValueKey('auth-toggle-button')));
        await t.pumpAndSettle();
        expect(t.takeException(), isNull);
      },
    );
  }
}
