import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/core/widgets/app_status_view.dart';
import 'package:bingo_app/src/core/widgets/section_card.dart';
import 'package:bingo_app/src/features/game/presentation/session_detail_screen.dart';
import 'package:bingo_app/src/features/lobby/data/lobby_repository.dart';
import 'package:bingo_app/src/features/lobby/domain/lobby_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final lobbyRoomsProvider = FutureProvider.autoDispose<List<BingoRoom>>((ref) async {
  return ref.read(lobbyRepositoryProvider).getRooms();
});

class LobbyScreen extends ConsumerStatefulWidget {
  const LobbyScreen({super.key});

  @override
  ConsumerState<LobbyScreen> createState() => _LobbyScreenState();
}

class _LobbyScreenState extends ConsumerState<LobbyScreen> {
  final TextEditingController _sessionIdController = TextEditingController();

  @override
  void dispose() {
    _sessionIdController.dispose();
    super.dispose();
  }

  Future<void> _openSession(BuildContext context, String value) async {
    final sessionId = int.tryParse(value.trim());
    if (sessionId == null) {
      return;
    }
    final session = await ref.read(lobbyRepositoryProvider).getSession(sessionId);
    if (!context.mounted) return;
    await Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => SessionDetailScreen(session: session)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final roomsAsync = ref.watch(lobbyRoomsProvider);

    return Scaffold(
      appBar: AppBar(title: Text(localizations.roomsTitle)),
      body: roomsAsync.when(
        loading: () => Center(child: Text(localizations.loading)),
        error: (error, stack) => AppStatusView(
          title: localizations.errorGeneric,
          message: error.toString(),
          onAction: () => ref.invalidate(lobbyRoomsProvider),
        ),
        data: (rooms) {
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              SectionCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(localizations.sessionGapTitle, style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 8),
                    Text(localizations.sessionGapBody),
                    const SizedBox(height: 16),
                    TextField(
                      key: const ValueKey('session-id-field'),
                      controller: _sessionIdController,
                      keyboardType: TextInputType.number,
                      decoration: InputDecoration(
                        labelText: localizations.enterSessionIdLabel,
                        suffixIcon: IconButton(
                          tooltip: localizations.loadSession,
                          onPressed: () => _openSession(context, _sessionIdController.text),
                          icon: const Icon(Icons.search),
                        ),
                      ),
                      onSubmitted: (value) => _openSession(context, value),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              if (rooms.isEmpty)
                AppStatusView(title: localizations.emptyState, message: localizations.contractGapBody)
              else
                ...rooms.map(
                  (room) => Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: SectionCard(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Expanded(
                                child: Text(room.name, style: Theme.of(context).textTheme.titleLarge),
                              ),
                              Chip(label: Text(room.active ? localizations.roomStatusActive : localizations.roomStatusInactive)),
                            ],
                          ),
                          if (room.description != null) ...[
                            const SizedBox(height: 8),
                            Text(room.description!),
                          ],
                          const SizedBox(height: 12),
                          Wrap(
                            spacing: 12,
                            runSpacing: 8,
                            children: [
                              _InfoChip(label: localizations.roomPriceLabel, value: '${room.ticketPrice} ${room.currency}'),
                              _InfoChip(label: localizations.roomCapacityLabel, value: room.sessionCapacity.toString()),
                              _InfoChip(label: localizations.roomScheduleLabel, value: room.scheduleRule),
                              _InfoChip(label: localizations.roomPlayersLabel, value: localizations.playerCountUnavailable),
                            ],
                          ),
                          const SizedBox(height: 12),
                          Text(localizations.sessionGapBody),
                        ],
                      ),
                    ),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }
}

class _InfoChip extends StatelessWidget {
  const _InfoChip({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Chip(label: Text('$label: $value'));
  }
}
