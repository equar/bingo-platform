import 'package:bingo_app/src/core/localization/ethiopian_date_time.dart';
import 'package:bingo_app/src/features/support/presentation/support_shortcut.dart';
import 'package:bingo_app/src/core/widgets/responsive_content.dart';
import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/core/widgets/app_status_view.dart';
import 'package:bingo_app/src/core/widgets/section_card.dart';
import 'package:bingo_app/src/features/lobby/data/lobby_repository.dart';
import 'package:bingo_app/src/features/lobby/domain/lobby_models.dart';
import 'package:bingo_app/src/features/game/presentation/session_detail_screen.dart';
import 'package:bingo_app/src/features/lobby/presentation/ticket_preview_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final lobbyRoomsProvider = FutureProvider.autoDispose<List<BingoRoom>>((ref) {
  return ref.read(lobbyRepositoryProvider).getRooms();
});

final roomDetailsProvider = FutureProvider.autoDispose.family<BingoRoom, int>((
  ref,
  roomId,
) {
  return ref.read(lobbyRepositoryProvider).getRoom(roomId);
});

final roomSessionsProvider = FutureProvider.autoDispose
    .family<List<GameSession>, int>((ref, roomId) {
      return ref.read(lobbyRepositoryProvider).getRoomSessions(roomId);
    });

class LobbyScreen extends ConsumerWidget {
  const LobbyScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final localizations = AppLocalizations.of(context)!;
    final roomsAsync = ref.watch(lobbyRoomsProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(localizations.roomsTitle),
        actions: [
          const SupportShortcut(),
          IconButton(
            key: const ValueKey('lobby-refresh-button'),
            tooltip: localizations.refresh,
            onPressed: () => ref.invalidate(lobbyRoomsProvider),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: ResponsiveContent(
        child: roomsAsync.when(
          skipLoadingOnRefresh: false,
          loading: () => Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const CircularProgressIndicator(),
                const SizedBox(height: 16),
                Text(localizations.loading),
              ],
            ),
          ),
          error: (error, stack) => AppStatusView(
            title: localizations.errorGeneric,
            message: localizations.lobbyLoadError,
            onAction: () => ref.invalidate(lobbyRoomsProvider),
          ),
          data: (rooms) => RefreshIndicator(
            onRefresh: () async => ref.invalidate(lobbyRoomsProvider),
            child: rooms.isEmpty
                ? ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    children: [
                      const SizedBox(height: 120),
                      AppStatusView(
                        title: localizations.lobbyEmptyTitle,
                        message: localizations.lobbyEmptyBody,
                      ),
                    ],
                  )
                : ListView.builder(
                    key: const ValueKey('lobby-rooms-list'),
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.all(16),
                    itemCount: rooms.length,
                    itemBuilder: (context, index) {
                      final room = rooms[index];
                      final colors = Theme.of(context).colorScheme;
                      final isDark =
                          Theme.of(context).brightness == Brightness.dark;
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: SectionCard(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Expanded(
                                    child: Text(
                                      room.name,
                                      style: Theme.of(
                                        context,
                                      ).textTheme.titleLarge,
                                    ),
                                  ),
                                  Container(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 10,
                                      vertical: 6,
                                    ),
                                    decoration: BoxDecoration(
                                      color: room.active
                                          ? (isDark
                                                ? colors.secondaryContainer
                                                : const Color(0xFFEAF8EE))
                                          : (isDark
                                                ? colors.surfaceContainerHigh
                                                : const Color(0xFFF1F3F7)),
                                      borderRadius: BorderRadius.circular(20),
                                    ),
                                    child: Text(
                                      room.active
                                          ? localizations.roomStatusActive
                                          : localizations.roomStatusInactive,
                                      style: TextStyle(
                                        fontWeight: FontWeight.w700,
                                        fontSize: 12,
                                        color: room.active
                                            ? (isDark
                                                  ? colors.onSecondaryContainer
                                                  : const Color(0xFF167639))
                                            : (isDark
                                                  ? colors.onSurfaceVariant
                                                  : const Color(0xFF667084)),
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                              if (room.description?.isNotEmpty == true) ...[
                                const SizedBox(height: 8),
                                Text(room.description!),
                              ],
                              const SizedBox(height: 12),
                              Text(
                                '${room.ticketPrice} ${room.currency}',
                                style: Theme.of(
                                  context,
                                ).textTheme.headlineSmall,
                              ),
                              Text(
                                localizations.roomPriceLabel,
                                style: Theme.of(context).textTheme.bodySmall,
                              ),
                              const SizedBox(height: 16),
                              Wrap(
                                spacing: 12,
                                runSpacing: 8,
                                children: [
                                  _InfoChip(
                                    label: localizations.roomCapacityLabel,
                                    value: room.sessionCapacity.toString(),
                                  ),
                                  _InfoChip(
                                    label: localizations.roomScheduleLabel,
                                    value: room.scheduleRule,
                                  ),
                                ],
                              ),
                              const SizedBox(height: 12),
                              SizedBox(
                                width: double.infinity,
                                child: FilledButton.icon(
                                  key: ValueKey('room-details-${room.id}'),
                                  onPressed: () => Navigator.of(context).push(
                                    MaterialPageRoute<void>(
                                      builder: (_) =>
                                          RoomDetailsScreen(roomId: room.id),
                                    ),
                                  ),
                                  icon: const Icon(Icons.arrow_forward_rounded),
                                  label: Text(localizations.roomDetails),
                                ),
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ),
      ),
    );
  }
}

class RoomDetailsScreen extends ConsumerWidget {
  const RoomDetailsScreen({super.key, required this.roomId});

  final int roomId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final localizations = AppLocalizations.of(context)!;
    final roomAsync = ref.watch(roomDetailsProvider(roomId));
    final sessionsAsync = ref.watch(roomSessionsProvider(roomId));

    return Scaffold(
      appBar: AppBar(title: Text(localizations.roomDetails)),
      body: ResponsiveContent(
        child: roomAsync.when(
          loading: () => Center(child: Text(localizations.loading)),
          error: (error, stack) => AppStatusView(
            title: localizations.errorGeneric,
            message: localizations.roomDetailsLoadError,
            onAction: () {
              ref.invalidate(roomDetailsProvider(roomId));
              ref.invalidate(roomSessionsProvider(roomId));
            },
          ),
          data: (room) => RefreshIndicator(
            onRefresh: () async {
              ref.invalidate(roomDetailsProvider(roomId));
              ref.invalidate(roomSessionsProvider(roomId));
            },
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.all(16),
              children: [
                SectionCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        room.name,
                        key: const ValueKey('room-detail-name'),
                        style: Theme.of(context).textTheme.headlineSmall,
                      ),
                      if (room.description?.isNotEmpty == true) ...[
                        const SizedBox(height: 8),
                        Text(room.description!),
                      ],
                      const SizedBox(height: 12),
                      _DetailLine(
                        localizations.roomPriceLabel,
                        '${room.ticketPrice} ${room.currency}',
                      ),
                      _DetailLine(
                        localizations.roomScheduleLabel,
                        '${room.scheduleRule} (${room.timezone})',
                      ),
                      _DetailLine(
                        localizations.roomCapacityLabel,
                        room.sessionCapacity.toString(),
                      ),
                      _DetailLine(
                        localizations.sessionStatusLabel,
                        localizations.roomStatusActive,
                      ),
                      if (room.configuredPatterns.isNotEmpty)
                        _DetailLine(
                          localizations.gamePatterns,
                          room.configuredPatterns.join(', '),
                        ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                Text(
                  localizations.roomSessions,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 8),
                sessionsAsync.when(
                  loading: () => Padding(
                    padding: const EdgeInsets.all(24),
                    child: Center(child: Text(localizations.loading)),
                  ),
                  error: (error, stack) => AppStatusView(
                    title: localizations.errorGeneric,
                    message: localizations.sessionsLoadError,
                    onAction: () =>
                        ref.invalidate(roomSessionsProvider(roomId)),
                  ),
                  data: (sessions) => sessions.isEmpty
                      ? SectionCard(
                          key: ValueKey('room-sessions-loaded-$roomId'),
                          child: Text(localizations.sessionsEmpty),
                        )
                      : Column(
                          key: ValueKey('room-sessions-loaded-$roomId'),
                          children: sessions
                              .map(
                                (session) => Padding(
                                  padding: const EdgeInsets.only(bottom: 10),
                                  child: _SessionCard(session: session),
                                ),
                              )
                              .toList(),
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

class _SessionCard extends StatelessWidget {
  const _SessionCard({required this.session});

  final GameSession session;

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final isTerminal = const {
      'CANCELLED',
      'CLOSED',
      'SETTLED',
    }.contains(session.state.toUpperCase());
    final hasOpenClaimWindow =
        session.state.toUpperCase() == 'CLOSED' &&
        session.endedAt != null &&
        session.claimWindowSeconds != null &&
        DateTime.now().isBefore(
          session.endedAt!.add(Duration(seconds: session.claimWindowSeconds!)),
        );
    final canOpenLiveGame =
        hasOpenClaimWindow ||
        const {
          'OPEN',
          'STARTED',
          'DRAWING',
        }.contains(session.state.toUpperCase());
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            session.sessionName,
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 6),
          Chip(
            label: Text(
              isTerminal
                  ? localizations.sessionUnavailable(session.state)
                  : localizations.sessionStatus(session.state),
            ),
          ),
          _DetailLine(
            localizations.sessionSchedule,
            EthiopianDateTime.dateTime(
              session.scheduledStartAt,
              languageCode: Localizations.localeOf(context).languageCode,
            ),
          ),
          _DetailLine(
            localizations.roomPriceLabel,
            '${session.ticketPrice} ${session.currency}',
          ),
          _DetailLine(
            localizations.roomCapacityLabel,
            session.sessionCapacity.toString(),
          ),
          if (session.configuredPatterns.isNotEmpty)
            _DetailLine(
              localizations.gamePatterns,
              session.configuredPatterns.join(', '),
            ),
          const SizedBox(height: 8),
          Align(
            alignment: Alignment.centerRight,
            child: OutlinedButton.icon(
              key: ValueKey('ticket-preview-${session.id}'),
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => TicketPreviewScreen(
                    sessionId: session.id,
                    roomId: session.roomId,
                  ),
                ),
              ),
              icon: const Icon(Icons.confirmation_number_outlined),
              label: Text(localizations.ticketPreviewButton),
            ),
          ),
          if (canOpenLiveGame) ...[
            const SizedBox(height: 8),
            Align(
              alignment: Alignment.centerRight,
              child: FilledButton.icon(
                key: ValueKey('live-game-${session.id}'),
                onPressed: () => Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => SessionDetailScreen(session: session),
                  ),
                ),
                icon: const Icon(Icons.sports_esports_outlined),
                label: Text(localizations.liveGameTitle),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _DetailLine extends StatelessWidget {
  const _DetailLine(this.label, this.value);

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 8),
    child: Text('$label: $value'),
  );
}

class _InfoChip extends StatelessWidget {
  const _InfoChip({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
    decoration: BoxDecoration(
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      borderRadius: BorderRadius.circular(12),
    ),
    child: Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: Theme.of(context).textTheme.bodySmall),
        const SizedBox(height: 3),
        Text(value, style: Theme.of(context).textTheme.titleSmall),
      ],
    ),
  );
}
