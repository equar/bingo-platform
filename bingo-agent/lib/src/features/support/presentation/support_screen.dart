import 'package:bingo_agent/src/features/support/data/support_repository.dart';
import 'package:bingo_agent/src/core/network/api_error.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final supportTicketsProvider = FutureProvider.autoDispose(
    (ref) => ref.watch(supportRepositoryProvider).myTickets());

class SupportScreen extends ConsumerWidget {
  const SupportScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final asyncValue = ref.watch(supportTicketsProvider);
    return asyncValue.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, stack) => _SupportLoadError(
        message: userFacingApiError(error, l10n) ?? l10n.requestFailed,
        onRetry: () => ref.invalidate(supportTicketsProvider),
      ),
      data: (page) => RefreshIndicator(
        onRefresh: () async {
          try {
            await ref.refresh(supportTicketsProvider.future);
          } catch (_) {
            // The provider error state includes a retry action.
          }
        },
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(16),
          children: [
            Text(l10n.supportTickets,
                style: Theme.of(context).textTheme.headlineSmall),
            const SizedBox(height: 12),
            if (page.content.isEmpty)
              Center(child: Text(l10n.noSupportTickets))
            else
              ...page.content.map((ticket) => ListTile(
                  title: Text(ticket.ticketNumber),
                  subtitle: Text(ticket.subject))),
          ],
        ),
      ),
    );
  }
}

class _SupportLoadError extends StatelessWidget {
  const _SupportLoadError({required this.message, required this.onRetry});

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
