import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:bingo_player/src/features/community/presentation/private_games_screen.dart';
import 'package:bingo_player/src/features/community/presentation/daily_challenges_screen.dart';
import 'package:bingo_player/src/features/community/presentation/leaderboard_screen.dart';

const bingoNavy = Color(0xFF081D46);
const bingoBlue = Color(0xFF1688FF);

class HomeScreen extends StatelessWidget {
  const HomeScreen({
    super.key,
    required this.onGames,
    required this.onOpenMenu,
    required this.onSupport,
  });

  final VoidCallback onGames;
  final VoidCallback onOpenMenu;
  final VoidCallback onSupport;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final colors = Theme.of(context).colorScheme;
    return LayoutBuilder(
      builder: (context, constraints) {
        final contentWidth = constraints.maxWidth >= 640
            ? 560.0
            : constraints.maxWidth - 40;
        final fillsViewport =
            constraints.maxHeight >= 680 &&
            contentWidth >= 300 &&
            MediaQuery.textScalerOf(context).scale(14) <= 18;

        final header = Container(
          key: const ValueKey('home-header-background'),
          width: double.infinity,
          decoration: const BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [Color(0xFF0B2B5B), bingoNavy],
            ),
          ),
          child: SafeArea(
            bottom: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 4, 12, 18),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  IconButton(
                    key: const ValueKey('home-menu'),
                    tooltip: l.homeMenu,
                    onPressed: onOpenMenu,
                    color: Colors.white,
                    icon: const Icon(Icons.menu_rounded, size: 28),
                  ),
                  Expanded(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Icon(
                              Icons.emoji_events_rounded,
                              color: Color(0xFFFFC52D),
                              size: 20,
                            ),
                            const SizedBox(width: 4),
                            Flexible(
                              child: Text(
                                'BINGO',
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: Theme.of(context).textTheme.titleLarge
                                    ?.copyWith(
                                      color: Colors.white,
                                      letterSpacing: 1.2,
                                    ),
                              ),
                            ),
                          ],
                        ),
                        Text(
                          l.homeTitle,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          textAlign: TextAlign.center,
                          style: Theme.of(context).textTheme.labelSmall
                              ?.copyWith(
                                color: Colors.white70,
                                letterSpacing: .5,
                              ),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    tooltip: l.homeSupport,
                    onPressed: onSupport,
                    color: Colors.white,
                    icon: const Icon(Icons.support_agent_rounded, size: 27),
                  ),
                ],
              ),
            ),
          ),
        );
        final playButton = Semantics(
          button: true,
          child: Container(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(24),
              gradient: const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [Color(0xFFD86800), Color(0xFFC94D00)],
              ),
              boxShadow: [
                BoxShadow(
                  color: const Color(0xFFFF7900).withValues(alpha: .22),
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
        );
        final featureGrid = _HomeFeatureGrid(
          fillSpace: fillsViewport,
          children: [
            _FeatureCard(
              title: l.homeLiveGames,
              subtitle: l.homeLiveGamesBody,
              icon: Icons.emoji_events_rounded,
              color: bingoBlue,
              background: const Color(0xFFEBF4FF),
              onTap: onGames,
            ),
            _FeatureCard(
              title: l.homePrivateGame,
              subtitle: l.privateHomeBody,
              icon: Icons.groups_rounded,
              color: const Color(0xFFFF8A12),
              background: const Color(0xFFFFF5E7),
              onTap: () => Navigator.of(context).push<void>(
                MaterialPageRoute(builder: (_) => const PrivateGamesScreen()),
              ),
            ),
            _FeatureCard(
              title: l.homeDailyChallenge,
              subtitle: l.challengeHomeBody,
              icon: Icons.calendar_month_rounded,
              color: const Color(0xFF12892B),
              background: const Color(0xFFEAF8EE),
              onTap: () => Navigator.of(context).push<void>(
                MaterialPageRoute(
                  builder: (_) => const DailyChallengesScreen(),
                ),
              ),
            ),
            _FeatureCard(
              title: l.homeLeaderboard,
              subtitle: l.leaderboardHomeBody,
              icon: Icons.bar_chart_rounded,
              color: const Color(0xFF8739EF),
              background: const Color(0xFFF2ECFF),
              onTap: () => Navigator.of(context).push<void>(
                MaterialPageRoute(builder: (_) => const LeaderboardScreen()),
              ),
            ),
          ],
        );
        final playAndWin = Material(
          color: Theme.of(context).brightness == Brightness.dark
              ? colors.tertiaryContainer
              : const Color(0xFFFFF0B0),
          borderRadius: BorderRadius.circular(22),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: onGames,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
              child: Row(
                children: [
                  const ExcludeSemantics(child: _GiftArtwork()),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          l.homePlayAndWin,
                          style: TextStyle(
                            fontSize: 26,
                            fontWeight: FontWeight.w900,
                            color:
                                Theme.of(context).brightness == Brightness.dark
                                ? colors.onTertiaryContainer
                                : bingoNavy,
                            letterSpacing: -.8,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          l.homePlayAndWinBody,
                          style: TextStyle(
                            fontSize: 14,
                            height: 1.4,
                            color:
                                Theme.of(context).brightness == Brightness.dark
                                ? colors.onTertiaryContainer
                                : const Color(0xFF5E6575),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
        final mainContent = Padding(
          padding: const EdgeInsets.fromLTRB(20, 18, 20, 26),
          child: Column(
            mainAxisSize: fillsViewport ? MainAxisSize.max : MainAxisSize.min,
            children: [
              playButton,
              const SizedBox(height: 20),
              if (fillsViewport) Expanded(child: featureGrid) else featureGrid,
              const SizedBox(height: 14),
              playAndWin,
            ],
          ),
        );
        final content = Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 600),
            child: Column(
              mainAxisSize: fillsViewport ? MainAxisSize.max : MainAxisSize.min,
              children: [
                header,
                if (fillsViewport)
                  Expanded(child: mainContent)
                else
                  mainContent,
              ],
            ),
          ),
        );

        return ColoredBox(
          color: colors.surface,
          child: fillsViewport
              ? content
              : SingleChildScrollView(
                  key: const ValueKey('home-scroll'),
                  child: content,
                ),
        );
      },
    );
  }
}

class _HomeFeatureGrid extends StatelessWidget {
  const _HomeFeatureGrid({required this.children, this.fillSpace = false});

  final List<Widget> children;
  final bool fillSpace;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final largeText = MediaQuery.textScalerOf(context).scale(14) > 20;
      final columns = constraints.maxWidth < 300 || largeText ? 1 : 2;
      final width = (constraints.maxWidth - (columns - 1) * 12) / columns;
      final rows = (children.length + columns - 1) ~/ columns;
      final rowSpacing = (rows - 1) * 12;
      final availableCardHeight = (constraints.maxHeight - rowSpacing) / rows;
      final cardHeight = availableCardHeight < 140
          ? 140.0
          : availableCardHeight;
      return Wrap(
        spacing: 12,
        runSpacing: 12,
        children: [
          for (final child in children)
            SizedBox(
              width: width,
              child: fillSpace && constraints.hasBoundedHeight
                  ? SizedBox(height: cardHeight, child: child)
                  : ConstrainedBox(
                      constraints: const BoxConstraints(minHeight: 140),
                      child: child,
                    ),
            ),
        ],
      );
    },
  );
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
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final featureColor = isDark ? colors.onSurface : color;
    return LayoutBuilder(
      builder: (context, constraints) {
        final compact =
            constraints.maxWidth < 180 || constraints.maxHeight < 160;
        return Material(
          color: isDark ? colors.surfaceContainerHigh : background,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(19),
            side: BorderSide(
              color: isDark
                  ? colors.outlineVariant
                  : color.withValues(alpha: .1),
            ),
          ),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: onTap,
            child: Padding(
              padding: EdgeInsets.all(compact ? 12 : 14),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: EdgeInsets.all(compact ? 8 : 10),
                        decoration: BoxDecoration(
                          color: isDark
                              ? colors.surface
                              : Colors.white.withValues(alpha: .85),
                          borderRadius: BorderRadius.circular(14),
                        ),
                        child: Icon(
                          icon,
                          size: compact ? 24 : 27,
                          color: featureColor,
                        ),
                      ),
                      const Spacer(),
                      Icon(
                        Icons.chevron_right_rounded,
                        size: 23,
                        color: featureColor,
                      ),
                    ],
                  ),
                  SizedBox(height: compact ? 8 : 12),
                  Text(
                    title,
                    style: TextStyle(
                      fontSize: compact ? 15 : 17,
                      fontWeight: FontWeight.w800,
                      color: isDark ? colors.onSurface : bingoNavy,
                      height: 1.2,
                      letterSpacing: -.4,
                    ),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    subtitle,
                    style: TextStyle(
                      fontSize: compact ? 12 : 13,
                      height: 1.25,
                      color: isDark
                          ? colors.onSurfaceVariant
                          : const Color(0xFF626C7C),
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
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
