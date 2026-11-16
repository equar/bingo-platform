import 'package:bingo_player/src/core/localization/ethiopian_date_time.dart';
import 'package:bingo_player/src/features/support/presentation/support_shortcut.dart';
import 'package:bingo_player/src/core/widgets/responsive_content.dart';
import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/core/widgets/app_status_view.dart';
import 'package:bingo_player/src/core/widgets/section_card.dart';
import 'package:bingo_player/src/features/lobby/data/lobby_repository.dart';
import 'package:bingo_player/src/features/lobby/domain/lobby_models.dart';
import 'package:bingo_player/src/features/game/presentation/session_detail_screen.dart';
import 'package:bingo_player/src/features/lobby/presentation/ticket_preview_screen.dart';
import 'package:bingo_player/src/core/config/app_config.dart';
import 'package:bingo_player/src/features/game/application/live_game_controller.dart';
import 'package:bingo_player/src/features/game/data/game_repository.dart';
import 'package:bingo_player/src/features/wallet/data/wallet_repository.dart';
import 'package:bingo_player/src/features/wallet/presentation/wallet_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final lobbyRoomsProvider = FutureProvider.autoDispose<List<BingoRoom>>((ref) {
  return ref.read(lobbyRepositoryProvider).getRooms();
});

final activeSessionsProvider = FutureProvider.autoDispose<List<GameSession>>((
  ref,
) {
  return ref.read(lobbyRepositoryProvider).getSessions();
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
    final sessionsAsync = ref.watch(activeSessionsProvider);
    final purchasesEnabled = ref
        .watch(appConfigProvider)
        .playerTicketPurchasesEnabled;

    return DefaultTabController(
      length: 3,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Bingo'),
          bottom: TabBar(
            isScrollable: true,
            tabs: [
              Tab(text: localizations.liveGamesTab),
              Tab(text: localizations.upcomingGamesTab),
              Tab(text: localizations.previousGamesTab),
            ],
          ),
          actions: [
            const SupportShortcut(),
            IconButton(
              key: const ValueKey('lobby-refresh-button'),
              tooltip: localizations.refresh,
              onPressed: () => ref.invalidate(activeSessionsProvider),
              icon: const Icon(Icons.refresh),
            ),
          ],
        ),
        body: ResponsiveContent(
          child: sessionsAsync.when(
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
              onAction: () => ref.invalidate(activeSessionsProvider),
            ),
            data: (sessions) => TabBarView(
              children: [
                _SessionList(
                  sessions: sessions
                      .where(
                        (s) => const {
                          'STARTED',
                          'DRAWING',
                        }.contains(s.state.toUpperCase()),
                      )
                      .toList(),
                  emptyTitle: localizations.noLiveGames,
                  emptyBody: localizations.lobbyEmptyBody,
                  onRefresh: () => ref.invalidate(activeSessionsProvider),
                  onBuy: null,
                  purchasesEnabled: purchasesEnabled,
                ),
                _SessionList(
                  sessions:
                      sessions
                          .where((s) => s.state.toUpperCase() == 'OPEN')
                          .toList()
                        ..sort(
                          (a, b) =>
                              a.scheduledStartAt.compareTo(b.scheduledStartAt),
                        ),
                  emptyTitle: localizations.noUpcomingGames,
                  emptyBody: localizations.lobbyEmptyBody,
                  onRefresh: () => ref.invalidate(activeSessionsProvider),
                  onBuy: (session) => _buyTickets(context, ref, session),
                  purchasesEnabled: purchasesEnabled,
                ),
                _SessionList(
                  sessions:
                      sessions
                          .where(
                            (s) => const {
                              'CLOSED',
                              'SETTLED',
                              'CANCELLED',
                            }.contains(s.state.toUpperCase()),
                          )
                          .toList()
                        ..sort(
                          (a, b) =>
                              b.scheduledStartAt.compareTo(a.scheduledStartAt),
                        ),
                  emptyTitle: localizations.noPreviousGames,
                  emptyBody: localizations.lobbyEmptyBody,
                  onRefresh: () => ref.invalidate(activeSessionsProvider),
                  onBuy: null,
                  purchasesEnabled: purchasesEnabled,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _SessionList extends StatelessWidget {
  const _SessionList({
    required this.sessions,
    required this.emptyTitle,
    required this.emptyBody,
    required this.onRefresh,
    required this.onBuy,
    required this.purchasesEnabled,
  });
  final List<GameSession> sessions;
  final String emptyTitle;
  final String emptyBody;
  final VoidCallback onRefresh;
  final ValueChanged<GameSession>? onBuy;
  final bool purchasesEnabled;

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    return RefreshIndicator(
      onRefresh: () async => onRefresh(),
      child: sessions.isEmpty
          ? ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: [
                const SizedBox(height: 120),
                AppStatusView(title: emptyTitle, message: emptyBody),
              ],
            )
          : ListView.builder(
              key: ValueKey(
                'player-sessions-${onBuy == null ? 'history' : 'upcoming'}',
              ),
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.all(16),
              itemCount: sessions.length,
              itemBuilder: (context, index) {
                final session = sessions[index];
                final isLive = const {'STARTED', 'DRAWING'}.contains(
                  session.state.toUpperCase(),
                );
                return Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: SectionCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          session.sessionName,
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
                        Text(session.roomName),
                        const SizedBox(height: 8),
                        Wrap(
                          spacing: 16,
                          children: [
                            _InfoChip(
                              label: localizations.sessionStatusLabel,
                              value: session.state,
                            ),
                            _InfoChip(
                              label: localizations.roomPriceLabel,
                              value:
                                  '${session.ticketPrice} ${session.currency}',
                            ),
                            _InfoChip(
                              label: localizations.availableSeatsLabel,
                              value: session.availableSeats.toString(),
                            ),
                          ],
                        ),
                        const SizedBox(height: 10),
                        Row(
                          children: [
                            Expanded(
                              child: OutlinedButton(
                                key: ValueKey('session-details-${session.id}'),
                                onPressed: () => Navigator.of(context).push(
                                  MaterialPageRoute<void>(
                                    builder: (_) =>
                                        const GameRulesScreen(),
                                  ),
                                ),
                                child: Text(localizations.gameRules),
                              ),
                            ),
                            if (onBuy != null) ...[
                              const SizedBox(width: 8),
                              Expanded(
                                child: FilledButton(
                                  key: ValueKey('buy-tickets-${session.id}'),
                                  onPressed:
                                      purchasesEnabled &&
                                          session.availableSeats > 0
                                      ? () => onBuy!(session)
                                      : null,
                                  child: Text(localizations.buyTicketsButton),
                                ),
                              ),
                            ] else if (isLive) ...[
                              const SizedBox(width: 8),
                              Expanded(
                                child: FilledButton.icon(
                                  key: ValueKey('live-game-${session.id}'),
                                  onPressed: () =>
                                      Navigator.of(context).push(
                                        MaterialPageRoute<void>(
                                          builder: (_) => SessionDetailScreen(
                                            session: session,
                                          ),
                                        ),
                                      ),
                                  icon: const Icon(
                                    Icons.sports_esports_outlined,
                                  ),
                                  label: Text(localizations.liveGameTitle),
                                ),
                              ),
                            ],
                          ],
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
    );
  }
}

Future<void> _buyTickets(
  BuildContext context,
  WidgetRef ref,
  GameSession session,
) async {
  final localizations = AppLocalizations.of(context)!;
  final int owned;
  try {
    owned =
        (await ref
                .read(gameRepositoryProvider)
                .getMyTickets(session.id, page: 0, size: 1))
            .totalElements;
  } catch (_) {
    if (context.mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(localizations.errorGeneric)));
    }
    return;
  }
  final remainingPlayerLimit = session.maxTicketsPerPlayer == null
      ? 10
      : (session.maxTicketsPerPlayer! - owned).clamp(0, 10);
  final maxQuantity = [
    10,
    session.availableSeats,
    remainingPlayerLimit,
  ].reduce((a, b) => a < b ? a : b);
  if (maxQuantity == 0) {
    if (context.mounted) {
      final message = session.availableSeats == 0
          ? localizations.ticketsSoldOutTitle
          : localizations.ticketLimitReachedTitle;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(message)));
    }
    return;
  }
  if (!context.mounted) return;
  var quantity = 1;
  final selected = await showDialog<int>(
    context: context,
    builder: (dialogContext) => StatefulBuilder(
      builder: (context, setState) => AlertDialog(
        title: Text(localizations.purchaseTicketTitle),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            DropdownButton<int>(
              value: quantity,
              items: [
                for (var i = 1; i <= maxQuantity; i++)
                  DropdownMenuItem(
                    value: i,
                    child: Text(localizations.purchaseQuantityLabel(i)),
                  ),
              ],
              onChanged: (value) {
                if (value != null) setState(() => quantity = value);
              },
            ),
            Text(
              localizations.purchaseTotalLabel(
                (session.ticketPrice * quantity).toStringAsFixed(2),
                session.currency,
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: Text(localizations.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, quantity),
            child: Text(localizations.buyTicketsButton),
          ),
        ],
      ),
    ),
  );
  if (selected == null || !context.mounted) return;
  try {
    final wallet = await ref.read(walletRepositoryProvider).getBalance();
    final total = session.ticketPrice * selected;
    if (wallet.totalBalance < total) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              localizations.insufficientTicketBalance(
                total.toStringAsFixed(2),
                session.currency,
              ),
            ),
          ),
        );
      }
      return;
    }
    final result = await ref
        .read(liveGameControllerProvider(session.id).notifier)
        .purchaseTickets(selected);
    ref.invalidate(walletBalanceProvider);
    ref.invalidate(activeSessionsProvider);
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          result.error == null
              ? localizations.ticketsPurchasedCount(result.purchasedCount)
              : '${result.purchasedCount > 0 ? '${localizations.ticketsPurchasedCount(result.purchasedCount)}. ' : ''}${result.error!.userMessage(localizations)}',
        ),
      ),
    );
  } catch (_) {
    if (context.mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(localizations.errorGeneric)));
    }
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
