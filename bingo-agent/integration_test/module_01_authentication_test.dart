import 'dart:convert';

import 'package:bingo_agent/src/app.dart';
import 'package:bingo_agent/src/core/config/app_config.dart';
import 'package:bingo_agent/src/core/storage/session_storage.dart';
import 'package:bingo_agent/src/features/auth/data/auth_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:dio/dio.dart';

const _apiUrl = String.fromEnvironment('API_BASE_URL');
const _phone = String.fromEnvironment('BINGO_QA_APPROVED_AGENT_PHONE');
const _pin = String.fromEnvironment('BINGO_QA_APPROVED_AGENT_PIN');
const _pendingPhone = String.fromEnvironment('BINGO_QA_PENDING_AGENT_PHONE');
const _pendingPin = String.fromEnvironment('BINGO_QA_PENDING_AGENT_PIN');
var _surfaceConverted = false;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Module 01 agent authentication on Android', (tester) async {
    expect(_phone, isNotEmpty);
    expect(_pin, isNotEmpty);
    expect(_pendingPhone, isNotEmpty);

    final storage = const SessionStorage();
    await tester.pumpWidget(
      const BingoAgentApp(key: ValueKey('agent-initial')),
    );
    await tester.pumpAndSettle();
    await _waitForEither(
      tester,
      find.byType(TextFormField),
      find.byType(NavigationBar),
    );
    expect(find.byType(NavigationBar), findsNothing);
    final fields = find.byType(TextFormField);
    expect(fields, findsNWidgets(2));
    await _captureScreenshot(tester, 'module-01-agent-login');
    await tester.tap(find.byType(FilledButton));
    await tester.pumpAndSettle();
    expect(find.text('This field is required.'), findsOneWidget);
    expect(find.text('PIN must contain 4 to 8 digits.'), findsOneWidget);

    await tester.pumpWidget(
      BingoAgentApp(
        key: const ValueKey('agent-offline'),
        config: const AppConfig(
          apiBaseUrl: 'http://10.0.2.2:1',
          apiBaseUrlLabel: 'API_BASE_URL',
        ),
      ),
    );
    await tester.pumpAndSettle();
    await _waitFor(tester, find.byType(TextFormField).first);
    await _enterAgentCredentials(
      tester,
      phone: _phone,
      pin: _pin,
    );
    await _waitFor(
      tester,
      find.text('Sign-in failed. Check your details and try again.'),
    );
    expect(find.byType(NavigationBar), findsNothing);
    expect(find.byType(TextFormField), findsNWidgets(2));
    expect(
      find.text('Sign-in failed. Check your details and try again.'),
      findsOneWidget,
    );
    await _captureScreenshot(tester, 'module-01-agent-network-error');

    await tester.pumpWidget(
      const BingoAgentApp(key: ValueKey('agent-recovered')),
    );
    await tester.pumpAndSettle();
    await _waitFor(tester, find.byType(TextFormField).first);
    await _enterAgentCredentials(
      tester,
      phone: 'invalid-phone',
      pin: _pin,
    );
    await _waitFor(
      tester,
      find.text('Sign-in failed. Check your details and try again.'),
    );

    final syntheticPhone =
        '251900${(DateTime.now().millisecondsSinceEpoch % 1000000).toString().padLeft(6, '0')}';
    for (var attempt = 0; attempt < 6; attempt++) {
      await _enterAgentCredentials(
        tester,
        phone: syntheticPhone,
        pin: '1234',
      );
      await _waitFor(
        tester,
        find.text('Sign-in failed. Check your details and try again.'),
      );
    }
    final throttleProbe = Dio(BaseOptions(baseUrl: _apiUrl));
    try {
      await throttleProbe.post<void>(
        '/api/v1/agent/login',
        data: {
          'phoneNumber': syntheticPhone,
          'pin': '1234',
        },
      );
      fail('Expected the synthetic identity to be rate limited');
    } on DioException catch (error) {
      expect(error.response?.statusCode, 429);
    } finally {
      throttleProbe.close();
    }
    expect(
      find.text('Sign-in failed. Check your details and try again.'),
      findsOneWidget,
    );

    await _enterAgentCredentials(
      tester,
      phone: _phone,
      pin: '0000',
    );
    await _waitFor(
      tester,
      find.text('Sign-in failed. Check your details and try again.'),
    );
    expect(find.byType(NavigationBar), findsNothing);

    // Prove the emulator can reach the real QA login service immediately before
    // the widget submits the same phone/PIN pair. This also clears the account
    // throttle after the preceding bad-PIN assertion.
    final loginProbe = Dio(BaseOptions(baseUrl: _apiUrl));
    try {
      final response = await loginProbe.post<Map<String, dynamic>>(
        '/api/v1/agent/login',
        data: {'phoneNumber': _phone, 'pin': _pin},
      );
      expect(response.statusCode, 200);
      expect(response.data?['user']?['roles'], contains('AGENT'));
    } finally {
      loginProbe.close();
    }
    await _enterAgentCredentials(
      tester,
      phone: _phone,
      pin: _pin,
    );
    await _waitForEither(
      tester,
      find.byType(NavigationBar),
      find.text('Sign-in failed. Check your details and try again.'),
    );
    expect(find.byType(NavigationBar), findsOneWidget);
    await _captureScreenshot(tester, 'module-01-agent-authenticated');

    await tester.pumpWidget(
      const BingoAgentApp(key: ValueKey('agent-restored')),
    );
    await tester.pumpAndSettle();
    await _waitFor(tester, find.byType(NavigationBar));
    await tester.tap(find.byTooltip('Logout'));
    await tester.pumpAndSettle();
    expect(find.byType(NavigationBar), findsNothing);
    expect((await storage.readSession())['token'], isNull);

    await _enterAgentCredentials(
      tester,
      phone: _pendingPhone,
      pin: _pendingPin,
    );
    await _waitFor(
      tester,
      find.text('Sign-in failed. Check your details and try again.'),
    );
    expect(find.byType(NavigationBar), findsNothing);
    expect(
      find.text('Sign-in failed. Check your details and try again.'),
      findsOneWidget,
    );
    await _captureScreenshot(tester, 'module-01-agent-pending-rejected');

    final expiry =
        DateTime.now()
            .toUtc()
            .subtract(const Duration(minutes: 1))
            .millisecondsSinceEpoch ~/
        1000;
    final payload = base64Url
        .encode(utf8.encode(jsonEncode({'exp': expiry})))
        .replaceAll('=', '');
    final token = 'e30.$payload.signature';
    final expired = AuthResponse(
      accessToken: token,
      tokenType: 'Bearer',
      expiresInSeconds: 3600,
      user: UserProfile(
        id: 2,
        displayName: 'QA Agent',
        phoneNumber: _phone,
        roles: ['AGENT'],
      ),
    );
    await storage.saveSession(
      accessToken: token,
      sessionJson: jsonEncode(expired.toJson()),
    );
    await tester.pumpWidget(
      const BingoAgentApp(key: ValueKey('agent-expired')),
    );
    await tester.pumpAndSettle();
    await _waitFor(
      tester,
      find.text('Your session expired. Please sign in again.'),
    );
    expect((await storage.readSession())['token'], isNull);
    await _captureScreenshot(tester, 'module-01-agent-expired-session');
    await _enterAgentCredentials(
      tester,
      phone: _phone,
      pin: _pin,
    );
    await _waitFor(tester, find.byType(NavigationBar));
    final storedToken = (await storage.readSession())['token'];
    expect(storedToken, isNotEmpty);
    final displayedText = tester
        .widgetList<Text>(find.byType(Text))
        .map((widget) => widget.data)
        .whereType<String>();
    expect(displayedText.contains(storedToken), isFalse);
    await _captureScreenshot(tester, 'module-01-agent-session-persisted');
  });
}

Future<void> _enterAgentCredentials(
  WidgetTester tester, {
  required String phone,
  required String pin,
}) async {
  final fields = find.byType(TextFormField);
  await tester.enterText(fields.at(0), phone);
  await tester.enterText(fields.at(1), pin);
  await tester.tap(find.byType(FilledButton));
  await tester.pump();
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
