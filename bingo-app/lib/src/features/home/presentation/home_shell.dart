import 'package:bingo_app/src/core/widgets/responsive_content.dart';
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

  final _visited = <int>{0};
  final _navigatorKeys = List.generate(4, (_) => GlobalKey<NavigatorState>());
  final _heroControllers = List.generate(4, (_) => HeroController());
  late final _observers = List.generate(
    4,
    (_) => _TabObserver(() {
      if (mounted) setState(() {});
    }),
  );

  void _select(int index) {
    if (index == _index) {
      _navigatorKeys[index].currentState?.popUntil((route) => route.isFirst);
      return;
    }
    FocusManager.instance.primaryFocus?.unfocus();
    setState(() {
      _visited.add(index);
      _index = index;
    });
  }

  Widget _rootPage(int index) => switch (index) {
    0 => HomeScreen(
      onGames: () => _select(1),
      onWallet: () => _select(2),
      onProfile: () => _select(3),
      onSupport: _support,
    ),
    1 => const LobbyScreen(),
    2 => const WalletScreen(),
    _ => ProfileScreen(
      onGames: () => _select(1),
      onWallet: () => _select(2),
      onSupport: _support,
    ),
  };

  Widget _pages() => PopScope<Object?>(
    canPop:
        _index == 0 &&
        !(_navigatorKeys[_index].currentState?.canPop() ?? false),
    onPopInvokedWithResult: (didPop, result) {
      if (didPop) return;
      final navigator = _navigatorKeys[_index].currentState;
      if (navigator?.canPop() ?? false) {
        navigator!.pop(result);
      } else if (_index != 0) {
        _select(0);
      }
    },
    child: IndexedStack(
      index: _index,
      children: [
        for (var index = 0; index < 4; index++)
          if (_visited.contains(index))
            TickerMode(
              enabled: _index == index,
              child: HeroControllerScope(
                controller: _heroControllers[index],
                child: Navigator(
                  key: _navigatorKeys[index],
                  observers: [_observers[index]],
                  onGenerateRoute: (_) =>
                      MaterialPageRoute<void>(builder: (_) => _rootPage(index)),
                ),
              ),
            )
          else
            const SizedBox.shrink(),
      ],
    ),
  );

  void _support() => _navigatorKeys[_index].currentState!.push<void>(
    MaterialPageRoute(builder: (_) => const SupportScreen()),
  );

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return LayoutBuilder(
      builder: (context, constraints) {
        final wide =
            constraints.maxWidth >= 840 && constraints.maxHeight >= 480;
        final page = _pages();
        return AnnotatedRegion<SystemUiOverlayStyle>(
          value: SystemUiOverlayStyle.dark.copyWith(
            statusBarColor: Colors.transparent,
          ),
          child: Scaffold(
            body: wide
                ? Row(
                    children: [
                      SafeArea(
                        child: NavigationRail(
                          selectedIndex: _index,
                          onDestinationSelected: _select,
                          labelType: NavigationRailLabelType.all,
                          backgroundColor: Colors.white,
                          indicatorColor: Theme.of(
                            context,
                          ).colorScheme.primary.withValues(alpha: .10),
                          destinations: [
                            NavigationRailDestination(
                              icon: const Icon(Icons.home_outlined),
                              selectedIcon: const Icon(Icons.home_rounded),
                              label: Text(l.homeTitle),
                            ),
                            NavigationRailDestination(
                              icon: const Icon(Icons.grid_view_outlined),
                              selectedIcon: const Icon(Icons.grid_view_rounded),
                              label: Text(l.homeGames),
                            ),
                            NavigationRailDestination(
                              icon: const Icon(
                                Icons.account_balance_wallet_outlined,
                              ),
                              selectedIcon: const Icon(
                                Icons.account_balance_wallet_rounded,
                              ),
                              label: Text(l.homeWallet),
                            ),
                            NavigationRailDestination(
                              icon: const Icon(Icons.person_outline_rounded),
                              selectedIcon: const Icon(Icons.person_rounded),
                              label: Text(l.homeProfile),
                            ),
                          ],
                        ),
                      ),
                      const VerticalDivider(width: 1, color: Color(0xFFECEEF3)),
                      Expanded(child: ResponsiveContent(child: page)),
                    ],
                  )
                : page,
            bottomNavigationBar: wide
                ? null
                : DecoratedBox(
                    decoration: const BoxDecoration(
                      color: Colors.white,
                      border: Border(top: BorderSide(color: Color(0xFFECEEF3))),
                    ),
                    child: NavigationBarTheme(
                      data: NavigationBarThemeData(
                        backgroundColor: Colors.white,
                        surfaceTintColor: Colors.transparent,
                        indicatorColor: Theme.of(
                          context,
                        ).colorScheme.primary.withValues(alpha: .10),
                        labelTextStyle: WidgetStateProperty.resolveWith(
                          (states) => TextStyle(
                            fontSize: 12,
                            fontWeight: states.contains(WidgetState.selected)
                                ? FontWeight.w800
                                : FontWeight.w600,
                            color: states.contains(WidgetState.selected)
                                ? Theme.of(context).colorScheme.primary
                                : const Color(0xFF667084),
                          ),
                        ),
                        iconTheme: WidgetStateProperty.resolveWith(
                          (states) => IconThemeData(
                            size: 27,
                            color: states.contains(WidgetState.selected)
                                ? Theme.of(context).colorScheme.primary
                                : const Color(0xFF667084),
                          ),
                        ),
                      ),
                      child: NavigationBar(
                        height: MediaQuery.textScalerOf(context).scale(12) > 18
                            ? 88
                            : 76,
                        animationDuration:
                            MediaQuery.disableAnimationsOf(context)
                            ? Duration.zero
                            : const Duration(milliseconds: 200),
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
                            icon: const Icon(
                              Icons.account_balance_wallet_outlined,
                            ),
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
      },
    );
  }
}

/// Rebuild the shell after route changes so Android back follows the active tab.
class _TabObserver extends NavigatorObserver {
  _TabObserver(this.onChanged);
  final VoidCallback onChanged;

  void _notify() =>
      WidgetsBinding.instance.addPostFrameCallback((_) => onChanged());

  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) =>
      _notify();

  @override
  void didPop(Route<dynamic> route, Route<dynamic>? previousRoute) => _notify();

  @override
  void didRemove(Route<dynamic> route, Route<dynamic>? previousRoute) =>
      _notify();
}
