import 'package:bingo_player/src/core/localization/ethiopian_date_time.dart';
import 'package:bingo_player/src/core/network/api_provider.dart';
import 'package:bingo_player/src/features/player_content/domain/player_content_models.dart';
import 'package:bingo_player/src/features/game/data/game_api_provider.dart';
import 'package:bingo_player/src/features/lobby/data/lobby_repository.dart';
import 'package:bingo_player/src/features/game/presentation/session_detail_screen.dart';
import 'package:bingo_player/src/features/game/domain/live_game_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:bingo_player/l10n/app_localizations.dart';

class GameHistoryScreen extends ConsumerStatefulWidget {
  const GameHistoryScreen({super.key});

  @override
  ConsumerState<GameHistoryScreen> createState() => _GameHistoryScreenState();
}

class _GameHistoryScreenState extends ConsumerState<GameHistoryScreen> {
  late final Future<
    ({List<PlayerGameHistoryItem> entries, List<WinClaimRecord> claims})
  >
  _history = _loadHistoryAndClaims();

  Future<void> _openGame(PlayerGameHistoryItem entry) async {
    final l = AppLocalizations.of(context)!;
    try {
      final session = await ref
          .read(lobbyRepositoryProvider)
          .getSession(entry.sessionId);
      if (!mounted) return;
      await Navigator.of(context).push<void>(
        MaterialPageRoute<void>(
          builder: (_) => SessionDetailScreen(session: session),
        ),
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(l.errorGeneric)));
    }
  }

  Future<({List<PlayerGameHistoryItem> entries, List<WinClaimRecord> claims})>
  _loadHistoryAndClaims() async {
    final historyApi = ref.read(playerContentApiProvider);
    final gameApi = ref.read(gameApiProvider);
    final entries = <PlayerGameHistoryItem>[];
    var page = 0;
    while (true) {
      final result = await historyApi.getGameHistory(page: page, size: 100);
      entries.addAll(result.content);
      if (result.last || result.content.isEmpty) break;
      page++;
    }
    final claims = <WinClaimRecord>[];
    page = 0;
    while (true) {
      final result = await gameApi.getMyWinClaims(page: page, size: 100);
      claims.addAll(result.content);
      if (result.last || result.content.isEmpty) break;
      page++;
    }
    return (entries: entries, claims: claims);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(AppLocalizations.of(context)!.gameHistoryTitle)),
    body: FutureBuilder(
      future: _history,
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          return Center(
            child: Text(AppLocalizations.of(context)!.gameHistoryLoadError),
          );
        }
        final data = snapshot.data;
        final l = AppLocalizations.of(context)!;
        final entries = data?.entries ?? const <PlayerGameHistoryItem>[];
        final claims = data?.claims ?? const <WinClaimRecord>[];
        if (entries.isEmpty) {
          return Center(
            child: Text(AppLocalizations.of(context)!.gameHistoryEmpty),
          );
        }
        final activeGames = <int, List<PlayerGameHistoryItem>>{};
        final historyEntries = <PlayerGameHistoryItem>[];
        for (final entry in entries) {
          if (const {
            'OPEN',
            'STARTED',
            'DRAWING',
          }.contains(entry.sessionState.toUpperCase())) {
            activeGames.putIfAbsent(entry.sessionId, () => []).add(entry);
          } else {
            historyEntries.add(entry);
          }
        }
        final wonSessions = claims
            .where((claim) => claim.serverValidationPassed)
            .map((claim) => claim.sessionId)
            .toSet();
        final winnings = claims
            .where((claim) => claim.serverValidationPassed)
            .fold<num>(0, (sum, claim) => sum + (claim.prizeAmount ?? 0));
        final currency = claims
            .where((claim) => claim.prizeCurrency?.isNotEmpty == true)
            .map((claim) => claim.prizeCurrency!)
            .firstOrNull;
        final completedGames = historyEntries
            .map((entry) => entry.sessionId)
            .toSet();
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Card(
              key: const ValueKey('player-game-statistics'),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      l.playerStatisticsTitle,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 12),
                    Wrap(
                      spacing: 20,
                      runSpacing: 12,
                      children: [
                        _StatValue(
                          label: l.gamesPlayed,
                          value: '${completedGames.length}',
                        ),
                        _StatValue(
                          label: l.ticketsPlayed,
                          value: '${historyEntries.length}',
                        ),
                        _StatValue(
                          label: l.winningGames,
                          value: '${wonSessions.length}',
                        ),
                        _StatValue(
                          label: l.prizesWon,
                          value:
                              '${winnings.toStringAsFixed(2)} ${currency ?? ''}',
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            if (activeGames.isNotEmpty) ...[
              const SizedBox(height: 16),
              Text(
                l.myActiveGamesTitle,
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 8),
              for (final gameEntries in activeGames.values) ...[
                Card(
                  child: ListTile(
                    leading: const CircleAvatar(
                      child: Icon(Icons.sports_esports_outlined),
                    ),
                    title: Text(
                      gameEntries.first.sessionName.isEmpty
                          ? gameEntries.first.roomName
                          : gameEntries.first.sessionName,
                    ),
                    subtitle: Text(
                      '${gameEntries.first.roomName}\n'
                      '${l.sessionStatusLabel}: '
                      '${_localizedSessionState(l, gameEntries.first.sessionState)} · '
                      '${l.ticketCountLabel}: ${gameEntries.length}',
                    ),
                    isThreeLine: true,
                    trailing: const Icon(Icons.chevron_right_rounded),
                    onTap: () => _openGame(gameEntries.first),
                  ),
                ),
                const SizedBox(height: 8),
              ],
            ],
            for (final entry in historyEntries)
              Card(
                child: ListTile(
                  leading: const CircleAvatar(
                    child: Icon(Icons.sports_esports_outlined),
                  ),
                  title: Text(
                    entry.sessionName.isEmpty
                        ? entry.roomName
                        : entry.sessionName,
                  ),
                  subtitle: Text(
                    '${entry.roomName} · ${entry.ticketCode}\n${EthiopianDateTime.date(entry.purchasedAt, languageCode: Localizations.localeOf(context).languageCode)}',
                  ),
                  isThreeLine: true,
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () => _openGame(entry),
                ),
              ),
          ],
        );
      },
    ),
  );
}

String _localizedSessionState(AppLocalizations localizations, String state) =>
    switch (state.toUpperCase()) {
      'OPEN' => localizations.upcomingGamesTab,
      'STARTED' || 'DRAWING' => localizations.liveGamesTab,
      _ => state,
    };

class _StatValue extends StatelessWidget {
  const _StatValue({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => SizedBox(
    width: 130,
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(value, style: Theme.of(context).textTheme.titleLarge),
        Text(label, style: Theme.of(context).textTheme.bodySmall),
      ],
    ),
  );
}
