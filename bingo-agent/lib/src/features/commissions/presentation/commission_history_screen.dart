import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
import 'package:bingo_agent/src/core/formatting/financial_amount.dart';
import 'package:bingo_agent/src/core/network/api_error.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

final commissionHistoryProvider = FutureProvider.autoDispose((ref) async {
  final repository = ref.watch(agentRepositoryProvider);
  final balanceFuture = repository.floatBalance();
  final historyFuture = repository.commissionHistory();
  return (await balanceFuture, await historyFuture);
});

class CommissionHistoryScreen extends ConsumerWidget {
  const CommissionHistoryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final asyncValue = ref.watch(commissionHistoryProvider);
    return asyncValue.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, stack) => _CommissionLoadError(
        message: userFacingApiError(error, l10n) ?? l10n.requestFailed,
        onRetry: () => ref.invalidate(commissionHistoryProvider),
      ),
      data: (value) => RefreshIndicator(
        onRefresh: () async {
          try {
            await ref.refresh(commissionHistoryProvider.future);
          } catch (_) {
            // The provider error state includes a retry action.
          }
        },
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(16),
          children: [
            Text(l10n.historyTitle,
                style: Theme.of(context).textTheme.headlineSmall),
            if (value.$2.isEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 32),
                child: Center(child: Text(l10n.noTransactions)),
              )
            else
              ...value.$2.map((entry) {
                final currency = NumberFormat.currency(
                  locale: Localizations.localeOf(context).languageCode,
                  name: value.$1.currency,
                );
                return ListTile(
                  title: Text(
                    l10n.commissionAmountValue(
                      formatFinancialAmount(entry.commissionAmount, currency),
                    ),
                  ),
                  subtitle: Text(
                    '${_commissionStatus(l10n, entry.status)} · ${DateFormat.yMMMd(
                      Localizations.localeOf(context).languageCode,
                    ).format(entry.createdAt.toLocal())}',
                  ),
                );
              }),
          ],
        ),
      ),
    );
  }

  String _commissionStatus(AppLocalizations l10n, String status) =>
      switch (status.toUpperCase()) {
        'ACCRUED' => l10n.accrued,
        'SETTLED' => l10n.settled,
        _ => status,
      };
}

class _CommissionLoadError extends StatelessWidget {
  const _CommissionLoadError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 12),
            FilledButton(onPressed: onRetry, child: Text(l10n.retry)),
          ],
        ),
      ),
    );
  }
}
