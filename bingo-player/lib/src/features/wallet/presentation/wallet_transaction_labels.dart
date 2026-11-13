import 'package:bingo_player/l10n/app_localizations.dart';

String walletTransactionTypeLabel(
  AppLocalizations localizations,
  String transactionType,
) {
  return switch (transactionType) {
    'SIGNUP_REWARD_CREDIT' => localizations.signupRewardTransactionLabel,
    'PROMOTIONAL_REWARD_CREDIT' =>
      localizations.promotionalRewardTransactionLabel,
    _ => transactionType,
  };
}
