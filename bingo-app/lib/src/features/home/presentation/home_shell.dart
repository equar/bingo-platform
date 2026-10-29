import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/features/home/presentation/home_screen.dart';
import 'package:bingo_app/src/features/lobby/presentation/lobby_screen.dart';
import 'package:bingo_app/src/features/profile/presentation/profile_screen.dart';
import 'package:bingo_app/src/features/support/presentation/support_screen.dart';
import 'package:bingo_app/src/features/wallet/presentation/wallet_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class HomeShell extends StatefulWidget {
  const HomeShell({super.key});

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int _index = 0;

  void _select(int index) => setState(() => _index = index);

  void _support() => Navigator.of(
    context,
  ).push<void>(MaterialPageRoute(builder: (_) => const SupportScreen()));

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.dark.copyWith(
        statusBarColor: Colors.transparent,
      ),
      child: Scaffold(
        body: switch (_index) {
          0 => HomeScreen(
            onGames: () => _select(1),
            onWallet: () => _select(2),
            onProfile: () => _select(3),
            onSupport: _support,
          ),
          1 => const LobbyScreen(),
          2 => const WalletScreen(),
          _ => const ProfileScreen(),
        },
        bottomNavigationBar: DecoratedBox(
          decoration: const BoxDecoration(
            color: Colors.white,
            border: Border(top: BorderSide(color: Color(0xFFECEEF3))),
          ),
          child: NavigationBarTheme(
            data: NavigationBarThemeData(
              backgroundColor: Colors.white,
              surfaceTintColor: Colors.transparent,
              indicatorColor: bingoBlue.withValues(alpha: .09),
              labelTextStyle: WidgetStateProperty.resolveWith(
                (states) => TextStyle(
                  fontSize: 12,
                  fontWeight: states.contains(WidgetState.selected)
                      ? FontWeight.w800
                      : FontWeight.w600,
                  color: states.contains(WidgetState.selected)
                      ? bingoBlue
                      : const Color(0xFF667084),
                ),
              ),
              iconTheme: WidgetStateProperty.resolveWith(
                (states) => IconThemeData(
                  size: 27,
                  color: states.contains(WidgetState.selected)
                      ? bingoBlue
                      : const Color(0xFF667084),
                ),
              ),
            ),
            child: NavigationBar(
              height: 76,
              selectedIndex: _index,
              onDestinationSelected: _select,
              destinations: [
                NavigationDestination(
                  icon: const Icon(Icons.home_outlined),
                  selectedIcon: const Icon(Icons.home_rounded),
                  label: l.homeTitle,
                ),
                NavigationDestination(
                  icon: const Icon(Icons.grid_view_outlined),
                  selectedIcon: const Icon(Icons.grid_view_rounded),
                  label: l.homeGames,
                ),
                NavigationDestination(
                  icon: const Icon(Icons.account_balance_wallet_outlined),
                  selectedIcon: const Icon(
                    Icons.account_balance_wallet_rounded,
                  ),
                  label: l.homeWallet,
                ),
                NavigationDestination(
                  icon: const Icon(Icons.person_outline_rounded),
                  selectedIcon: const Icon(Icons.person_rounded),
                  label: l.homeProfile,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
