import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final floatHistoryProvider = FutureProvider.autoDispose((ref) async {
  final repository = ref.watch(agentRepositoryProvider);
  return (await repository.floatBalance(), await repository.floatHistory(), await repository.topUpRequests());
});

class FloatHistoryScreen extends ConsumerWidget {
  const FloatHistoryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncValue = ref.watch(floatHistoryProvider);
    return asyncValue.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, stack) => Center(child: Text(error.toString())),
      data: (value) {
        final floatBalance = value.$1;
        final history = value.$2;
        final requests = value.$3;
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text('Float history', style: Theme.of(context).textTheme.headlineSmall),
            Text('${floatBalance.availableBalance} ${floatBalance.currency} available'),
            const SizedBox(height: 12),
            ...history.map((entry) => ListTile(title: Text(entry.transactionType), subtitle: Text(entry.amount))),
            const SizedBox(height: 12),
            Text('Top-up requests', style: Theme.of(context).textTheme.titleMedium),
            ...requests.map((entry) => ListTile(title: Text(entry.amount), subtitle: Text(entry.status))),
          ],
        );
      },
    );
  }
}