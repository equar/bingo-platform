import 'package:bingo_agent/src/features/agents/data/agent_models.dart';
import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
import 'package:bingo_agent/src/features/auth/data/auth_models.dart';
import 'package:bingo_agent/src/features/players/presentation/players_screen.dart';
import 'package:bingo_agent/src/features/payouts/presentation/payouts_screen.dart';
import 'package:bingo_agent/src/features/tickets/presentation/ticket_sale_screen.dart';
import 'package:bingo_agent/src/features/wallet/presentation/wallet_topup_screen.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets(
    'player lookup and registration guard invalid data and submit by keyboard',
    (tester) async {
      final repository = _FakeAgentRepository();
      await tester.pumpWidget(_app(repository, const PlayersScreen()));

      final phone = find.byType(TextField).first;
      final name = find.byType(TextField).at(1);
      await tester.enterText(phone, 'not a phone');
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pump();

      expect(repository.lookupCalls, 0);
      expect(repository.registerCalls, 0);
      expect(
        tester
            .widget<FilledButton>(find.widgetWithText(FilledButton, 'Lookup'))
            .onPressed,
        isNull,
      );
      expect(find.text('Enter a valid phone number.'), findsOneWidget);

      await tester.enterText(phone, '0911000001');
      expect(
        tester
            .widget<OutlinedButton>(
              find.widgetWithText(OutlinedButton, 'Register'),
            )
            .onPressed,
        isNull,
      );
      await tester.enterText(name, '   ');
      expect(repository.registerCalls, 0);

      await tester.enterText(name, 'QA Player');
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pumpAndSettle();
      expect(repository.registerCalls, 1);
      expect(repository.registeredBody, {
        'phoneNumber': '0911000001',
        'displayName': 'QA Player',
      });
    },
  );

  testWidgets(
    'wallet top-up blocks invalid amount and retries unknown outcome with same key',
    (tester) async {
      final repository = _FakeAgentRepository()..failFirstTransfer = true;
      await tester.pumpWidget(_app(repository, const WalletTopUpScreen()));

      await tester.enterText(find.byType(TextField).at(0), '0911000001');
      await tester.enterText(find.byType(TextField).at(1), '0');
      await tester.enterText(find.byType(TextField).at(2), '1234');
      final topUp = find.widgetWithText(FilledButton, 'Top up');
      expect(tester.widget<FilledButton>(topUp).onPressed, isNull);
      expect(repository.transferKeys, isEmpty);

      await tester.enterText(find.byType(TextField).at(1), '10.50');
      await tester.tap(find.byType(TextField).at(2));
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pumpAndSettle();
      expect(repository.transferKeys, hasLength(1));
      expect(
        find.text(
          'We couldn’t confirm the transaction status. Check your transaction history before trying again.',
        ),
        findsOneWidget,
      );
      expect(
        tester.widget<TextField>(find.byType(TextField).first).enabled,
        isFalse,
      );

      await tester.tap(find.widgetWithText(FilledButton, 'Retry'));
      await tester.pumpAndSettle();
      expect(repository.transferKeys, hasLength(2));
      expect(repository.transferKeys.first, repository.transferKeys.last);
      expect(repository.transferBodies.first, repository.transferBodies.last);
      expect(repository.transferBodies.first['amount'], 10.5);
    },
  );

  testWidgets(
    'ticket sale rejects nonpositive session IDs and valid input sells once',
    (tester) async {
      final repository = _FakeAgentRepository();
      await tester.pumpWidget(
        _app(repository, const TicketSaleScreen(), locale: const Locale('am')),
      );

      await tester.enterText(find.byType(TextField).at(0), '0');
      await tester.enterText(find.byType(TextField).at(1), '0911000001');
      await tester.enterText(find.byType(TextField).at(2), '1234');
      final sale = find.byType(FilledButton);
      expect(tester.widget<FilledButton>(sale).onPressed, isNull);
      expect(repository.ticketBodies, isEmpty);
      expect(find.text('ከዜሮ በላይ የሆነ ትክክለኛ የሴሽን መለያ ያስገቡ።'), findsOneWidget);

      await tester.enterText(find.byType(TextField).at(0), '42');
      await tester.pump();
      expect(tester.widget<FilledButton>(sale).onPressed, isNotNull);
      await tester.tap(sale);
      await tester.pumpAndSettle();
      expect(repository.ticketBodies, hasLength(1));
      expect(repository.ticketBodies.single['sessionId'], 42);
      expect(find.text('QA-TICKET'), findsOneWidget);
    },
  );

  testWidgets(
    'payout lookup clears stale result and completed payout cannot be resubmitted',
    (tester) async {
      final repository = _FakeAgentRepository();
      await tester.pumpWidget(_app(repository, const PayoutsScreen()));

      final code = find.byType(TextField).first;
      final pin = find.byType(TextField).at(1);
      final verify = find.widgetWithText(OutlinedButton, 'Verify and disburse');
      expect(tester.widget<OutlinedButton>(verify).onPressed, isNull);

      await tester.enterText(code, 'PAYOUT-ONE');
      await tester.enterText(pin, '123');
      await tester.tap(find.widgetWithText(FilledButton, 'Lookup'));
      await tester.pumpAndSettle();
      expect(repository.payoutLookupCalls, 1);
      expect(find.text('2.00 ETB'), findsOneWidget);
      expect(tester.widget<OutlinedButton>(verify).onPressed, isNull);

      await tester.enterText(code, 'PAYOUT-TWO');
      await tester.pump();
      expect(find.text('2.00 ETB'), findsNothing);
      await tester.enterText(pin, '1234');
      await tester.pump();
      expect(tester.widget<OutlinedButton>(verify).onPressed, isNotNull);
      await tester.tap(
        find.widgetWithText(OutlinedButton, 'Verify and disburse'),
      );
      await tester.pumpAndSettle();
      expect(repository.verifyBodies, hasLength(1));
      expect(repository.verifyBodies.single['payoutCode'], 'PAYOUT-TWO');
      expect(find.text('Completed'), findsOneWidget);
      expect(tester.widget<OutlinedButton>(verify).onPressed, isNull);
      await tester.tap(pin);
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pumpAndSettle();
      expect(repository.verifyBodies, hasLength(1));
    },
  );
}

Widget _app(
  AgentRepository repository,
  Widget home, {
  Locale locale = const Locale('en'),
}) {
  return ProviderScope(
    overrides: [agentRepositoryProvider.overrideWithValue(repository)],
    child: MaterialApp(
      locale: locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(body: home),
    ),
  );
}

class _FakeAgentRepository extends AgentRepository {
  _FakeAgentRepository() : super(Dio());

  int lookupCalls = 0;
  int payoutLookupCalls = 0;
  int registerCalls = 0;
  bool failFirstTransfer = false;
  Map<String, dynamic>? registeredBody;
  final transferKeys = <String>[];
  final transferBodies = <Map<String, dynamic>>[];
  final ticketBodies = <Map<String, dynamic>>[];
  final verifyBodies = <Map<String, dynamic>>[];

  @override
  Future<UserProfile> lookupPlayer(String phoneNumber) async {
    lookupCalls += 1;
    return UserProfile(
      id: 1,
      displayName: 'QA Player',
      phoneNumber: phoneNumber,
      roles: const ['PLAYER'],
    );
  }

  @override
  Future<UserProfile> registerPlayer(Map<String, dynamic> body) async {
    registerCalls += 1;
    registeredBody = Map.from(body);
    return UserProfile(
      id: 2,
      displayName: body['displayName'] as String,
      phoneNumber: body['phoneNumber'] as String,
      roles: const ['PLAYER'],
    );
  }

  @override
  Future<Map<String, dynamic>> transferFloat(
    Map<String, dynamic> body,
    String key,
  ) async {
    transferKeys.add(key);
    transferBodies.add(Map.from(body));
    if (failFirstTransfer) {
      failFirstTransfer = false;
      throw DioException(
        requestOptions: RequestOptions(
          path: '/api/v1/agent/float/transfers',
          method: 'POST',
        ),
        type: DioExceptionType.connectionTimeout,
      );
    }
    return {'availableBalance': '89.50'};
  }

  @override
  Future<Map<String, dynamic>> sellTicket(
    Map<String, dynamic> body,
    String key,
  ) async {
    ticketBodies.add(Map.from(body));
    return {
      'ticket': {'publicTicketCode': 'QA-TICKET'},
    };
  }

  @override
  Future<PayoutLookup> lookupPayout(String code) async {
    payoutLookupCalls += 1;
    return PayoutLookup.fromJson({
      'entitlementId': 7,
      'ticketId': 8,
      'claimantId': 9,
      'prizeAmount': '2.00',
      'currency': 'ETB',
      'payoutCode': code,
      'status': 'APPROVED',
    });
  }

  @override
  Future<PayoutVerificationResult> verifyPayout(
    Map<String, dynamic> body,
    String key,
  ) async {
    verifyBodies.add(Map.from(body));
    return PayoutVerificationResult.fromJson({
      'recordId': 1,
      'entitlementId': 7,
      'agentId': 3,
      'payoutCode': body['payoutCode'],
      'status': 'COMPLETED',
      'disbursedAmount': '2.00',
    });
  }
}
