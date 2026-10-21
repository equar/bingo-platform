import 'package:flutter_test/flutter_test.dart';

import 'package:bingo_agent/src/app.dart';

void main() {
  testWidgets('shows the missing config screen without API_BASE_URL',
      (WidgetTester tester) async {
    await tester.pumpWidget(const BingoAgentApp());
    await tester.pumpAndSettle();

    expect(find.text('Missing API URL'), findsOneWidget);
    expect(find.text('Configure the backend base URL with API_BASE_URL.'),
        findsOneWidget);
  });
}
