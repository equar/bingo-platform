import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

const bingoNavy = Color(0xFF081D46);
const bingoBlue = Color(0xFF1688FF);

class HomeScreen extends StatelessWidget {
  const HomeScreen({
    super.key,
    required this.onGames,
    required this.onWallet,
    required this.onProfile,
    required this.onSupport,
  });

  final VoidCallback onGames;
  final VoidCallback onWallet;
  final VoidCallback onProfile;
  final VoidCallback onSupport;

  void _menu(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (final item in [
              (Icons.grid_view_rounded, l.homeGames, onGames),
              (Icons.account_balance_wallet_outlined, l.homeWallet, onWallet),
              (Icons.support_agent_outlined, l.homeSupport, onSupport),
              (Icons.person_outline_rounded, l.homeProfile, onProfile),
            ])
              ListTile(
                key: item.$2 == l.homeSupport
                    ? const ValueKey('support-tab')
                    : null,
                leading: Icon(item.$1, color: bingoBlue),
                title: Text(item.$2),
                trailing: const Icon(Icons.chevron_right_rounded),
                onTap: () {
                  Navigator.pop(sheetContext);
                  item.$3();
                },
              ),
            const SizedBox(height: 12),
          ],
        ),
      ),
    );
  }

  void _upcoming(BuildContext context, String title) {
    final l = AppLocalizations.of(context)!;
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontSize: 25,
                  fontWeight: FontWeight.w800,
                  color: bingoNavy,
                ),
              ),
              const SizedBox(height: 12),
              Text(
                l.homeUpcomingBody,
                style: const TextStyle(fontSize: 16, height: 1.5),
              ),
              const SizedBox(height: 22),
              FilledButton(
                onPressed: () {
                  Navigator.pop(sheetContext);
                  onGames();
                },
                child: Text(l.homeBrowseGames),
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return ColoredBox(
      color: const Color(0xFFFAFBFD),
      child: SingleChildScrollView(
        key: const ValueKey('home-scroll'),
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 600),
            child: Column(
              children: [
                Stack(
                  children: [
                    AspectRatio(
                      aspectRatio: 1.27,
                      child: Image.asset(
                        'assets/images/bingo-ethiopia-hero.png',
                        fit: BoxFit.cover,
                        alignment: Alignment.bottomCenter,
                        excludeFromSemantics: true,
                      ),
                    ),
                    Positioned(
                      top: 0,
                      left: 14,
                      right: 14,
                      child: SafeArea(
                        bottom: false,
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            IconButton(
                              key: const ValueKey('home-menu'),
                              tooltip: l.homeMenu,
                              onPressed: () => _menu(context),
                              icon: const Icon(
                                Icons.menu_rounded,
                                size: 30,
                                color: bingoNavy,
                              ),
                            ),
                            IconButton(
                              tooltip: l.homeSupport,
                              onPressed: onSupport,
                              icon: const Icon(
                                Icons.notifications_none_rounded,
                                size: 29,
                                color: bingoNavy,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    Positioned(
                      bottom: 0,
                      left: 0,
                      right: 0,
                      child: IgnorePointer(
                        child: Container(
                          height: 24,
                          decoration: const BoxDecoration(
                            gradient: LinearGradient(
                              begin: Alignment.topCenter,
                              end: Alignment.bottomCenter,
                              colors: [Color(0x00FAFBFD), Color(0xFFFAFBFD)],
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 8, 20, 26),
                  child: Column(
                    children: [
                      Semantics(
                        button: true,
                        child: Container(
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(24),
                            gradient: const LinearGradient(
                              begin: Alignment.topLeft,
                              end: Alignment.bottomRight,
                              colors: [Color(0xFFFF9719), Color(0xFFFF6500)],
                            ),
                            boxShadow: [
                              BoxShadow(
                                color: const Color(
                                  0xFFFF7900,
                                ).withValues(alpha: .22),
                                blurRadius: 22,
                                offset: const Offset(0, 9),
                              ),
                            ],
                          ),
                          child: Material(
                            color: Colors.transparent,
                            child: InkWell(
                              key: const ValueKey('home-play-bingo'),
                              onTap: onGames,
                              borderRadius: BorderRadius.circular(24),
                              child: Padding(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 30,
                                  vertical: 14,
                                ),
                                child: Row(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  children: [
                                    const Icon(
                                      Icons.play_arrow_rounded,
                                      color: Colors.white,
                                      size: 46,
                                    ),
                                    const SizedBox(width: 12),
                                    Flexible(
                                      child: Text(
                                        l.homePlayBingo,
                                        textAlign: TextAlign.center,
                                        style: const TextStyle(
                                          color: Colors.white,
                                          fontSize: 28,
                                          fontWeight: FontWeight.w800,
                                          letterSpacing: -.5,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(height: 20),
                      IntrinsicHeight(
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            Expanded(
                              child: _FeatureCard(
                                title: l.homeLiveGames,
                                subtitle: l.homeLiveGamesBody,
                                icon: Icons.emoji_events_rounded,
                                color: bingoBlue,
                                background: const Color(0xFFEBF4FF),
                                onTap: onGames,
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: _FeatureCard(
                                title: l.homePrivateGame,
                                subtitle: l.homeComingSoon,
                                icon: Icons.groups_rounded,
                                color: const Color(0xFFFF8A12),
                                background: const Color(0xFFFFF5E7),
                                onTap: () =>
                                    _upcoming(context, l.homePrivateGame),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 12),
                      IntrinsicHeight(
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            Expanded(
                              child: _FeatureCard(
                                title: l.homeDailyChallenge,
                                subtitle: l.homeComingSoon,
                                icon: Icons.calendar_month_rounded,
                                color: const Color(0xFF12892B),
                                background: const Color(0xFFEAF8EE),
                                onTap: () =>
                                    _upcoming(context, l.homeDailyChallenge),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: _FeatureCard(
                                title: l.homeLeaderboard,
                                subtitle: l.homeComingSoon,
                                icon: Icons.bar_chart_rounded,
                                color: const Color(0xFF8739EF),
                                background: const Color(0xFFF2ECFF),
                                onTap: () =>
                                    _upcoming(context, l.homeLeaderboard),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 14),
                      Material(
                        color: const Color(0xFFFFF0B0),
                        borderRadius: BorderRadius.circular(22),
                        clipBehavior: Clip.antiAlias,
                        child: InkWell(
                          onTap: onGames,
                          child: Padding(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 16,
                              vertical: 16,
                            ),
                            child: Row(
                              children: [
                                const ExcludeSemantics(child: _GiftArtwork()),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        l.homePlayAndWin,
                                        style: const TextStyle(
                                          fontSize: 26,
                                          fontWeight: FontWeight.w900,
                                          color: bingoNavy,
                                          letterSpacing: -.8,
                                        ),
                                      ),
                                      const SizedBox(height: 4),
                                      Text(
                                        l.homePlayAndWinBody,
                                        style: const TextStyle(
                                          fontSize: 14,
                                          height: 1.4,
                                          color: Color(0xFF5E6575),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _FeatureCard extends StatelessWidget {
  const _FeatureCard({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.color,
    required this.background,
    required this.onTap,
  });
  final String title;
  final String subtitle;
  final IconData icon;
  final Color color;
  final Color background;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Material(
    color: background,
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(19),
      side: BorderSide(color: color.withValues(alpha: .1)),
    ),
    clipBehavior: Clip.antiAlias,
    child: InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(icon, size: 32, color: color),
                const Spacer(),
                Icon(Icons.chevron_right_rounded, size: 23, color: color),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              title,
              style: const TextStyle(
                fontSize: 17,
                fontWeight: FontWeight.w800,
                color: bingoNavy,
                height: 1.2,
                letterSpacing: -.4,
              ),
            ),
            const SizedBox(height: 5),
            Text(
              subtitle,
              style: const TextStyle(
                fontSize: 13,
                height: 1.25,
                color: Color(0xFF626C7C),
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class _GiftArtwork extends StatelessWidget {
  const _GiftArtwork();

  @override
  Widget build(BuildContext context) => SizedBox(
    width: 82,
    height: 64,
    child: Stack(
      alignment: Alignment.center,
      children: [
        Positioned(
          bottom: 0,
          right: 0,
          child: Container(
            width: 39,
            height: 39,
            decoration: BoxDecoration(
              color: const Color(0xFFFFC52D),
              shape: BoxShape.circle,
              border: Border.all(color: const Color(0xFFFFAA0A), width: 3),
            ),
            child: const Icon(
              Icons.star_rounded,
              size: 25,
              color: Color(0xFFFFF0A0),
            ),
          ),
        ),
        const Positioned(
          left: 0,
          top: 0,
          child: Icon(
            Icons.card_giftcard_rounded,
            size: 64,
            color: Color(0xFFF14935),
          ),
        ),
        const Positioned(
          left: 29,
          top: 29,
          child: Icon(Icons.star_rounded, size: 21, color: Color(0xFFFFC52D)),
        ),
      ],
    ),
  );
}
