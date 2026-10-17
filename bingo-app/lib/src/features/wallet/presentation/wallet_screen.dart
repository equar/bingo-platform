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
  late final TabController _tabController = TabController(length: 3, vsync: this);

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
      _refreshWalletData();
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.toString())));
    } finally {
      if (mounted) {
        setState(() => _submittingDeposit = false);
      }
    }
  }

  void _refreshWalletData() {
    ref.invalidate(walletBalanceProvider);
    ref.invalidate(walletTransactionsProvider(_transactionPage));
    ref.invalidate(walletDepositsProvider(_depositPage));
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
          tabs: const [
            Tab(text: 'Transactions'),
            Tab(text: 'Deposits'),
            Tab(text: 'Transfers'),
          ],
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
                    Text('${balance.totalBalance} ${balance.currency}', style: Theme.of(context).textTheme.headlineMedium),
                    const SizedBox(height: 12),
                    Text('Available: ${balance.availableBalance} ${balance.currency}'),
                    Text('Promotional: ${balance.promotionalBalance} ${balance.currency}'),
                    Text(localizations.balanceDisclaimer),
                    const SizedBox(height: 12),
                    FilledButton(
                      key: const ValueKey('wallet-submit-deposit-button'),
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
                height: 560,
                child: TabBarView(
                  controller: _tabController,
                  children: [
                    _TransactionsPane(
                      asyncValue: transactionsAsync,
                      page: _transactionPage,
                      canGoPrevious: _transactionPage > 0,
                      canGoNext: transactionsAsync.asData?.value.content.length == 10,
                      onPrev: () => setState(() => _transactionPage -= 1),
                      onNext: () => setState(() => _transactionPage += 1),
                      localizations: localizations,
                    ),
                    _DepositsPane(
                      asyncValue: depositsAsync,
                      page: _depositPage,
                      canGoPrevious: _depositPage > 0,
                      canGoNext: depositsAsync.asData?.value.content.length == 10,
                      onPrev: () => setState(() => _depositPage -= 1),
                      onNext: () => setState(() => _depositPage += 1),
                      localizations: localizations,
                    ),
                    _TransfersPane(currency: balance.currency, onCompleted: _refreshWalletData),
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

class _TransfersPane extends ConsumerStatefulWidget {
  const _TransfersPane({required this.currency, required this.onCompleted});

  final String currency;
  final VoidCallback onCompleted;

  @override
  ConsumerState<_TransfersPane> createState() => _TransfersPaneState();
}

class _TransfersPaneState extends ConsumerState<_TransfersPane> {
  final TextEditingController _phoneController = TextEditingController();
  final TextEditingController _amountController = TextEditingController();
  final TextEditingController _noteController = TextEditingController();
  bool _lookingUp = false;
  bool _submitting = false;
  WalletRecipientLookup? _recipient;
  String? _errorMessage;

  @override
  void dispose() {
    _phoneController.dispose();
    _amountController.dispose();
    _noteController.dispose();
    super.dispose();
  }

  Future<void> _lookupRecipient() async {
    final phone = _phoneController.text.trim();
    if (phone.isEmpty) {
      setState(() => _errorMessage = 'Enter a phone number to look up a recipient.');
      return;
    }

    setState(() {
      _lookingUp = true;
      _errorMessage = null;
      _recipient = null;
    });

    try {
      final lookup = await ref.read(walletRepositoryProvider).lookupRecipient(phone);
      if (!mounted) return;
      setState(() => _recipient = lookup);
    } catch (error) {
      if (!mounted) return;
      setState(() => _errorMessage = error.toString());
    } finally {
      if (mounted) {
        setState(() => _lookingUp = false);
      }
    }
  }

  Future<void> _submitTransfer() async {
    final amount = num.tryParse(_amountController.text.trim());
    if (_recipient == null) {
      setState(() => _errorMessage = 'Look up the recipient first.');
      return;
    }
    if (amount == null || amount <= 0) {
      setState(() => _errorMessage = 'Enter a valid transfer amount.');
      return;
    }

    setState(() {
      _submitting = true;
      _errorMessage = null;
    });

    try {
      await ref.read(walletRepositoryProvider).submitTransfer(
            WalletTransferRequest(
              recipientPhoneNumber: _recipient!.normalizedPhone,
              amount: amount,
              currency: widget.currency,
              note: _noteController.text.trim().isEmpty ? null : _noteController.text.trim(),
            ),
            const Uuid().v4(),
          );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Transfer submitted.')));
      setState(() {
        _recipient = null;
        _phoneController.clear();
        _amountController.clear();
        _noteController.clear();
      });
      widget.onCompleted();
    } catch (error) {
      if (!mounted) return;
      setState(() => _errorMessage = error.toString());
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      child: Column(
        children: [
          SectionCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Send money', style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 12),
                TextField(
                  controller: _phoneController,
                  keyboardType: TextInputType.phone,
                  decoration: const InputDecoration(labelText: 'Recipient phone number'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _amountController,
                  keyboardType: TextInputType.number,
                  decoration: InputDecoration(labelText: 'Amount (${widget.currency})'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _noteController,
                  decoration: const InputDecoration(labelText: 'Note (optional)'),
                  maxLines: 2,
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    OutlinedButton(
                      onPressed: _lookingUp ? null : _lookupRecipient,
                      child: _lookingUp ? const Text('Looking up...') : const Text('Lookup recipient'),
                    ),
                    const SizedBox(width: 12),
                    FilledButton(
                      onPressed: _submitting ? null : _submitTransfer,
                      child: _submitting ? const Text('Sending...') : const Text('Send transfer'),
                    ),
                  ],
                ),
                if (_errorMessage != null) ...[
                  const SizedBox(height: 12),
                  Text(_errorMessage!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                ],
              ],
            ),
          ),
          const SizedBox(height: 16),
          SectionCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Recipient preview', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 8),
                if (_recipient == null)
                  const Text('Look up a phone number to confirm the recipient before sending.')
                else ...[
                  Text(_recipient!.displayName),
                  Text(_recipient!.normalizedPhone),
                ],
              ],
            ),
          ),
        ],
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
                  final bucketSummary = entry.transferableBalanceAfter != null || entry.promotionalBalanceAfter != null
                      ? '\nTransferable: ${entry.transferableBalanceAfter ?? entry.balanceAfter} · Promo: ${entry.promotionalBalanceAfter ?? 0}'
                      : '';
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: SectionCard(
                      padding: const EdgeInsets.all(12),
                      child: ListTile(
                        title: Text('${entry.transactionType} • ${entry.amount} ${entry.currency}'),
                        subtitle: Text('${entry.note ?? entry.operationId}$bucketSummary'),
                        isThreeLine: bucketSummary.isNotEmpty,
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