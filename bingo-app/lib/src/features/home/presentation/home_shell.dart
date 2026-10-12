import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/features/lobby/presentation/lobby_screen.dart';
import 'package:bingo_app/src/features/profile/presentation/profile_screen.dart';
import 'package:bingo_app/src/features/wallet/presentation/wallet_screen.dart';
import 'package:flutter/material.dart';

class HomeShell extends StatefulWidget {
  const HomeShell({super.key});

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final pages = [
      const LobbyScreen(),
      const WalletScreen(),
      const ProfileScreen(),
    ];

    return Scaffold(
      body: IndexedStack(index: _index, children: pages),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (index) => setState(() => _index = index),
        destinations: [
          NavigationDestination(icon: const Icon(Icons.casino_outlined), label: localizations.homeLobby),
          NavigationDestination(icon: const Icon(Icons.account_balance_wallet_outlined), label: localizations.homeWallet),
          NavigationDestination(icon: const Icon(Icons.person_outline), label: localizations.homeProfile),
        ],
      ),
    );
  }
}
