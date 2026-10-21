import 'package:bingo_agent/src/features/agents/data/agent_models.dart';
import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
import 'package:bingo_agent/src/features/history/data/financial_history_entry.dart';
import 'package:bingo_agent/src/features/history/presentation/financial_history_screen.dart';
import 'package:bingo_agent/src/features/home/presentation/home_shell.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets(
      'shows merged records, filters by type, and displays transaction details',
      (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          agentRepositoryProvider.overrideWithValue(_HistoryRepository())
        ],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const Scaffold(body: FinancialHistoryScreen()),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Ticket sale'), findsOneWidget);
    expect(find.text('Commission accrued'), findsOneWidget);

    await tester.tap(find.text('Ticket sale'));
    await tester.pumpAndSettle();
    expect(find.text('Transaction details'), findsOneWidget);
    expect(find.text('ticket-operation'), findsOneWidget);
    await tester.tap(find.text('Close'));
    await tester.pumpAndSettle();

    await tester
        .tap(find.byType(DropdownButtonFormField<FinancialActivityType?>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Commission accrued').last);
    await tester.pumpAndSettle();
    expect(find.text('Commission accrued'), findsNWidgets(2));
    expect(find.text('Ticket sale'), findsNothing);
  });

  testWidgets(
      'long Amharic financial-history filter labels fit on a mobile layout',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(390, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          agentRepositoryProvider.overrideWithValue(_HistoryRepository())
        ],
        child: MaterialApp(
          locale: const Locale('am'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const Scaffold(body: FinancialHistoryScreen()),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets('Amharic navigation labels fit the agent mobile shell',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(390, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          agentRepositoryProvider.overrideWithValue(_HistoryRepository())
        ],
        child: MaterialApp(
          locale: const Locale('am'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const HomeShell(),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}

class _HistoryRepository extends AgentRepository {
  _HistoryRepository() : super(Dio());

  @override
  Future<AgentDashboard> dashboard() async => AgentDashboard(
        agentId: 1,
        status: 'APPROVED',
        availableFloat: '75.00',
        clearingFloat: '0.00',
        ticketsSold: 2,
        commissionAccruals: 1,
        topUpRequests: 1,
        generatedAt: DateTime.utc(2026, 10, 20),
      );

  @override
  Future<AgentFloatBalance> floatBalance() async => AgentFloatBalance(
        agentId: 1,
        floatId: 2,
        currency: 'ETB',
        availableBalance: '75.00',
        clearingBalance: '0.00',
        updatedAt: DateTime.utc(2026, 10, 20),
      );

  @override
  Future<List<AgentFloatTransaction>> floatHistory() async => [
        AgentFloatTransaction(
          id: 1,
          transactionType: 'TICKET_SALE_DEBIT',
          amount: '25.00',
          availableBefore: '100.00',
          availableAfter: '75.00',
          clearingBefore: '0.00',
          clearingAfter: '0.00',
          relatedEntityType: 'BINGO_TICKET',
          relatedEntityId: 10,
          operationId: 'ticket-operation',
          note: null,
          createdAt: DateTime.utc(2026, 10, 20, 12),
        ),
      ];

  @override
  Future<List<CommissionAccrual>> commissionHistory() async => [
        CommissionAccrual(
          id: 2,
          ticketId: 10,
          sessionId: 3,
          saleAmount: '25.00',
          commissionAmount: '1.25',
          status: 'ACCRUED',
          operationId: 'commission-operation',
          createdAt: DateTime.utc(2026, 10, 20, 11),
          settledAt: null,
          settlementReference: null,
          settlementNote: null,
        ),
      ];

  @override
  Future<List<FloatTopUpRequest>> topUpRequests() async => [];
}
