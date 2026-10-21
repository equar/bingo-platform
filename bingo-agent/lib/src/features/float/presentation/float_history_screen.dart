import 'package:bingo_agent/src/core/network/idempotency.dart';
import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
import 'package:bingo_agent/src/features/agents/data/agent_models.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

final floatHistoryProvider = FutureProvider.autoDispose((ref) async {
  final repository = ref.watch(agentRepositoryProvider);
  return (
    await repository.floatBalance(),
    await repository.floatHistory(),
    await repository.topUpRequests()
  );
});

class FloatHistoryScreen extends ConsumerWidget {
  const FloatHistoryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final asyncValue = ref.watch(floatHistoryProvider);
    return asyncValue.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, stack) => Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(l10n.requestFailed),
            const SizedBox(height: 8),
            FilledButton(
                onPressed: () => ref.invalidate(floatHistoryProvider),
                child: Text(l10n.retry)),
          ],
        ),
      ),
      data: (value) {
        final floatBalance = value.$1;
        final history = value.$2;
        final requests = value.$3;
        final currency = NumberFormat.currency(
          locale: Localizations.localeOf(context).languageCode,
          name: floatBalance.currency,
        );
        final number = NumberFormat.decimalPattern(
            Localizations.localeOf(context).languageCode);
        return RefreshIndicator(
          onRefresh: () async => ref.invalidate(floatHistoryProvider),
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(16),
            children: [
              Text(l10n.floatHistoryTitle,
                  style: Theme.of(context).textTheme.headlineSmall),
              Text(l10n.availableBalance(
                number.format(
                    double.tryParse(floatBalance.availableBalance) ?? 0),
                floatBalance.currency,
              )),
              const SizedBox(height: 12),
              FilledButton.icon(
                onPressed: () => _openTopUpRequest(context, ref, floatBalance),
                icon: const Icon(Icons.add),
                label: Text(l10n.requestFloatTopUp),
              ),
              const SizedBox(height: 12),
              ...history.map((entry) => ListTile(
                    title: Text(_transactionLabel(l10n, entry.transactionType)),
                    subtitle: Text(DateFormat.yMMMd(
                            Localizations.localeOf(context).languageCode)
                        .add_jm()
                        .format(entry.createdAt.toLocal())),
                    trailing: Text(
                        currency.format(double.tryParse(entry.amount) ?? 0)),
                  )),
              const SizedBox(height: 12),
              Text(l10n.topUpRequests,
                  style: Theme.of(context).textTheme.titleMedium),
              ...requests.map((entry) => ListTile(
                    title: Text(
                        currency.format(double.tryParse(entry.amount) ?? 0)),
                    subtitle: Text(
                        '${_statusLabel(l10n, entry.status)} · ${DateFormat.yMMMd(
                      Localizations.localeOf(context).languageCode,
                    ).format(entry.createdAt.toLocal())}'),
                  )),
            ],
          ),
        );
      },
    );
  }

  Future<void> _openTopUpRequest(
    BuildContext context,
    WidgetRef ref,
    AgentFloatBalance balance,
  ) async {
    final l10n = AppLocalizations.of(context)!;
    final submitted = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (_) => _TopUpRequestDialog(currency: balance.currency),
    );
    if (!context.mounted || submitted != true) return;
    ref.invalidate(floatHistoryProvider);
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(l10n.topUpRequestSubmitted)));
  }

  String _transactionLabel(AppLocalizations l10n, String type) =>
      switch (type) {
        'OPENING_BALANCE' => l10n.openingBalance,
        'TOP_UP' => l10n.floatTopUp,
        'TICKET_SALE_DEBIT' => l10n.ticketSale,
        'PLAYER_WALLET_TRANSFER_DEBIT' => l10n.playerWalletTopUp,
        'PAYOUT_DISBURSEMENT_DEBIT' => l10n.payoutDisbursement,
        'PAYOUT_CLEARING_CREDIT' => l10n.payoutClearing,
        'COMMISSION_CREDIT' => l10n.commissionCredit,
        'MANUAL_ADJUSTMENT' => l10n.manualAdjustment,
        _ => l10n.otherTransaction,
      };

  String _statusLabel(AppLocalizations l10n, String status) =>
      switch (status.toUpperCase()) {
        'PENDING' => l10n.pending,
        'APPROVED' => l10n.approved,
        'REJECTED' => l10n.rejected,
        _ => status,
      };
}

class _TopUpRequestDialog extends ConsumerStatefulWidget {
  const _TopUpRequestDialog({required this.currency});

  final String currency;

  @override
  ConsumerState<_TopUpRequestDialog> createState() =>
      _TopUpRequestDialogState();
}

class _TopUpRequestDialogState extends ConsumerState<_TopUpRequestDialog> {
  final _formKey = GlobalKey<FormState>();
  final _amountController = TextEditingController();
  final _noteController = TextEditingController();
  bool _busy = false;
  String? _error;
  String? _idempotencyKey;
  String? _attemptAmount;
  String? _attemptNote;

  @override
  void dispose() {
    _amountController.dispose();
    _noteController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final l10n = AppLocalizations.of(context)!;
    if (_idempotencyKey == null && !_formKey.currentState!.validate()) return;
    if (_idempotencyKey == null) {
      final amount = _amountController.text.trim();
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(l10n.confirmTopUpTitle),
          content: Text(l10n.confirmTopUpBody(amount, widget.currency)),
          actions: [
            TextButton(
                onPressed: () => Navigator.of(context).pop(false),
                child: Text(l10n.cancel)),
            FilledButton(
                onPressed: () => Navigator.of(context).pop(true),
                child: Text(l10n.confirm)),
          ],
        ),
      );
      if (!mounted || confirmed != true) return;
      _idempotencyKey = IdempotencyKeyStore.newKey();
      _attemptAmount = amount;
      _attemptNote = _noteController.text.trim();
    }

    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(agentRepositoryProvider).submitTopUp({
        'amount': num.parse(_attemptAmount!),
        'currency': widget.currency,
        'note': _attemptNote!.isEmpty ? null : _attemptNote,
      }, _idempotencyKey!);
      if (mounted) Navigator.of(context).pop(true);
    } on DioException catch (error) {
      if (error.response?.statusCode case final status?
          when status >= 400 && status < 500) {
        _idempotencyKey = null;
        _attemptAmount = null;
        _attemptNote = null;
      }
      if (mounted) {
        setState(() {
          _error = l10n.topUpRequestFailed;
          _busy = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _error = l10n.topUpRequestFailed;
          _busy = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final attempted = _idempotencyKey != null;
    return PopScope(
      canPop: !attempted,
      child: AlertDialog(
        title: Text(l10n.requestFloatTopUp),
        content: Form(
          key: _formKey,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextFormField(
                  controller: _amountController,
                  readOnly: attempted || _busy,
                  keyboardType:
                      const TextInputType.numberWithOptions(decimal: true),
                  decoration: InputDecoration(
                      labelText: l10n.topUpAmount, suffixText: widget.currency),
                  validator: (value) {
                    final amount = value?.trim() ?? '';
                    if (!RegExp(r'^\d{1,17}(\.\d{1,2})?$').hasMatch(amount) ||
                        (double.tryParse(amount) ?? 0) <= 0) {
                      return l10n.positiveAmountRequired;
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _noteController,
                  readOnly: attempted || _busy,
                  maxLength: 500,
                  decoration: InputDecoration(
                    labelText: '${l10n.topUpNote} (${l10n.optional})',
                  ),
                ),
                if (_error != null) ...[
                  const SizedBox(height: 8),
                  Text(_error!,
                      style: TextStyle(
                          color: Theme.of(context).colorScheme.error)),
                ],
              ],
            ),
          ),
        ),
        actions: [
          if (!attempted)
            TextButton(
                onPressed:
                    _busy ? null : () => Navigator.of(context).pop(false),
                child: Text(l10n.cancel)),
          FilledButton(
            onPressed: _busy ? null : _submit,
            child: _busy
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2))
                : Text(attempted ? l10n.retry : l10n.submitRequest),
          ),
        ],
      ),
    );
  }
}
