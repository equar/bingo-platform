import 'package:bingo_agent/src/app.dart';
import 'package:bingo_agent/src/core/storage/session_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const _phone = String.fromEnvironment('BINGO_M02B_AGENT_PHONE');
const _pin = String.fromEnvironment('BINGO_M02B_AGENT_PIN');
const _amount = String.fromEnvironment('BINGO_M02B_AMOUNT', defaultValue: '12.34');
const _availableBalance = String.fromEnvironment('BINGO_M02B_AVAILABLE_BALANCE', defaultValue: '25.5');
const _reference = String.fromEnvironment('BINGO_M02B_REFERENCE');
const _phase = String.fromEnvironment('BINGO_M02B_PHASE', defaultValue: 'submit');
var _surfaceConverted = false;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Module 02B agent submits a float request without balance credit', (tester) async {
    expect(_phone, isNotEmpty);
    expect(_pin, isNotEmpty);
    expect(_reference, startsWith('QA-M02B-'));
    await const SessionStorage().clear();
    await tester.pumpWidget(const BingoAgentApp());
    await tester.pumpAndSettle();
    await _waitFor(tester, find.byType(TextFormField));

    final loginFields = find.byType(TextFormField);
    await tester.enterText(loginFields.at(0), _phone);
    await tester.enterText(loginFields.at(1), _pin);
    await tester.tap(find.byType(FilledButton));
    await _waitFor(tester, find.byType(NavigationBar));
    expect((await const SessionStorage().readSession())['token'], isNotEmpty);

    if (_phase == 'posted') {
      await tester.tap(find.text('Float'));
      await _waitFor(tester, find.textContaining(_reference));
      final formattedBalance = _availableBalance.replaceFirst(RegExp(r'\.0+$'), '');
      expect(find.textContaining('Available: $formattedBalance ETB'), findsOneWidget);
      expect(find.text('Float top-up'), findsAtLeastNWidgets(1));
      final requestRow = find.ancestor(
        of: find.textContaining(_reference),
        matching: find.byType(ListTile),
      );
      expect(requestRow, findsOneWidget);
      expect(find.descendant(of: requestRow, matching: find.textContaining('25.50')), findsOneWidget);
      expect(find.descendant(of: requestRow, matching: find.textContaining('Approved')), findsOneWidget);
      final suffix = _reference.substring(_reference.lastIndexOf('-') + 1);
      await _capture(tester, 'module-02b-agent-float-posted-$suffix');
      return;
    }

    await tester.tap(find.text('Float'));
    await _waitFor(tester, find.text('Request float top-up'));
    await tester.pumpAndSettle();
    final runSuffix = _reference.substring(_reference.lastIndexOf('-') + 1);
    await _capture(tester, 'module-02b-agent-float-before-request-$runSuffix');
    await tester.tap(find.text('Request float top-up'));
    await tester.pumpAndSettle();

    final fields = find.byType(TextFormField);
    expect(fields, findsNWidgets(4));
    await tester.enterText(fields.at(0), _amount);
    await tester.enterText(fields.at(1), _reference);
    expect(find.byType(DropdownButtonFormField<int>), findsOneWidget);
    await tester.tap(find.text('Submit request'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Confirm'));
    await _waitFor(tester, find.text('Top-up request submitted for admin review.'));
    await _waitFor(tester, find.textContaining(_reference));
    await _capture(tester, 'module-02b-agent-float-request-submitted-$runSuffix');
  });
}

Future<void> _waitFor(WidgetTester tester, Finder finder) async {
  for (var attempt = 0; attempt < 80 && finder.evaluate().isEmpty; attempt++) {
    await tester.pump(const Duration(milliseconds: 250));
  }
  expect(finder.evaluate(), isNotEmpty);
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
