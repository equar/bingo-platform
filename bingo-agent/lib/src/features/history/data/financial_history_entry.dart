import 'package:bingo_agent/src/features/agents/data/agent_models.dart';

enum FinancialActivityType {
  openingBalance,
  floatTopUp,
  ticketSale,
  playerWalletTopUp,
  payoutDisbursement,
  payoutClearing,
  commissionCredit,
  manualAdjustment,
  commissionAccrual,
  floatTopUpRequest,
  other,
}

class FinancialHistoryEntry {
  const FinancialHistoryEntry({
    required this.type,
    required this.amount,
    required this.currency,
    required this.createdAt,
    required this.status,
    required this.reference,
    this.counterparty,
    this.note,
    this.relatedEntity,
  });

  final FinancialActivityType type;
  final String amount;
  final String currency;
  final DateTime createdAt;
  final String status;
  final String reference;
  final String? counterparty;
  final String? note;
  final String? relatedEntity;

  static List<FinancialHistoryEntry> combine({
    required String currency,
    required List<AgentFloatTransaction> floatTransactions,
    required List<CommissionAccrual> commissions,
    required List<FloatTopUpRequest> topUpRequests,
  }) {
    final entries = <FinancialHistoryEntry>[
      for (final transaction in floatTransactions)
        FinancialHistoryEntry(
          type: _floatType(transaction.transactionType),
          amount: transaction.amount,
          currency: currency,
          createdAt: transaction.createdAt,
          status: 'COMPLETED',
          reference: transaction.operationId,
          note: transaction.note,
          relatedEntity: _relatedEntity(
              transaction.relatedEntityType, transaction.relatedEntityId),
        ),
      for (final commission in commissions)
        FinancialHistoryEntry(
          type: FinancialActivityType.commissionAccrual,
          amount: commission.commissionAmount,
          currency: currency,
          createdAt: commission.settledAt ?? commission.createdAt,
          status: commission.status,
          reference: commission.settlementReference?.isNotEmpty == true
              ? commission.settlementReference!
              : commission.operationId,
          note: commission.settlementNote,
          relatedEntity: 'TICKET#${commission.ticketId}',
        ),
      for (final request in topUpRequests)
        FinancialHistoryEntry(
          type: FinancialActivityType.floatTopUpRequest,
          amount: request.amount,
          currency: request.currency,
          createdAt: request.createdAt,
          status: request.status,
          reference: request.operationId,
          counterparty: request.reviewedByUserId == null
              ? null
              : 'ADMIN#${request.reviewedByUserId}',
          note: request.reviewNote,
        ),
    ]..sort((a, b) => b.createdAt.compareTo(a.createdAt));
    return entries;
  }

  static FinancialActivityType _floatType(String type) => switch (type) {
        'OPENING_BALANCE' => FinancialActivityType.openingBalance,
        'TOP_UP' => FinancialActivityType.floatTopUp,
        'TICKET_SALE_DEBIT' => FinancialActivityType.ticketSale,
        'PLAYER_WALLET_TRANSFER_DEBIT' =>
          FinancialActivityType.playerWalletTopUp,
        'PAYOUT_DISBURSEMENT_DEBIT' => FinancialActivityType.payoutDisbursement,
        'PAYOUT_CLEARING_CREDIT' => FinancialActivityType.payoutClearing,
        'COMMISSION_CREDIT' => FinancialActivityType.commissionCredit,
        'MANUAL_ADJUSTMENT' => FinancialActivityType.manualAdjustment,
        _ => FinancialActivityType.other,
      };

  static String? _relatedEntity(String? type, int? id) {
    if (type == null || type.isEmpty || id == null) {
      return null;
    }
    return '$type#$id';
  }
}
