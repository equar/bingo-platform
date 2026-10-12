import 'dart:math';

import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/core/models/page_result.dart';
import 'package:bingo_app/src/core/widgets/app_status_view.dart';
import 'package:bingo_app/src/core/widgets/section_card.dart';
import 'package:bingo_app/src/features/wallet/data/wallet_repository.dart';
import 'package:bingo_app/src/features/wallet/domain/wallet_models.dart';
import 'package:bingo_app/src/features/wallet/presentation/deposit_dialog.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

final walletBalanceProvider = FutureProvider.autoDispose<WalletBalance>((ref) async {
  return ref.read(walletRepositoryProvider).getBalance();
});

final walletTransactionsProvider = FutureProvider.autoDispose.family<PageResult<WalletTransaction>, int>((ref, page) async {
  return ref.read(walletRepositoryProvider).getTransactions(page: page, size: 10);
});

final walletDepositsProvider = FutureProvider.autoDispose.family<PageResult<DepositClaim>, int>((ref, page) async {
  return ref.read(walletRepositoryProvider).getDeposits(page: page, size: 10);
});

class WalletScreen extends ConsumerStatefulWidget {
  const WalletScreen({super.key});

  @override
  ConsumerState<WalletScreen> createState() => _WalletScreenState();
}

class _WalletScreenState extends ConsumerState<WalletScreen> with SingleTickerProviderStateMixin {
  int _transactionPage = 0;
  int _depositPage = 0;
  PaymentInstructions? _instructions;
  bool _submittingDeposit = false;
  late final TabController _tabController = TabController(length: 2, vsync: this);

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _createDeposit(String currency) async {
    final request = await showDialog<DepositRequest>(
      context: context,
      builder: (_) => DepositDialog(currency: currency),
    );
    if (request == null) return;

    setState(() => _submittingDeposit = true);
    try {
      final response = await ref.read(walletRepositoryProvider).submitDeposit(request, const Uuid().v4());
      if (!mounted) return;
      setState(() => _instructions = response.paymentInstructions);
      ref.invalidate(walletBalanceProvider);
      ref.invalidate(walletTransactionsProvider(_transactionPage));
      ref.invalidate(walletDepositsProvider(_depositPage));
    } finally {
      if (mounted) {
        setState(() => _submittingDeposit = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final balanceAsync = ref.watch(walletBalanceProvider);
    final transactionsAsync = ref.watch(walletTransactionsProvider(_transactionPage));
    final depositsAsync = ref.watch(walletDepositsProvider(_depositPage));

    return Scaffold(
      appBar: AppBar(
        title: Text(localizations.balanceTitle),
        bottom: TabBar(
          controller: _tabController,
          tabs: [Text(localizations.transactionsTitle), Text(localizations.depositsTitle)],
        ),
      ),
      body: balanceAsync.when(
        loading: () => Center(child: Text(localizations.loading)),
        error: (error, stack) => AppStatusView(
          title: localizations.errorGeneric,
          message: error.toString(),
          onAction: () => ref.invalidate(walletBalanceProvider),
        ),
        data: (balance) {
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              SectionCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(localizations.currentBalance, style: Theme.of(context).textTheme.titleLarge),
                    const SizedBox(height: 8),
                    Text('${balance.availableBalance} ${balance.currency}', style: Theme.of(context).textTheme.headlineMedium),
                    const SizedBox(height: 8),
                    Text(localizations.balanceDisclaimer),
                    const SizedBox(height: 12),
                    FilledButton(
                      onPressed: _submittingDeposit ? null : () => _createDeposit(balance.currency),
                      child: _submittingDeposit
                          ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                          : Text(localizations.submitDeposit),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              if (_instructions != null) ...[
                SectionCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(localizations.telebirrInstructionsTitle, style: Theme.of(context).textTheme.titleMedium),
                      const SizedBox(height: 8),
                      Text(_instructions!.providerName),
                      Text(_instructions!.businessAccount),
                      const SizedBox(height: 8),
                      Text(_instructions!.paymentInstructions),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
              ],
              SizedBox(
                height: 500,
                child: TabBarView(
                  controller: _tabController,
                  children: [
                    _TransactionsPane(
                      asyncValue: transactionsAsync,
                      page: _transactionPage,
                      canGoPrevious: _transactionPage > 0,
                      canGoNext: transactionsAsync.asData?.value.last == false,
                      onPrev: () => setState(() => _transactionPage -= 1),
                      onNext: () => setState(() => _transactionPage += 1),
                      localizations: localizations,
                    ),
                    _DepositsPane(
                      asyncValue: depositsAsync,
                      page: _depositPage,
                      canGoPrevious: _depositPage > 0,
                      canGoNext: depositsAsync.asData?.value.last == false,
                      onPrev: () => setState(() => _depositPage -= 1),
                      onNext: () => setState(() => _depositPage += 1),
                      localizations: localizations,
                    ),
                  ],
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _TransactionsPane extends StatelessWidget {
  const _TransactionsPane({
    required this.asyncValue,
    required this.page,
    required this.canGoPrevious,
    required this.canGoNext,
    required this.onPrev,
    required this.onNext,
    required this.localizations,
  });

  final AsyncValue<PageResult<WalletTransaction>> asyncValue;
  final int page;
  final bool canGoPrevious;
  final bool canGoNext;
  final VoidCallback onPrev;
  final VoidCallback onNext;
  final AppLocalizations localizations;

  @override
  Widget build(BuildContext context) {
    return asyncValue.when(
      loading: () => Center(child: Text(localizations.loading)),
      error: (error, stack) => AppStatusView(title: localizations.errorGeneric, message: error.toString()),
      data: (result) {
        if (result.content.isEmpty) {
          return AppStatusView(title: localizations.emptyState, message: localizations.transactionHistoryEmpty);
        }
        return Column(
          children: [
            Expanded(
              child: ListView.builder(
                itemCount: result.content.length,
                itemBuilder: (context, index) {
                  final entry = result.content[index];
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: SectionCard(
                      padding: const EdgeInsets.all(12),
                      child: ListTile(
                        title: Text('${entry.transactionType} • ${entry.amount} ${entry.currency}'),
                        subtitle: Text(entry.note ?? entry.operationId),
                        trailing: Text('${entry.balanceAfter}'),
                      ),
                    ),
                  );
                },
              ),
            ),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('Page ${page + 1}/${max(1, result.totalPages)}'),
                Row(
                  children: [
                    TextButton(onPressed: canGoPrevious ? onPrev : null, child: const Text('Prev')),
                    TextButton(onPressed: canGoNext ? onNext : null, child: const Text('Next')),
                  ],
                ),
              ],
            ),
          ],
        );
      },
    );
  }
}

class _DepositsPane extends StatelessWidget {
  const _DepositsPane({
    required this.asyncValue,
    required this.page,
    required this.canGoPrevious,
    required this.canGoNext,
    required this.onPrev,
    required this.onNext,
    required this.localizations,
  });

  final AsyncValue<PageResult<DepositClaim>> asyncValue;
  final int page;
  final bool canGoPrevious;
  final bool canGoNext;
  final VoidCallback onPrev;
  final VoidCallback onNext;
  final AppLocalizations localizations;

  @override
  Widget build(BuildContext context) {
    return asyncValue.when(
      loading: () => Center(child: Text(localizations.loading)),
      error: (error, stack) => AppStatusView(title: localizations.errorGeneric, message: error.toString()),
      data: (result) {
        if (result.content.isEmpty) {
          return AppStatusView(title: localizations.emptyState, message: localizations.depositHistoryEmpty);
        }
        return Column(
          children: [
            Expanded(
              child: ListView.builder(
                itemCount: result.content.length,
                itemBuilder: (context, index) {
                  final entry = result.content[index];
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: SectionCard(
                      padding: const EdgeInsets.all(12),
                      child: ListTile(
                        title: Text('${entry.submittedCashAmount} ${entry.currency}'),
                        subtitle: Text('${entry.normalizedTelebirrReference}\n${entry.status}'),
                        isThreeLine: true,
                        trailing: Text(entry.paymentInstructions?.providerName ?? localizations.statusPending),
                      ),
                    ),
                  );
                },
              ),
            ),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('Page ${page + 1}/${max(1, result.totalPages)}'),
                Row(
                  children: [
                    TextButton(onPressed: canGoPrevious ? onPrev : null, child: const Text('Prev')),
                    TextButton(onPressed: canGoNext ? onNext : null, child: const Text('Next')),
                  ],
                ),
              ],
            ),
          ],
        );
      },
    );
  }
}
