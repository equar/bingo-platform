import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/features/wallet/domain/wallet_models.dart';
import 'package:bingo_player/src/features/wallet/presentation/wallet_transaction_labels.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/widgets.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('deposit statuses show only pending, approved, or rejected', () {
    DepositClaim depositWithStatus(String status) =>
        DepositClaim.fromJson({'status': status});

    expect(depositWithStatus('PENDING').displayStatus, 'PENDING');
    expect(depositWithStatus('AWAITING_VERIFICATION').displayStatus, 'PENDING');
    expect(depositWithStatus('VERIFIED').displayStatus, 'PENDING');
    expect(
      depositWithStatus('VERIFICATION_EXCEPTION').displayStatus,
      'PENDING',
    );
    expect(depositWithStatus('APPROVED').displayStatus, 'APPROVED');
    expect(depositWithStatus('REJECTED').displayStatus, 'REJECTED');
  });

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
    expect(
      walletTransactionCampaignName(
        localizations,
        'PROMOTIONAL_REWARD_CREDIT',
        'Referral reward – This is a reward from the first time registered players',
      ),
      'Referral reward',
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
