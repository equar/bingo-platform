import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
import 'package:bingo_agent/src/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final agentDashboardProvider = FutureProvider.autoDispose((ref) async {
  final repository = ref.watch(agentRepositoryProvider);
  return (await repository.dashboard(), await repository.floatBalance());
});

class AgentDashboardScreen extends ConsumerWidget {
  const AgentDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final asyncValue = ref.watch(agentDashboardProvider);
    return asyncValue.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, stack) => Center(child: Text(error.toString())),
      data: (value) {
        final dashboard = value.$1;
        final floatBalance = value.$2;
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text(l10n.dashboardTitle, style: Theme.of(context).textTheme.headlineMedium),
            const SizedBox(height: 16),
            Wrap(
              spacing: 12,
              runSpacing: 12,
              children: [
                _Metric(label: 'Available float', value: '${dashboard.availableFloat} ${floatBalance.currency}'),
                _Metric(label: 'Clearing float', value: '${dashboard.clearingFloat} ${floatBalance.currency}'),
                _Metric(label: 'Tickets sold', value: dashboard.ticketsSold.toString()),
                _Metric(label: 'Commissions', value: dashboard.commissionAccruals.toString()),
                _Metric(label: 'Pending top-ups', value: dashboard.topUpRequests.toString()),
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
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(label), const SizedBox(height: 8), Text(value, style: Theme.of(context).textTheme.headlineSmall)]),
        ),
      ),
    );
  }
}