import 'package:bingo_player/src/core/localization/ethiopian_date_time.dart';
import 'package:bingo_player/src/core/network/api_provider.dart';
import 'package:bingo_player/src/features/player_content/domain/player_content_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:bingo_player/l10n/app_localizations.dart';

class GameHistoryScreen extends ConsumerStatefulWidget {
  const GameHistoryScreen({super.key});

  @override
  ConsumerState<GameHistoryScreen> createState() => _GameHistoryScreenState();
}

class _GameHistoryScreenState extends ConsumerState<GameHistoryScreen> {
  late final _history = ref
      .read(playerContentApiProvider)
      .getGameHistory(page: 0, size: 50);

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
        final entries =
            snapshot.data?.content ?? const <PlayerGameHistoryItem>[];
        if (entries.isEmpty) {
          return Center(
            child: Text(AppLocalizations.of(context)!.gameHistoryEmpty),
          );
        }
        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: entries.length,
          separatorBuilder: (_, _) => const SizedBox(height: 8),
          itemBuilder: (context, index) {
            final entry = entries[index];
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
