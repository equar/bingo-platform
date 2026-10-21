import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
import 'package:bingo_agent/src/features/agents/data/agent_models.dart';
import 'package:bingo_agent/src/features/history/data/financial_history_entry.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

final financialHistoryProvider = FutureProvider.autoDispose((ref) async {
  final repository = ref.watch(agentRepositoryProvider);
  final results = await Future.wait<dynamic>([
    repository.floatBalance(),
    repository.floatHistory(),
    repository.commissionHistory(),
    repository.topUpRequests(),
  ]);
  return FinancialHistoryEntry.combine(
    currency: (results[0] as AgentFloatBalance).currency,
    floatTransactions: results[1] as List<AgentFloatTransaction>,
    commissions: results[2] as List<CommissionAccrual>,
    topUpRequests: results[3] as List<FloatTopUpRequest>,
  );
});

class FinancialHistoryScreen extends ConsumerStatefulWidget {
  const FinancialHistoryScreen({super.key});

  @override
  ConsumerState<FinancialHistoryScreen> createState() =>
      _FinancialHistoryScreenState();
}

class _FinancialHistoryScreenState
    extends ConsumerState<FinancialHistoryScreen> {
  FinancialActivityType? _type;
  String? _status;
  DateTimeRange? _dateRange;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final asyncValue = ref.watch(financialHistoryProvider);
    return asyncValue.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, stack) => _HistoryError(
          onRetry: () => ref.invalidate(financialHistoryProvider)),
      data: (entries) {
        final statuses = entries
            .map((entry) => entry.status.toUpperCase())
            .toSet()
            .toList()
          ..sort();
        final filtered = entries.where(_matchesFilters).toList();
        return RefreshIndicator(
          onRefresh: () async => ref.invalidate(financialHistoryProvider),
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(16),
            children: [
              Text(l10n.financialHistoryTitle,
                  style: Theme.of(context).textTheme.headlineSmall),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  SizedBox(
                    width: 220,
                    child: DropdownButtonFormField<FinancialActivityType?>(
                      initialValue: _type,
                      isExpanded: true,
                      decoration: InputDecoration(labelText: l10n.filterByType),
                      items: [
                        DropdownMenuItem(
                          value: null,
                          child: Text(l10n.allTypes,
                              maxLines: 1, overflow: TextOverflow.ellipsis),
                        ),
                        for (final type in FinancialActivityType.values)
                          DropdownMenuItem(
                            value: type,
                            child: Text(_typeLabel(l10n, type),
                                maxLines: 1, overflow: TextOverflow.ellipsis),
                          ),
                      ],
                      onChanged: (value) => setState(() => _type = value),
                    ),
                  ),
                  SizedBox(
                    width: 180,
                    child: DropdownButtonFormField<String?>(
                      initialValue: _status,
                      isExpanded: true,
                      decoration:
                          InputDecoration(labelText: l10n.filterByStatus),
                      items: [
                        DropdownMenuItem(
                          value: null,
                          child: Text(l10n.allStatuses,
                              maxLines: 1, overflow: TextOverflow.ellipsis),
                        ),
                        for (final status in statuses)
                          DropdownMenuItem(
                            value: status,
                            child: Text(_statusLabel(l10n, status),
                                maxLines: 1, overflow: TextOverflow.ellipsis),
                          ),
                      ],
                      onChanged: (value) => setState(() => _status = value),
                    ),
                  ),
                  OutlinedButton.icon(
                    onPressed: () => _selectDateRange(l10n),
                    icon: const Icon(Icons.date_range),
                    label: Text(_dateLabel(l10n)),
                  ),
                  if (_type != null || _status != null || _dateRange != null)
                    TextButton(
                        onPressed: () => setState(() {
                              _type = null;
                              _status = null;
                              _dateRange = null;
                            }),
                        child: Text(l10n.clearFilters)),
                ],
              ),
              const SizedBox(height: 12),
              if (filtered.isEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 32),
                  child: Center(child: Text(l10n.noTransactions)),
                )
              else
                ...filtered.map((entry) => _entryTile(context, l10n, entry)),
            ],
          ),
        );
      },
    );
  }

  bool _matchesFilters(FinancialHistoryEntry entry) {
    if (_type != null && entry.type != _type) return false;
    if (_status != null && entry.status.toUpperCase() != _status) return false;
    if (_dateRange != null) {
      final date = entry.createdAt;
      final endExclusive = DateTime(
          _dateRange!.end.year, _dateRange!.end.month, _dateRange!.end.day + 1);
      if (date.isBefore(_dateRange!.start) || !date.isBefore(endExclusive)) {
        return false;
      }
    }
    return true;
  }

  Future<void> _selectDateRange(AppLocalizations l10n) async {
    final locale = Localizations.localeOf(context).languageCode;
    final selected = await showDateRangePicker(
      context: context,
      firstDate: DateTime(2000),
      lastDate: DateTime.now().add(const Duration(days: 365)),
      initialDateRange: _dateRange,
      helpText: l10n.filterByDate,
      cancelText: l10n.cancel,
      confirmText: l10n.confirm,
      locale: Locale(locale),
    );
    if (mounted && selected != null) setState(() => _dateRange = selected);
  }

  String _dateLabel(AppLocalizations l10n) {
    if (_dateRange == null) return l10n.anyDate;
    final formatter =
        DateFormat.yMMMd(Localizations.localeOf(context).languageCode);
    return '${formatter.format(_dateRange!.start)} – ${formatter.format(_dateRange!.end)}';
  }

  Widget _entryTile(BuildContext context, AppLocalizations l10n,
      FinancialHistoryEntry entry) {
    final locale = Localizations.localeOf(context).languageCode;
    final currency =
        NumberFormat.currency(locale: locale, name: entry.currency);
    final date =
        DateFormat.yMMMd(locale).add_jm().format(entry.createdAt.toLocal());
    return Card(
      child: ListTile(
        title: Text(_typeLabel(l10n, entry.type)),
        subtitle: Text('$date · ${_statusLabel(l10n, entry.status)}'),
        trailing: Text(currency.format(double.tryParse(entry.amount) ?? 0)),
        onTap: () => _showDetails(l10n, entry, currency, date),
      ),
    );
  }

  Future<void> _showDetails(
    AppLocalizations l10n,
    FinancialHistoryEntry entry,
    NumberFormat currency,
    String date,
  ) async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (context) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(l10n.transactionDetails,
                  style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 16),
              _detailLine(
                  l10n.transactionTypeLabel, _typeLabel(l10n, entry.type)),
              _detailLine(l10n.transactionAmount,
                  currency.format(double.tryParse(entry.amount) ?? 0)),
              _detailLine(l10n.transactionDate, date),
              _detailLine(
                  l10n.transactionStatus, _statusLabel(l10n, entry.status)),
              _detailLine(l10n.transactionReference, entry.reference),
              _detailLine(
                  l10n.counterparty, entry.counterparty ?? l10n.noCounterparty),
              if (entry.relatedEntity != null)
                _detailLine(l10n.relatedRecord, entry.relatedEntity!),
              if (entry.note?.isNotEmpty == true)
                _detailLine(l10n.topUpNote, entry.note!),
              const SizedBox(height: 12),
              FilledButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: Text(l10n.close)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _detailLine(String label, String value) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
                child: Text(label,
                    style: const TextStyle(fontWeight: FontWeight.w600))),
            const SizedBox(width: 12),
            Expanded(child: Text(value, textAlign: TextAlign.end)),
          ],
        ),
      );

  String _typeLabel(AppLocalizations l10n, FinancialActivityType type) =>
      switch (type) {
        FinancialActivityType.openingBalance => l10n.openingBalance,
        FinancialActivityType.floatTopUp => l10n.floatTopUp,
        FinancialActivityType.ticketSale => l10n.ticketSale,
        FinancialActivityType.playerWalletTopUp => l10n.playerWalletTopUp,
        FinancialActivityType.payoutDisbursement => l10n.payoutDisbursement,
        FinancialActivityType.payoutClearing => l10n.payoutClearing,
        FinancialActivityType.commissionCredit => l10n.commissionCredit,
        FinancialActivityType.manualAdjustment => l10n.manualAdjustment,
        FinancialActivityType.commissionAccrual => l10n.commissionAccrual,
        FinancialActivityType.floatTopUpRequest => l10n.floatTopUpRequest,
        FinancialActivityType.other => l10n.otherTransaction,
      };

  String _statusLabel(AppLocalizations l10n, String status) =>
      switch (status.toUpperCase()) {
        'PENDING' => l10n.pending,
        'APPROVED' => l10n.approved,
        'REJECTED' => l10n.rejected,
        'ACCRUED' => l10n.accrued,
        'SETTLED' => l10n.settled,
        'COMPLETED' => l10n.completed,
        _ => status,
      };
}

class _HistoryError extends StatelessWidget {
  const _HistoryError({required this.onRetry});

  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(l10n.requestFailed),
          const SizedBox(height: 8),
          FilledButton(onPressed: onRetry, child: Text(l10n.retry)),
        ],
      ),
    );
  }
}
