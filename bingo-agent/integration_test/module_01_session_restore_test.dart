import 'package:bingo_agent/src/app.dart';
import 'package:bingo_agent/src/core/storage/session_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'Agent session restores after a new app process and logout clears it',
    (tester) async {
      await tester.pumpWidget(const BingoAgentApp());
      await _waitFor(tester, find.byType(NavigationBar));
      final storedToken = (await const SessionStorage().readSession())['token'];
      expect(storedToken, isNotEmpty);
      await _captureScreenshot(tester);

      await tester.tap(find.byTooltip('Logout'));
      await _waitForCount(tester, find.byType(TextFormField), 3);
      expect((await const SessionStorage().readSession())['token'], isNull);
    },
  );
}

Future<void> _waitForCount(
  WidgetTester tester,
  Finder finder,
  int count,
) async {
  final deadline = DateTime.now().add(const Duration(seconds: 15));
  while (DateTime.now().isBefore(deadline) &&
      finder.evaluate().length != count) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  expect(finder, findsNWidgets(count));
}

Future<void> _waitFor(WidgetTester tester, Finder finder) async {
  final deadline = DateTime.now().add(const Duration(seconds: 15));
  while (DateTime.now().isBefore(deadline) && finder.evaluate().isEmpty) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  expect(finder, findsOneWidget);
}

Future<void> _captureScreenshot(WidgetTester tester) async {
  final binding = IntegrationTestWidgetsFlutterBinding.instance;
  await binding.convertFlutterSurfaceToImage();
  await tester.pump();
  await binding.takeScreenshot('module-01-agent-process-restart');
}
