import 'package:bingo_agent/src/features/agents/data/agent_models.dart';
import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
import 'package:bingo_agent/src/features/float/presentation/float_history_screen.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets(
      'validates amount and retries a top-up request with the same idempotency key',
      (tester) async {
    final repository = _FakeAgentRepository()..failFirstSubmission = true;
    await tester.pumpWidget(
      ProviderScope(
        overrides: [agentRepositoryProvider.overrideWithValue(repository)],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const Scaffold(body: FloatHistoryScreen()),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Request float top-up'));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextFormField).first, '0');
    await tester.enterText(find.byType(TextFormField).at(1), 'QA-TEST-REF');
    await tester.tap(find.text('Submit request'));
    await tester.pumpAndSettle();
    expect(find.text('Enter an amount greater than zero.'), findsOneWidget);
    expect(repository.submissionKeys, isEmpty);

    await tester.enterText(find.byType(TextFormField).first, '12.50');
    await tester.enterText(find.byType(TextFormField).at(1), 'QA-TEST-REF');
    await tester.tap(find.text('Submit request'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Confirm'));
    await tester.pumpAndSettle();
    expect(repository.submissionKeys, hasLength(1));
    expect(
        find.text(
            'Could not submit the request. Retry to safely check the same request.'),
        findsOneWidget);

    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(repository.submissionKeys, hasLength(2));
    expect(repository.submissionKeys[0], repository.submissionKeys[1]);
    expect(repository.submittedBodies[0]['amount'], 12.5);
    expect(repository.submittedBodies[1], repository.submittedBodies[0]);
    expect(find.textContaining('Pending'), findsOneWidget);
  });
}

class _FakeAgentRepository extends AgentRepository {
  _FakeAgentRepository() : super(Dio());

  bool failFirstSubmission = false;
  final submissionKeys = <String>[];
  final submittedBodies = <Map<String, dynamic>>[];

  @override
  Future<List<AgentFloatBankAccount>> floatBankAccounts() async => const [
        AgentFloatBankAccount(
          id: 1,
          bankName: 'QA Test Bank',
          maskedAccountNumber: '••••0001',
          accountHolderName: 'QA Test Holder',
          currency: 'ETB',
        ),
      ];

  @override
  Future<AgentFloatBalance> floatBalance() async => AgentFloatBalance(
        agentId: 3,
        floatId: 7,
        currency: 'ETB',
        availableBalance: '100.00',
        clearingBalance: '0.00',
        updatedAt: DateTime.utc(2026, 10, 20),
      );

  @override
  Future<List<AgentFloatTransaction>> floatHistory() async => [];

  @override
  Future<List<FloatTopUpRequest>> topUpRequests() async => [
        FloatTopUpRequest(
          id: 1,
          amount: '12.50',
          currency: 'ETB',
          status: 'PENDING',
          operationId: 'top-up-operation',
          reviewNote: null,
          reviewedByUserId: null,
          reviewedAt: null,
          createdAt: DateTime.utc(2026, 10, 20),
        ),
      ];

  @override
  Future<FloatTopUpRequest> submitTopUp(
      Map<String, dynamic> body, String key) async {
    submissionKeys.add(key);
    submittedBodies.add(Map.from(body));
    if (failFirstSubmission) {
      failFirstSubmission = false;
      throw DioException(
        requestOptions:
            RequestOptions(path: '/api/v1/agent/float-top-up-requests'),
        type: DioExceptionType.connectionTimeout,
      );
    }
    return FloatTopUpRequest(
      id: 1,
      amount: '12.50',
      currency: 'ETB',
      status: 'PENDING',
      operationId: key,
      reviewNote: null,
      reviewedByUserId: null,
      reviewedAt: null,
      createdAt: DateTime.utc(2026, 10, 20),
    );
  }
}
