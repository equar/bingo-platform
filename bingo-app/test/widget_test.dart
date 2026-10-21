import 'package:bingo_app/src/app.dart';
import 'package:bingo_app/src/core/config/app_config.dart';
import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('renders the app shell on startup', (WidgetTester tester) async {
    await tester.pumpWidget(const BingoApp());
    await tester.pumpAndSettle();

    expect(find.byType(Scaffold), findsOneWidget);
    expect(find.text('API base URL is missing'), findsOneWidget);
    expect(find.textContaining('--dart-define=API_BASE_URL'), findsOneWidget);
  });

  testWidgets('shows actionable guidance for an invalid API URL', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      BingoApp(
        config: const AppConfig(
          apiBaseUrl: 'ftp://api.example.com',
          apiBaseUrlLabel: 'API_BASE_URL',
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('API base URL is invalid'), findsOneWidget);
    expect(
      find.textContaining('production builds require HTTPS'),
      findsOneWidget,
    );
  });

  testWidgets('shows localized configuration guidance in Amharic', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(
      BingoApp(
        config: const AppConfig(
          apiBaseUrl: '',
          apiBaseUrlLabel: 'API_BASE_URL',
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.binding.setLocale('am', '');
    await tester.pumpAndSettle();

    expect(
      find.text(
        AppLocalizations.of(
          tester.element(find.byType(Scaffold)),
        )!.missingApiBaseUrlTitle,
      ),
      findsOneWidget,
    );
  });
}
