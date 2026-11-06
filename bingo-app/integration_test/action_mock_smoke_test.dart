import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/features/wallet/domain/wallet_models.dart';
import 'package:bingo_app/src/features/wallet/presentation/deposit_dialog.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('mocked Player deposit form blocks invalid input on device', (
    tester,
  ) async {
    DepositRequest? submitted;
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () async {
                submitted = await showDialog<DepositRequest>(
                  context: context,
                  builder: (_) => const DepositDialog(currency: 'ETB'),
                );
              },
              child: const Text('Open mocked deposit form'),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('Open mocked deposit form'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('deposit-amount-field')),
      '0',
    );
    await tester.enterText(
      find.byKey(const ValueKey('deposit-reference-field')),
      'DEVICE-QA',
    );

    final confirm = find.widgetWithText(FilledButton, 'Confirm');
    expect(tester.widget<FilledButton>(confirm).onPressed, isNull);
    expect(submitted, isNull);

    await tester.enterText(
      find.byKey(const ValueKey('deposit-amount-field')),
      '5.25',
    );
    await tester.pump();
    expect(tester.widget<FilledButton>(confirm).onPressed, isNotNull);
    await tester.tap(find.byKey(const ValueKey('deposit-reference-field')));
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();

    expect(submitted?.submittedCashAmount, 5.25);
    expect(submitted?.currency, 'ETB');
    expect(submitted?.telebirrReference, 'DEVICE-QA');
  });
}
