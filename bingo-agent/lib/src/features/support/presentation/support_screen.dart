import 'package:bingo_agent/src/features/support/data/support_repository.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final supportTicketsProvider = FutureProvider.autoDispose((ref) => ref.watch(supportRepositoryProvider).myTickets());

class SupportScreen extends ConsumerWidget {
  const SupportScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncValue = ref.watch(supportTicketsProvider);
    return asyncValue.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, stack) => Center(child: Text(error.toString())),
      data: (page) => ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text('Support tickets', style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 12),
          ...page.content.map((ticket) => ListTile(title: Text(ticket.ticketNumber), subtitle: Text(ticket.subject))),
        ],
      ),
    );
  }
}