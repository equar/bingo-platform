import 'dart:convert';
import 'dart:io';
import 'dart:ui' show Locale;

import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Tigrinya catalog has every English message key', () {
    final english =
        jsonDecode(File('lib/l10n/app_en.arb').readAsStringSync())
            as Map<String, dynamic>;
    final tigrinya =
        jsonDecode(File('lib/l10n/app_ti.arb').readAsStringSync())
            as Map<String, dynamic>;
    final englishMessageKeys = english.keys
        .where((key) => !key.startsWith('@'))
        .toSet();
    final tigrinyaMessageKeys = tigrinya.keys
        .where((key) => !key.startsWith('@'))
        .toSet();

    expect(tigrinyaMessageKeys, equals(englishMessageKeys));
  });

  test('wallet and claim labels are translated in Amharic', () async {
    final english = await AppLocalizations.delegate.load(const Locale('en'));
    final amharic = await AppLocalizations.delegate.load(const Locale('am'));

    final translatedLabels = [
      (english.walletTransactionsTab, amharic.walletTransactionsTab),
      (english.walletDepositsTab, amharic.walletDepositsTab),
      (english.walletTransfersTab, amharic.walletTransfersTab),
      (english.availableBalanceLabel, amharic.availableBalanceLabel),
      (english.promotionalBalanceLabel, amharic.promotionalBalanceLabel),
      (
        english.promotionalBalanceRestrictionHelp,
        amharic.promotionalBalanceRestrictionHelp,
      ),
      (english.currentBalance, amharic.currentBalance),
      (english.sendMoneyTitle, amharic.sendMoneyTitle),
      (english.recipientPhoneLabel, amharic.recipientPhoneLabel),
      (english.lookupRecipient, amharic.lookupRecipient),
      (english.sendTransfer, amharic.sendTransfer),
      (english.recipientPreviewTitle, amharic.recipientPreviewTitle),
      (english.drawSnapshotLabel, amharic.drawSnapshotLabel),
      (english.calledNumbersLabel, amharic.calledNumbersLabel),
      (english.serverValidationLabel, amharic.serverValidationLabel),
      (english.reviewNoteLabel, amharic.reviewNoteLabel),
    ];

    for (final (englishLabel, amharicLabel) in translatedLabels) {
      expect(amharicLabel, isNotEmpty);
      expect(amharicLabel, isNot(englishLabel));
    }
  });

  test(
    'Tigrinya is available with localized language and lobby labels',
    () async {
      final tigrinya = await AppLocalizations.delegate.load(const Locale('ti'));

      expect(tigrinya.languageTigrinya, 'ትግርኛ');
      expect(tigrinya.homeLobby, 'ኣዳራሽ');
      expect(tigrinya.profileLanguagePreferenceDescription, contains('ትግርኛ'));
    },
  );

  test(
    'Tigrinya purchase recovery messages retain all dynamic values',
    () async {
      final english = await AppLocalizations.delegate.load(const Locale('en'));
      final tigrinya = await AppLocalizations.delegate.load(const Locale('ti'));
      final summary = tigrinya.purchaseOutcomeSummary(
        2,
        3,
        'T-11, T-12',
        1,
        '40.00',
        'ETB',
      );

      expect(summary, contains('T-11, T-12'));
      expect(summary, contains('40.00'));
      expect(summary, contains('ETB'));
      expect(
        summary,
        isNot(
          english.purchaseOutcomeSummary(2, 3, 'T-11, T-12', 1, '40.00', 'ETB'),
        ),
      );
      expect(
        tigrinya.purchaseUnknownOutcome,
        isNot(english.purchaseUnknownOutcome),
      );
      expect(
        tigrinya.depositAmountLimitChanged,
        isNot(english.depositAmountLimitChanged),
      );
      expect(
        tigrinya.depositAmountAboveMaximum,
        isNot(english.depositAmountAboveMaximum),
      );
    },
  );
}
