import 'package:flutter_test/flutter_test.dart';

import 'package:bingo_agent/src/app.dart';

void main() {
  testWidgets('shows the missing config screen without API_BASE_URL',
      (WidgetTester tester) async {
    await tester.pumpWidget(const BingoAgentApp());
    await tester.pumpAndSettle();

    expect(find.text('Backend URL is missing or invalid'), findsOneWidget);
    expect(
      find.text(
        'Set API_BASE_URL to an absolute HTTP or HTTPS origin. Local HTTP is for debug only; profile and release builds require HTTPS and reject loopback URLs.',
      ),
      findsOneWidget,
    );
  });
}
