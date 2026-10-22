import 'dart:ui' show Locale;

import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('wallet and claim labels are translated in Amharic', () async {
    final english = await AppLocalizations.delegate.load(const Locale('en'));
    final amharic = await AppLocalizations.delegate.load(const Locale('am'));

    final translatedLabels = [
      (english.walletTransactionsTab, amharic.walletTransactionsTab),
      (english.walletDepositsTab, amharic.walletDepositsTab),
      (english.walletTransfersTab, amharic.walletTransfersTab),
      (english.availableBalanceLabel, amharic.availableBalanceLabel),
      (english.promotionalBalanceLabel, amharic.promotionalBalanceLabel),
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
}
