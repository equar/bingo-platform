import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final commissionHistoryProvider = FutureProvider.autoDispose((ref) => ref.watch(agentRepositoryProvider).commissionHistory());

class CommissionHistoryScreen extends ConsumerWidget {
  const CommissionHistoryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncValue = ref.watch(commissionHistoryProvider);
    return asyncValue.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, stack) => Center(child: Text(error.toString())),
      data: (history) => ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text('Transaction/commission history', style: Theme.of(context).textTheme.headlineSmall),
          ...history.map((entry) => ListTile(title: Text(entry.commissionAmount), subtitle: Text(entry.status))),
        ],
      ),
    );
  }
}