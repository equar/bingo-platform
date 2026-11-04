import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/features/wallet/domain/wallet_models.dart';
import 'package:bingo_app/src/features/wallet/presentation/deposit_dialog.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets(
    'deposit dialog rejects zero and negative values, then returns valid input',
    (tester) async {
      DepositRequest? submitted;
      await tester.pumpWidget(_app(onOpen: (request) => submitted = request));

      await tester.tap(find.text('Open deposit'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('deposit-amount-field')),
        '-1',
      );
      await tester.enterText(
        find.byKey(const ValueKey('deposit-reference-field')),
        'QA-REFERENCE',
      );
      expect(
        tester
            .widget<FilledButton>(find.widgetWithText(FilledButton, 'Confirm'))
            .onPressed,
        isNull,
      );

      expect(find.text('Enter an amount greater than zero.'), findsOneWidget);
      expect(submitted, isNull);

      await tester.enterText(
        find.byKey(const ValueKey('deposit-amount-field')),
        '0',
      );
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<FilledButton>(find.widgetWithText(FilledButton, 'Confirm'))
            .onPressed,
        isNull,
      );
      expect(find.text('Enter an amount greater than zero.'), findsOneWidget);
      await tester.enterText(
        find.byKey(const ValueKey('deposit-amount-field')),
        '1.25',
      );
      await tester.pump();
      expect(
        tester
            .widget<TextFormField>(
              find.byKey(const ValueKey('deposit-amount-field')),
            )
            .controller!
            .text,
        '1.25',
      );
      expect(
        tester
            .widget<TextFormField>(
              find.byKey(const ValueKey('deposit-reference-field')),
            )
            .controller!
            .text,
        'QA-REFERENCE',
      );
      expect(
        tester
            .widget<FilledButton>(find.widgetWithText(FilledButton, 'Confirm'))
            .onPressed,
        isNotNull,
      );
      await tester.tap(find.text('Confirm'));
      await tester.pumpAndSettle();

      expect(submitted?.submittedCashAmount, 1.25);
      expect(submitted?.currency, 'ETB');
      expect(submitted?.telebirrReference, 'QA-REFERENCE');
    },
  );

  testWidgets('deposit validation is localized in Amharic', (tester) async {
    await tester.pumpWidget(_app(locale: const Locale('am'), onOpen: (_) {}));
    await tester.tap(find.text('ተቀማጭ ክፈት'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('deposit-amount-field')),
      '0',
    );
    await tester.enterText(
      find.byKey(const ValueKey('deposit-reference-field')),
      'QA-REFERENCE',
    );

    expect(
      tester
          .widget<FilledButton>(find.widgetWithText(FilledButton, 'አረጋግጥ'))
          .onPressed,
      isNull,
    );
    expect(find.text('ከዜሮ በላይ የሆነ መጠን ያስገቡ።'), findsOneWidget);
  });

  testWidgets('valid deposit input submits from the keyboard', (tester) async {
    DepositRequest? submitted;
    await tester.pumpWidget(_app(onOpen: (request) => submitted = request));
    await tester.tap(find.text('Open deposit'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('deposit-amount-field')),
      '5.25',
    );
    await tester.enterText(
      find.byKey(const ValueKey('deposit-reference-field')),
      'QA-REFERENCE',
    );
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();

    expect(submitted?.submittedCashAmount, 5.25);
    expect(submitted?.telebirrReference, 'QA-REFERENCE');
  });
}

Widget _app({
  required ValueChanged<DepositRequest?> onOpen,
  Locale locale = const Locale('en'),
}) {
  return MaterialApp(
    locale: locale,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: Builder(
      builder: (context) => Scaffold(
        body: TextButton(
          onPressed: () async {
            final request = await showDialog<DepositRequest>(
              context: context,
              builder: (_) => const DepositDialog(currency: 'ETB'),
            );
            onOpen(request);
          },
          child: Text(
            locale.languageCode == 'am' ? 'ተቀማጭ ክፈት' : 'Open deposit',
          ),
        ),
      ),
    ),
  );
}
