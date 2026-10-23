import 'package:bingo_agent/src/app.dart';
import 'package:bingo_agent/src/core/storage/session_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _phase = String.fromEnvironment('BINGO_M02A_PHASE');
const _onePhone = String.fromEnvironment('BINGO_M02A_AGENT_ONE_PHONE');
const _onePassword = String.fromEnvironment('BINGO_M02A_AGENT_ONE_PASSWORD');
const _onePin = String.fromEnvironment('BINGO_M02A_AGENT_ONE_PIN');
const _twoPhone = String.fromEnvironment('BINGO_M02A_AGENT_TWO_PHONE');
const _twoPassword = String.fromEnvironment('BINGO_M02A_AGENT_TWO_PASSWORD');
const _twoPin = String.fromEnvironment('BINGO_M02A_AGENT_TWO_PIN');
var _surfaceConverted = false;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Module 02A agent onboarding and approved/suspended login', (
    tester,
  ) async {
    expect(
      _phase,
      anyOf('apply', 'approved-login', 'approved-login-two', 'suspended-login'),
    );
    if (_phase != 'apply') {
      await const SessionStorage().clear();
    }
    await tester.pumpWidget(const BingoAgentApp());
    await tester.pumpAndSettle();
    await _waitFor(tester, find.byType(TextFormField).first);

    if (_phase == 'apply') {
      await tester.tap(find.text('Apply to become an agent'));
      await tester.pumpAndSettle();
      await _apply(
        tester,
        phone: _onePhone,
        displayName: 'QA M02A Agent One',
        documentNumber: 'M02A-AGENT-001',
        password: _onePassword,
        pin: _onePin,
      );
      await _waitFor(
        tester,
        find.byKey(const ValueKey('agent-application-submitted')),
      );
      expect((await const SessionStorage().readSession())['token'], isNull);
      await _capture(tester, 'module-02a-agent-application-one-submitted');
      await tester.tap(find.text('Back to sign in'));
      await tester.pumpAndSettle();
      await _login(
        tester,
        phone: _onePhone,
        password: _onePassword,
        pin: _onePin,
      );
      await _waitFor(
        tester,
        find.text('Sign-in failed. Check your details and try again.'),
      );
      expect(find.byType(NavigationBar), findsNothing);
      expect((await const SessionStorage().readSession())['token'], isNull);
      await _capture(tester, 'module-02a-agent-pending-login-denied');
      await tester.tap(find.text('Apply to become an agent'));
      await tester.pumpAndSettle();
      await _apply(
        tester,
        phone: _twoPhone,
        displayName: 'QA M02A Agent Two',
        documentNumber: 'M02A-AGENT-002',
        password: _twoPassword,
        pin: _twoPin,
      );
      await _waitFor(
        tester,
        find.byKey(const ValueKey('agent-application-submitted')),
      );
      expect((await const SessionStorage().readSession())['token'], isNull);
      await _capture(tester, 'module-02a-agent-application-two-submitted');
      return;
    }

    final isFirst = _phase == 'approved-login';
    final phone = isFirst ? _onePhone : _twoPhone;
    final password = isFirst ? _onePassword : _twoPassword;
    final pin = isFirst ? _onePin : _twoPin;
    await _login(tester, phone: phone, password: password, pin: pin);
    if (_phase == 'approved-login' || _phase == 'approved-login-two') {
      await _waitFor(tester, find.byType(NavigationBar));
      await _waitFor(tester, find.text('Available float'));
      expect(
        find.text('Request failed. Check your connection and try again.'),
        findsNothing,
      );
      expect((await const SessionStorage().readSession())['token'], isNotEmpty);
      await _capture(tester, 'module-02a-agent-approved-login');
    } else {
      await _waitFor(
        tester,
        find.text('Sign-in failed. Check your details and try again.'),
      );
      expect(find.byType(NavigationBar), findsNothing);
      expect((await const SessionStorage().readSession())['token'], isNull);
      await _capture(tester, 'module-02a-agent-suspended-login-denied');
    }
  });
}

Future<void> _apply(
  WidgetTester tester, {
  required String phone,
  required String displayName,
  required String documentNumber,
  required String password,
  required String pin,
}) async {
  final fields = find.byType(TextFormField);
  expect(fields, findsNWidgets(9));
  final input = <String>[
    displayName,
    phone,
    'Addis Ababa',
    'Bole',
    'M02A-NATIONAL-ID',
    documentNumber,
    password,
    password,
    pin,
  ];
  for (var index = 0; index < input.length; index++) {
    await tester.ensureVisible(fields.at(index));
    await tester.enterText(fields.at(index), input[index]);
  }
  await tester.tap(find.byType(FilledButton));
  await tester.pump();
}

Future<void> _login(
  WidgetTester tester, {
  required String phone,
  required String password,
  required String pin,
}) async {
  final fields = find.byType(TextFormField);
  expect(fields, findsNWidgets(3));
  await tester.enterText(fields.at(0), phone);
  await tester.enterText(fields.at(1), password);
  await tester.enterText(fields.at(2), pin);
  await tester.tap(find.byType(FilledButton));
  await tester.pump();
}

Future<void> _waitFor(WidgetTester tester, Finder finder) async {
  for (var attempt = 0; attempt < 60 && finder.evaluate().isEmpty; attempt++) {
    await tester.pump(const Duration(milliseconds: 250));
  }
  expect(finder, findsOneWidget);
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
