import 'package:bingo_agent/src/features/agents/presentation/agent_dashboard_screen.dart';
import 'package:bingo_agent/src/features/auth/data/auth_session_controller.dart';
import 'package:bingo_agent/src/features/float/presentation/float_history_screen.dart';
import 'package:bingo_agent/src/features/history/presentation/financial_history_screen.dart';
import 'package:bingo_agent/src/features/payouts/presentation/payouts_screen.dart';
import 'package:bingo_agent/src/features/players/presentation/players_screen.dart';
import 'package:bingo_agent/src/features/support/presentation/support_screen.dart';
import 'package:bingo_agent/src/features/tickets/presentation/ticket_sale_screen.dart';
import 'package:bingo_agent/src/features/wallet/presentation/wallet_topup_screen.dart';
import 'package:bingo_agent/src/core/localization/language_selector.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class HomeShell extends ConsumerStatefulWidget {
  const HomeShell({super.key});

  @override
  ConsumerState<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends ConsumerState<HomeShell> {
  int _index = 0;
  final Set<int> _visited = {0};
  bool _signingOut = false;

  void _select(int index) {
    FocusManager.instance.primaryFocus?.unfocus();
    setState(() {
      _index = index;
      _visited.add(index);
    });
  }

  Future<void> _signOut() async {
    if (_signingOut) return;
    setState(() => _signingOut = true);
    try {
      await ref.read(authSessionControllerProvider.notifier).signOut();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(AppLocalizations.of(context)!.requestFailed)),
        );
      }
    } finally {
      if (mounted) setState(() => _signingOut = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final pages = const [
      AgentDashboardScreen(),
      PlayersScreen(),
      TicketSaleScreen(),
      WalletTopUpScreen(),
      PayoutsScreen(),
      FloatHistoryScreen(),
      FinancialHistoryScreen(),
      SupportScreen(),
    ];
    final items = [
      NavigationDestination(
        icon: Icon(Icons.dashboard_outlined),
        selectedIcon: Icon(Icons.dashboard),
        label: l10n.dashboardTitle,
      ),
      NavigationDestination(
        icon: Icon(Icons.people_outline),
        selectedIcon: Icon(Icons.people),
        label: l10n.playersTitle,
      ),
      NavigationDestination(
        icon: Icon(Icons.confirmation_number_outlined),
        selectedIcon: Icon(Icons.confirmation_number),
        label: l10n.ticketsTitle,
      ),
      NavigationDestination(
        icon: Icon(Icons.account_balance_wallet_outlined),
        selectedIcon: Icon(Icons.account_balance_wallet),
        label: l10n.walletTitle,
      ),
      NavigationDestination(
        icon: Icon(Icons.more_horiz),
        selectedIcon: Icon(Icons.more_horiz),
        label: l10n.more,
      ),
    ];
    const pageCount = 9;
    final pageLabels = [
      l10n.dashboardTitle,
      l10n.playersTitle,
      l10n.ticketsTitle,
      l10n.walletTitle,
      l10n.payoutsTitle,
      l10n.floatTitle,
      l10n.financialHistoryTitle,
      l10n.supportTitle,
      l10n.more,
    ];

    return Scaffold(
      key: const ValueKey('agent-home-screen'),
      appBar: AppBar(
        title: Text(pageLabels[_index]),
        actions: [
          const LanguageSelector(),
          IconButton(
            tooltip: l10n.logout,
            onPressed: _signingOut ? null : _signOut,
            icon: _signingOut
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.logout),
          ),
        ],
      ),
      body: LayoutBuilder(
        builder: (context, constraints) {
          final wide =
              constraints.maxWidth >= 840 && constraints.maxHeight >= 480;
          final content = IndexedStack(
            index: _index,
            children: [
              for (var index = 0; index < pageCount; index++)
                if (_visited.contains(index))
                  TickerMode(
                    enabled: _index == index,
                    child: index == 8
                        ? _MoreActionsScreen(
                            onPayouts: () => _select(4),
                            onFloat: () => _select(5),
                            onHistory: () => _select(6),
                            onSupport: () => _select(7),
                          )
                        : pages[index],
                  )
                else
                  const SizedBox.shrink(),
            ],
          );
          if (!wide) return content;
          return Row(
            children: [
              SafeArea(
                child: NavigationRail(
                  selectedIndex: _index < 4 ? _index : 4,
                  onDestinationSelected: (value) =>
                      _select(value == 4 ? 8 : value),
                  labelType: NavigationRailLabelType.all,
                  destinations: items
                      .map(
                        (item) => NavigationRailDestination(
                          icon: item.icon,
                          selectedIcon: item.selectedIcon,
                          label: Text(item.label),
                        ),
                      )
                      .toList(),
                ),
              ),
              const VerticalDivider(width: 1),
              Expanded(child: content),
            ],
          );
        },
      ),
      bottomNavigationBar:
          MediaQuery.sizeOf(context).width >= 840 &&
              MediaQuery.sizeOf(context).height >= 480
          ? null
          : NavigationBar(
              selectedIndex: _index < 4 ? _index : 4,
              onDestinationSelected: (value) => _select(value == 4 ? 8 : value),
              destinations: items,
            ),
    );
  }
}

class _MoreActionsScreen extends StatelessWidget {
  const _MoreActionsScreen({
    required this.onPayouts,
    required this.onFloat,
    required this.onHistory,
    required this.onSupport,
  });

  final VoidCallback onPayouts;
  final VoidCallback onFloat;
  final VoidCallback onHistory;
  final VoidCallback onSupport;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final actions = [
      (Icons.verified_outlined, l10n.payoutsTitle, onPayouts),
      (Icons.swap_horiz_outlined, l10n.floatTitle, onFloat),
      (Icons.receipt_long_outlined, l10n.historyTitle, onHistory),
      (Icons.support_agent_outlined, l10n.supportTitle, onSupport),
    ];
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        for (final (icon, label, onTap) in actions)
          Card(
            child: ListTile(
              leading: Icon(icon),
              title: Text(label),
              trailing: const Icon(Icons.chevron_right),
              onTap: onTap,
            ),
          ),
      ],
    );
  }
}
