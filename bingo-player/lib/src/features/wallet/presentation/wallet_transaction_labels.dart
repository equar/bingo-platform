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

String walletTransactionCampaignName(
  AppLocalizations localizations,
  String transactionType,
  String? note,
) {
  if (!transactionType.endsWith('_REWARD_CREDIT')) {
    return walletTransactionTypeLabel(localizations, transactionType);
  }
  final campaignName = note?.split(RegExp(r'\s+[—–-]\s+')).first.trim();
  return campaignName == null || campaignName.isEmpty
      ? walletTransactionTypeLabel(localizations, transactionType)
      : campaignName;
}

bool isWalletCampaignReward(String transactionType) =>
    transactionType.endsWith('_REWARD_CREDIT');
