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
            ref.invalidate(agentDashboardProvider);
            try {
              await ref.read(agentDashboardProvider.future);
            } catch (_) {
              // The provider error state includes a retry action.
            }
          },
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(16),
            children: [
              _OperationsBanner(
                availableFloat: formatFinancialAmount(
                  dashboard.availableFloat,
                  currency,
                ),
                title: l10n.agentOperations,
              ),
              const SizedBox(height: 16),
              LayoutBuilder(
                builder: (context, constraints) {
                  final columns = constraints.maxWidth >= 680 ? 2 : 1;
                  final gap = 12.0;
                  final cardWidth =
                      (constraints.maxWidth - gap * (columns - 1)) / columns;
                  return Wrap(
                    spacing: gap,
                    runSpacing: gap,
                    children: [
                      _Metric(
                        label: l10n.availableFloat,
                        value: formatFinancialAmount(
                          dashboard.availableFloat,
                          currency,
                        ),
                        icon: Icons.account_balance_wallet_outlined,
                        width: cardWidth,
                      ),
                      _Metric(
                        label: l10n.clearingFloat,
                        value: formatFinancialAmount(
                          dashboard.clearingFloat,
                          currency,
                        ),
                        icon: Icons.sync_alt_rounded,
                        width: cardWidth,
                      ),
                      _Metric(
                        label: l10n.ticketsSold,
                        value: count.format(dashboard.ticketsSold),
                        icon: Icons.confirmation_number_outlined,
                        width: cardWidth,
                      ),
                      _Metric(
                        label: l10n.commissions,
                        value: count.format(dashboard.commissionAccruals),
                        icon: Icons.trending_up_rounded,
                        width: cardWidth,
                      ),
                      _Metric(
                        label: l10n.topUpRequests,
                        value: count.format(dashboard.topUpRequests),
                        icon: Icons.add_card_rounded,
                        width: cardWidth,
                      ),
                    ],
                  );
                },
              ),
            ],
          ),
        );
      },
    );
  }
}

class _OperationsBanner extends StatelessWidget {
  const _OperationsBanner({required this.availableFloat, required this.title});

  final String availableFloat;
  final String title;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: const Color(0xFF102B46),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: const Color(0xFFC7362F).withValues(alpha: .65)),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: const Color(0xFF006B45),
              borderRadius: BorderRadius.circular(14),
            ),
            child: const Icon(Icons.verified_user_outlined, color: Colors.white),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title.toUpperCase(),
                  style: Theme.of(context).textTheme.labelLarge?.copyWith(
                    color: const Color(0xFFFFD8D5),
                    fontWeight: FontWeight.w800,
                    letterSpacing: 1.1,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  availableFloat,
                  style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    color: Colors.white,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ],
            ),
          ),
          const Icon(Icons.arrow_forward_rounded, color: Colors.white70),
        ],
      ),
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
  const _Metric({
    required this.label,
    required this.value,
    required this.icon,
    required this.width,
  });

  final String label;
  final String value;
  final IconData icon;
  final double width;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: width,
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(icon, color: Theme.of(context).colorScheme.primary),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      label,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Text(
                value,
                style: Theme.of(context)
                    .textTheme
                    .headlineSmall
                    ?.copyWith(fontWeight: FontWeight.w800),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
