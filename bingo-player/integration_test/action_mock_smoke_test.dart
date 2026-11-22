import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/core/models/page_result.dart';
import 'package:bingo_player/src/features/wallet/data/wallet_api.dart';
import 'package:bingo_player/src/features/wallet/data/wallet_repository.dart';
import 'package:bingo_player/src/features/wallet/domain/wallet_models.dart';
import 'package:bingo_player/src/features/wallet/presentation/wallet_screen.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('mocked Player wallet deposit validates before submission', (
    tester,
  ) async {
    final repository = _DepositRepository();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          walletRepositoryProvider.overrideWithValue(repository),
          walletBalanceProvider.overrideWith(
            (ref) async => WalletBalance(
              walletId: 1,
              currency: 'ETB',
              availableBalance: 0,
              promotionalBalance: 0,
              totalBalance: 0,
              updatedAt: DateTime.utc(2026),
            ),
          ),
          walletTransactionsProvider.overrideWith(
            (ref, page) async => _empty<WalletTransaction>(page),
          ),
          walletDepositsProvider.overrideWith(
            (ref, page) async => _empty<DepositClaim>(page),
          ),
        ],
        child: MaterialApp(
          localizationsDelegates: const [
            AppLocalizations.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: AppLocalizations.supportedLocales,
          home: const WalletScreen(initialTab: 1),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('wallet-open-deposit-form-button')),
    );
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('deposit-amount-field')),
      '0',
    );
    await tester.enterText(
      find.byKey(const ValueKey('deposit-reference-field')),
      'DEVICE-QA',
    );
    await tester.tap(
      find.byKey(const ValueKey('wallet-confirm-deposit-button')),
    );
    await tester.pumpAndSettle();
    expect(repository.calls, 0);

    await tester.enterText(
      find.byKey(const ValueKey('deposit-amount-field')),
      '5.25',
    );
    await tester.tap(
      find.byKey(const ValueKey('wallet-confirm-deposit-button')),
    );
    await tester.pumpAndSettle();
    expect(repository.calls, 1);
  });
}

PageResult<T> _empty<T>(int page) => PageResult(
  content: <T>[],
  page: page,
  size: 10,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
);

class _DepositRepository extends WalletRepository {
  _DepositRepository() : super(WalletApi(Dio()));
  int calls = 0;

  @override
  Future<DepositClaim> submitDeposit(DepositRequest request, String key) async {
    calls++;
    return DepositClaim(
      id: 1,
      operationId: 'mock',
      playerId: 1,
      submittedCashAmount: request.submittedCashAmount,
      currency: request.currency,
      conversionRateSnapshot: 1,
      calculatedCreditAmount: request.submittedCashAmount,
      normalizedTelebirrReference: request.telebirrReference,
      status: 'PENDING',
      submittedAt: DateTime.utc(2026),
    );
  }
}
