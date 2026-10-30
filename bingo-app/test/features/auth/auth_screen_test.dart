import 'dart:async';

import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/core/storage/session_storage.dart';
import 'package:bingo_app/src/features/auth/data/auth_api.dart';
import 'package:bingo_app/src/features/auth/data/auth_models.dart';
import 'package:bingo_app/src/features/auth/data/auth_repository.dart';
import 'package:bingo_app/src/features/auth/data/auth_session_controller.dart';
import 'package:bingo_app/src/features/auth/presentation/auth_screen.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

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
        home: AuthScreen(sessionExpired: expired),
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
      await tapKey(t, 'auth-submit-button');
      expect(repo.registrations.single.displayName, 'Bingo player');
      expect(repo.logins, isEmpty);
    },
  );
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
