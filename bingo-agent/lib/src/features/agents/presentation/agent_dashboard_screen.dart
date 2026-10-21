import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

final agentDashboardProvider = FutureProvider.autoDispose((ref) async {
  final repository = ref.watch(agentRepositoryProvider);
  return (await repository.dashboard(), await repository.floatBalance());
});

class AgentDashboardScreen extends ConsumerWidget {
  const AgentDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final asyncValue = ref.watch(agentDashboardProvider);
    return asyncValue.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, stack) => Center(child: Text(l10n.requestFailed)),
      data: (value) {
        final dashboard = value.$1;
        final floatBalance = value.$2;
        final locale = Localizations.localeOf(context).languageCode;
        final currency =
            NumberFormat.currency(locale: locale, name: floatBalance.currency);
        final count = NumberFormat.decimalPattern(locale);
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text(l10n.dashboardTitle,
                style: Theme.of(context).textTheme.headlineMedium),
            const SizedBox(height: 16),
            Wrap(
              spacing: 12,
              runSpacing: 12,
              children: [
                _Metric(
                    label: l10n.availableFloat,
                    value: currency.format(
                        double.tryParse(dashboard.availableFloat) ?? 0)),
                _Metric(
                    label: l10n.clearingFloat,
                    value: currency
                        .format(double.tryParse(dashboard.clearingFloat) ?? 0)),
                _Metric(
                    label: l10n.ticketsSold,
                    value: count.format(dashboard.ticketsSold)),
                _Metric(
                    label: l10n.commissions,
                    value: count.format(dashboard.commissionAccruals)),
                _Metric(
                    label: l10n.pendingTopUps,
                    value: count.format(dashboard.topUpRequests)),
              ],
            ),
          ],
        );
      },
    );
  }
}

class _Metric extends StatelessWidget {
  const _Metric({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 220,
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(label),
            const SizedBox(height: 8),
            Text(value, style: Theme.of(context).textTheme.headlineSmall)
          ]),
        ),
      ),
    );
  }
}
