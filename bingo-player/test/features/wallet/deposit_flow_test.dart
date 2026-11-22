import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/core/models/page_result.dart';
import 'package:bingo_player/src/core/network/api_error.dart';
import 'package:bingo_player/src/features/wallet/data/wallet_api.dart';
import 'package:bingo_player/src/features/wallet/data/wallet_repository.dart';
import 'package:bingo_player/src/features/wallet/domain/wallet_models.dart';
import 'package:bingo_player/src/features/wallet/presentation/wallet_screen.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('validates before API call and retains key for unknown retry', (
    tester,
  ) async {
    final repository = _DepositRepository();
    await tester.pumpWidget(_app(repository));
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
      '---',
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
    await tester.enterText(
      find.byKey(const ValueKey('deposit-reference-field')),
      'TBR-123',
    );
    await tester.tap(
      find.byKey(const ValueKey('wallet-confirm-deposit-button')),
    );
    await tester.pumpAndSettle();
    expect(repository.calls, 1);
    expect(
      find.textContaining('couldn’t confirm the transaction status'),
      findsOneWidget,
    );

    await tester.tap(
      find.byKey(const ValueKey('wallet-confirm-deposit-button')),
    );
    await tester.pumpAndSettle();
    expect(repository.calls, 2);
    expect(repository.keys.first, isNotEmpty);
    expect(repository.keys[1], repository.keys[0]);
    expect(repository.requests[0].submittedCashAmount, 5.25);
    expect(repository.requests[0].telebirrReference, 'TBR-123');
  });

  testWidgets('deposit field validation is localized in Amharic', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(_DepositRepository(), locale: const Locale('am')),
    );
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('wallet-open-deposit-form-button')),
    );
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('deposit-amount-field')),
      '5.123',
    );
    await tester.enterText(
      find.byKey(const ValueKey('deposit-reference-field')),
      'valid12',
    );
    await tester.tap(
      find.byKey(const ValueKey('wallet-confirm-deposit-button')),
    );
    await tester.pumpAndSettle();
    final l10n = AppLocalizations.of(
      tester.element(find.byType(WalletScreen)),
    )!;
    expect(find.text(l10n.depositAmountPrecision), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}

Widget _app(
  _DepositRepository repository, {
  Locale locale = const Locale('en'),
}) => ProviderScope(
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
        minimumDepositAmount: '1.00',
        maximumDepositAmount: '1000.00',
      ),
    ),
    walletTransactionsProvider.overrideWith(
      (ref, page) async => _emptyPage<WalletTransaction>(page),
    ),
    walletDepositsProvider.overrideWith(
      (ref, page) async => _emptyPage<DepositClaim>(page),
    ),
  ],
  child: MaterialApp(
    locale: locale,
    localizationsDelegates: const [
      AppLocalizations.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: AppLocalizations.supportedLocales,
    home: const WalletScreen(initialTab: 1),
  ),
);

PageResult<T> _emptyPage<T>(int page) => PageResult(
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
  final keys = <String>[];
  final requests = <DepositRequest>[];

  @override
  Future<DepositClaim> submitDeposit(DepositRequest request, String key) async {
    calls++;
    requests.add(request);
    keys.add(key);
    if (calls == 1) {
      throw ApiError(code: 'NETWORK_ERROR', outcomeUnknown: true);
    }
    return DepositClaim(
      id: 4,
      operationId: 'deposit-4',
      playerId: 1,
      submittedCashAmount: 5.25,
      currency: 'ETB',
      conversionRateSnapshot: 1,
      calculatedCreditAmount: 5.25,
      normalizedTelebirrReference: 'TBR123',
      status: 'PENDING',
      submittedAt: DateTime.utc(2026),
      paymentInstructions: PaymentInstructions(
        providerName: 'Telebirr',
        businessAccount: 'account',
        paymentInstructions: 'instructions',
        currency: 'ETB',
      ),
    );
  }
}
