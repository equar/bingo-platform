import 'package:bingo_player/src/core/localization/ethiopian_date_time.dart';
import 'package:bingo_player/src/core/network/api_provider.dart';
import 'package:bingo_player/src/features/player_content/domain/player_content_models.dart';
import 'package:bingo_player/src/features/game/data/game_api_provider.dart';
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
  late final _history = _loadHistoryAndClaims();

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
        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: entries.length + 1,
          separatorBuilder: (_, _) => const SizedBox(height: 8),
          itemBuilder: (context, index) {
            if (index == 0) {
              final games = entries.map((entry) => entry.sessionId).toSet();
              return Card(
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
                            value: '${games.length}',
                          ),
                          _StatValue(
                            label: l.ticketsPlayed,
                            value: '${entries.length}',
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
              );
            }
            final entry = entries[index - 1];
            return Card(
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
                trailing: Text(entry.sessionState),
              ),
            );
          },
        );
      },
    ),
  );
}

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
