import 'package:bingo_agent/src/features/agents/data/agent_models.dart';
import 'package:bingo_agent/src/features/history/data/financial_history_entry.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
      'combines authoritative float ledger, commission accrual, and top-up request records',
      () {
    final entries = FinancialHistoryEntry.combine(
      currency: 'ETB',
      floatTransactions: [
        AgentFloatTransaction(
          id: 2,
          transactionType: 'PAYOUT_DISBURSEMENT_DEBIT',
          amount: '25.00',
          availableBefore: '100.00',
          availableAfter: '75.00',
          clearingBefore: '0.00',
          clearingAfter: '0.00',
          relatedEntityType: 'PAYOUT_VERIFICATION_RECORD',
          relatedEntityId: 9,
          operationId: 'payout-operation',
          note: null,
          createdAt: DateTime.utc(2026, 10, 20, 12),
        ),
      ],
      commissions: [
        CommissionAccrual(
          id: 4,
          ticketId: 8,
          sessionId: 6,
          saleAmount: '100.00',
          commissionAmount: '5.00',
          status: 'ACCRUED',
          operationId: 'commission-operation',
          createdAt: DateTime.utc(2026, 10, 20, 11),
          settledAt: null,
          settlementReference: null,
          settlementNote: null,
        ),
      ],
      topUpRequests: [
        FloatTopUpRequest(
          id: 3,
          amount: '50.00',
          currency: 'ETB',
          status: 'REJECTED',
          operationId: 'top-up-operation',
          reviewNote: 'Not approved',
          reviewedByUserId: 12,
          reviewedAt: DateTime.utc(2026, 10, 20, 10),
          createdAt: DateTime.utc(2026, 10, 20, 10),
        ),
      ],
    );

    expect(entries, hasLength(3));
    expect(entries.map((entry) => entry.type),
        contains(FinancialActivityType.payoutDisbursement));
    expect(entries.map((entry) => entry.type),
        contains(FinancialActivityType.commissionAccrual));
    expect(entries.map((entry) => entry.type),
        contains(FinancialActivityType.floatTopUpRequest));
    expect(entries.first.reference, 'payout-operation');
    expect(entries.first.relatedEntity, 'PAYOUT_VERIFICATION_RECORD#9');
    expect(entries.last.status, 'REJECTED');
    expect(entries.last.counterparty, 'ADMIN#12');
    expect(entries.first.createdAt.isAfter(entries.last.createdAt), isTrue);
  });
}
