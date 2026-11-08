import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
import 'package:bingo_agent/src/core/network/api_error.dart';
import 'package:bingo_agent/src/core/formatting/financial_amount.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

final agentDashboardProvider = FutureProvider.autoDispose((ref) async {
  final repository = ref.watch(agentRepositoryProvider);
  final dashboardFuture = repository.dashboard();
  final floatFuture = repository.floatBalance();
  return (await dashboardFuture, await floatFuture);
});

class AgentDashboardScreen extends ConsumerWidget {
  const AgentDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final asyncValue = ref.watch(agentDashboardProvider);
    return asyncValue.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, stack) => _DashboardError(
        message: userFacingApiError(error, l10n) ?? l10n.requestFailed,
        onRetry: () => ref.invalidate(agentDashboardProvider),
      ),
      data: (value) {
        final dashboard = value.$1;
        final floatBalance = value.$2;
        final locale = Localizations.localeOf(context).languageCode;
        final currency =
            NumberFormat.currency(locale: locale, name: floatBalance.currency);
        final count = NumberFormat.decimalPattern(locale);
        return RefreshIndicator(
          onRefresh: () async {
            try {
              await ref.refresh(agentDashboardProvider.future);
            } catch (_) {
              // The provider error state includes a retry action.
            }
          },
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(16),
            children: [
              Wrap(
                spacing: 12,
                runSpacing: 12,
                children: [
                  _Metric(
                    label: l10n.availableFloat,
                    value: formatFinancialAmount(
                      dashboard.availableFloat,
                      currency,
                    ),
                  ),
                  _Metric(
                    label: l10n.clearingFloat,
                    value: formatFinancialAmount(
                      dashboard.clearingFloat,
                      currency,
                    ),
                  ),
                  _Metric(
                    label: l10n.ticketsSold,
                    value: count.format(dashboard.ticketsSold),
                  ),
                  _Metric(
                    label: l10n.commissions,
                    value: count.format(dashboard.commissionAccruals),
                  ),
                  _Metric(
                    label: l10n.topUpRequests,
                    value: count.format(dashboard.topUpRequests),
                  ),
                ],
              ),
            ],
          ),
        );
      },
    );
  }
}

class _DashboardError extends StatelessWidget {
  const _DashboardError({required this.message, required this.onRetry});

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
