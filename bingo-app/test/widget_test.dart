import 'package:bingo_app/src/app.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('shows missing API base URL message when config is absent', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const BingoApp());
    await tester.pumpAndSettle();

    expect(find.text('API base URL is missing'), findsOneWidget);
    expect(
      find.text(
        'Start the app with --dart-define=API_BASE_URL=... so the client can reach the backend.',
      ),
      findsOneWidget,
    );
  });
}
