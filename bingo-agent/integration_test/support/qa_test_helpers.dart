import 'package:bingo_agent/src/app.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

class AgentQaTestHelpers {
  AgentQaTestHelpers(this.tester);

  final WidgetTester tester;

  Future<void> launch() async {
    await tester.pumpWidget(const BingoAgentApp());
    await tester.pumpAndSettle();
  }

  Future<void> signIn({required String phone, required String pin}) async {
    final fields = find.byType(TextFormField);
    await tester.enterText(fields.at(0), phone);
    await tester.enterText(fields.at(1), pin);
    await tester.tap(find.byType(FilledButton));
    await tester.pumpAndSettle();
  }

  Future<void> tapText(String label) async {
    await tester.tap(find.text(label));
    await tester.pumpAndSettle();
  }

  Future<void> waitFor(Finder finder, {Duration timeout = const Duration(seconds: 15)}) async {
    final deadline = DateTime.now().add(timeout);
    while (DateTime.now().isBefore(deadline)) {
      if (finder.evaluate().isNotEmpty) return;
      await tester.pump(const Duration(milliseconds: 200));
    }
    fail('Timed out waiting for $finder');
  }

  Future<void> expectVisible(Finder finder) async {
    expect(finder, findsOneWidget);
  }

  Future<void> captureScreenshot(String name) async {
    final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();
    await binding.takeScreenshot(name);
  }
}
