import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
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
      error: (error, stack) => Center(child: Text(l10n.requestFailed)),
      data: (value) => ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(l10n.historyTitle,
              style: Theme.of(context).textTheme.headlineSmall),
          ...value.$2.map((entry) => ListTile(
                title: Text(l10n.commissionAmountValue(
                  NumberFormat.currency(
                    locale: Localizations.localeOf(context).languageCode,
                    name: value.$1.currency,
                  ).format(double.tryParse(entry.commissionAmount) ?? 0),
                )),
                subtitle: Text(
                    '${_commissionStatus(l10n, entry.status)} · ${DateFormat.yMMMd(
                  Localizations.localeOf(context).languageCode,
                ).format(entry.createdAt.toLocal())}'),
              )),
        ],
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
