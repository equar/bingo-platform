import 'package:bingo_agent/src/features/agents/presentation/agent_dashboard_screen.dart';
import 'package:bingo_agent/src/features/auth/data/auth_session_controller.dart';
import 'package:bingo_agent/src/features/commissions/presentation/commission_history_screen.dart';
import 'package:bingo_agent/src/features/float/presentation/float_history_screen.dart';
import 'package:bingo_agent/src/features/payouts/presentation/payouts_screen.dart';
import 'package:bingo_agent/src/features/players/presentation/players_screen.dart';
import 'package:bingo_agent/src/features/support/presentation/support_screen.dart';
import 'package:bingo_agent/src/features/tickets/presentation/ticket_sale_screen.dart';
import 'package:bingo_agent/src/features/wallet/presentation/wallet_topup_screen.dart';
import 'package:bingo_agent/src/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class HomeShell extends ConsumerStatefulWidget {
  const HomeShell({super.key});

  @override
  ConsumerState<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends ConsumerState<HomeShell> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final pages = const [
      AgentDashboardScreen(),
      PlayersScreen(),
      TicketSaleScreen(),
      WalletTopUpScreen(),
      PayoutsScreen(),
      FloatHistoryScreen(),
      CommissionHistoryScreen(),
      SupportScreen(),
    ];
    final items = [
      NavigationDestination(icon: Icon(Icons.dashboard_outlined), selectedIcon: Icon(Icons.dashboard), label: l10n.dashboardTitle),
      NavigationDestination(icon: Icon(Icons.people_outline), selectedIcon: Icon(Icons.people), label: l10n.playersTitle),
      NavigationDestination(icon: Icon(Icons.confirmation_number_outlined), selectedIcon: Icon(Icons.confirmation_number), label: l10n.ticketsTitle),
      NavigationDestination(icon: Icon(Icons.account_balance_wallet_outlined), selectedIcon: Icon(Icons.account_balance_wallet), label: l10n.walletTitle),
      NavigationDestination(icon: Icon(Icons.verified_outlined), selectedIcon: Icon(Icons.verified), label: l10n.payoutsTitle),
      NavigationDestination(icon: Icon(Icons.swap_horiz_outlined), selectedIcon: Icon(Icons.swap_horiz), label: l10n.floatTitle),
      NavigationDestination(icon: Icon(Icons.receipt_long_outlined), selectedIcon: Icon(Icons.receipt_long), label: l10n.commissionsTitle),
      NavigationDestination(icon: Icon(Icons.support_agent_outlined), selectedIcon: Icon(Icons.support_agent), label: l10n.supportTitle),
    ];

    return Scaffold(
      appBar: AppBar(
        title: Text(items[_index].label),
        actions: [
          IconButton(
            tooltip: l10n.logout,
            onPressed: () => ref.read(authSessionControllerProvider.notifier).signOut(),
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: pages[_index],
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (value) => setState(() => _index = value),
        destinations: items,
      ),
    );
  }
}