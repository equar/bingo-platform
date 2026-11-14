import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/features/wallet/presentation/wallet_transaction_labels.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/widgets.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('campaign credit transaction labels are localized in English', () async {
    final localizations = await AppLocalizations.delegate.load(
      const Locale('en'),
    );

    expect(
      walletTransactionTypeLabel(localizations, 'SIGNUP_REWARD_CREDIT'),
      'Signup reward',
    );
    expect(
      walletTransactionTypeLabel(localizations, 'PROMOTIONAL_REWARD_CREDIT'),
      'Promotional reward',
    );
  });

  test('campaign credit transaction labels are localized in Amharic', () async {
    final localizations = await AppLocalizations.delegate.load(
      const Locale('am'),
    );

    expect(
      walletTransactionTypeLabel(localizations, 'SIGNUP_REWARD_CREDIT'),
      'የምዝገባ ሽልማት',
    );
    expect(
      walletTransactionTypeLabel(localizations, 'PROMOTIONAL_REWARD_CREDIT'),
      'የዘመቻ ሽልማት',
    );
  });
}
