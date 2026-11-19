import 'dart:convert';

import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/features/wallet/domain/wallet_models.dart';

String walletTransactionTypeLabel(
  AppLocalizations localizations,
  String transactionType,
) {
  return switch (transactionType) {
    'SIGNUP_REWARD_CREDIT' => localizations.signupRewardTransactionLabel,
    'PROMOTIONAL_REWARD_CREDIT' =>
      localizations.promotionalRewardTransactionLabel,
    'TICKET_PURCHASE_DEBIT' => localizations.ticketPurchaseTransactionLabel,
    'WALLET_TRANSFER_DEBIT' => localizations.walletTransferSentLabel,
    'WALLET_TRANSFER_CREDIT' => localizations.walletTransferReceivedLabel,
    'DEPOSIT_CREDIT' => localizations.depositTransactionLabel,
    'PRIZE_CREDIT' => localizations.prizeTransactionLabel,
    'TICKET_REFUND_CREDIT' => localizations.ticketRefundTransactionLabel,
    'WALLET_ADJUSTMENT' => localizations.walletAdjustmentTransactionLabel,
    'WALLET_WITHDRAWAL_DEBIT' => localizations.withdrawalTransactionLabel,
    'WALLET_WITHDRAWAL_REFUND' =>
      localizations.withdrawalRefundTransactionLabel,
    _ => transactionType,
  };
}

String walletTransactionDisplayLabel(
  AppLocalizations localizations,
  WalletTransaction entry,
) {
  final metadata = _metadata(entry.metadataJson);
  switch (entry.transactionType) {
    case 'TICKET_PURCHASE_DEBIT':
      final gameName = entry.note?.trim();
      return gameName == null ||
              gameName.isEmpty ||
              gameName == 'Bingo ticket purchase'
          ? localizations.ticketPurchaseTransactionLabel
          : '${localizations.ticketPurchasedForLabel} $gameName';
    case 'WALLET_TRANSFER_DEBIT':
      return _withCounterparty(
        localizations.walletTransferToLabel,
        metadata['recipientName'] ?? metadata['recipientPhone'],
      );
    case 'WALLET_TRANSFER_CREDIT':
      return _withCounterparty(
        localizations.walletTransferFromLabel,
        metadata['senderName'] ?? metadata['senderPhone'],
      );
    case 'DEPOSIT_CREDIT':
      final provider = metadata['providerName'];
      if (provider != null && provider.isNotEmpty) {
        return '${localizations.depositFromLabel} $provider';
      }
      final note = entry.note?.trim() ?? '';
      final providerPrefix = note.startsWith('Deposit from ')
          ? 'Deposit from '
          : note.startsWith('Simulated QA deposit from ')
          ? 'Simulated QA deposit from '
          : null;
      if (providerPrefix != null) {
        return '${localizations.depositFromLabel} ${note.substring(providerPrefix.length)}';
      }
      if (note.toLowerCase().contains('telebirr')) {
        return '${localizations.depositFromLabel} Telebirr';
      }
      return localizations.depositTransactionLabel;
    default:
      return walletTransactionTypeLabel(localizations, entry.transactionType);
  }
}

Map<String, String> _metadata(String? json) {
  if (json == null || json.isEmpty) return const {};
  try {
    final decoded = jsonDecode(json);
    if (decoded is! Map) return const {};
    return decoded.map(
      (key, value) => MapEntry(key.toString(), value.toString()),
    );
  } on FormatException {
    return const {};
  }
}

String _withCounterparty(String prefix, String? name) =>
    name == null || name.trim().isEmpty ? prefix : '$prefix ${name.trim()}';

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
