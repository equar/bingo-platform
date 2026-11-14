import 'package:bingo_player/src/core/localization/ethiopian_date_time.dart';
import 'package:bingo_player/src/features/support/presentation/support_shortcut.dart';
import 'package:bingo_player/src/core/widgets/responsive_content.dart';
import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/core/widgets/app_status_view.dart';
import 'package:bingo_player/src/core/widgets/section_card.dart';
import 'package:bingo_player/src/features/lobby/data/lobby_repository.dart';
import 'package:bingo_player/src/features/lobby/domain/lobby_models.dart';
import 'package:bingo_player/src/features/game/presentation/session_detail_screen.dart';
import 'package:bingo_player/src/features/game/application/live_game_controller.dart';
import 'package:bingo_player/src/core/config/app_config.dart';
import 'package:bingo_player/src/features/lobby/presentation/ticket_preview_screen.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final lobbyRoomsProvider = FutureProvider.autoDispose<List<BingoRoom>>((ref) {
  return ref.read(lobbyRepositoryProvider).getRooms();
});

final activeSessionsProvider = FutureProvider.autoDispose<List<GameSession>>((
  ref,
) {
  return ref.read(lobbyRepositoryProvider).getActiveSessions();
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

    return Scaffold(
      appBar: AppBar(
        title: const Text('Bingo'),
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
          data: (sessions) => RefreshIndicator(
            onRefresh: () async => ref.invalidate(activeSessionsProvider),
            child: sessions.isEmpty
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
                    key: const ValueKey('lobby-active-sessions-list'),
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.all(16),
                    itemCount: sessions.length + 1,
                    itemBuilder: (context, index) {
                      if (index == 0) {
                        return Padding(
                          padding: const EdgeInsets.only(bottom: 16),
                          child: Row(
                            children: [
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      'Bingo',
                                      style: Theme.of(
                                        context,
                                      ).textTheme.headlineMedium,
                                    ),
                                    const SizedBox(height: 2),
                                    Text(
                                      'Choose a session to play',
                                      style: Theme.of(
                                        context,
                                      ).textTheme.bodyMedium,
                                    ),
                                  ],
                                ),
                              ),
                              Chip(label: Text('${sessions.length} Available')),
                            ],
                          ),
                        );
                      }
                      final session = sessions[index - 1];
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
                                      session.sessionName,
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
                                      color: isDark
                                          ? colors.secondaryContainer
                                          : const Color(0xFFEAF8EE),
                                      borderRadius: BorderRadius.circular(20),
                                    ),
                                    child: Text(
                                      localizations.roomStatusActive,
                                      style: TextStyle(
                                        fontWeight: FontWeight.w700,
                                        fontSize: 12,
                                        color: isDark
                                            ? colors.onSecondaryContainer
                                            : const Color(0xFF167639),
                                      ),
                                    ),
                                  ),
                                  const SizedBox(width: 4),
                                  IconButton(
                                    tooltip: 'Play or view tickets',
                                    onPressed: () => Navigator.of(context).push(
                                      MaterialPageRoute<void>(
                                        builder: (_) => SessionDetailScreen(
                                          session: session,
                                        ),
                                      ),
                                    ),
                                    icon: Icon(
                                      Icons.chevron_right_rounded,
                                      color: colors.onSurfaceVariant,
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 12),
                              Row(
                                children: [
                                  Expanded(
                                    child: _LobbyMetric(
                                      label: 'Ticket',
                                      value:
                                          '${session.ticketPrice} ${session.currency}',
                                    ),
                                  ),
                                  Expanded(
                                    child: _LobbyMetric(
                                      label: 'Prize',
                                      value:
                                          '${session.configuredPrizes.fold<num>(0, (total, prize) => total + prize.amount)} ${session.currency}',
                                    ),
                                  ),
                                  Expanded(
                                    child: _LobbyMetric(
                                      label: localizations.ticketCountLabel,
                                      value: session.ticketCount.toString(),
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 12),
                              Row(
                                children: [
                                  Expanded(
                                    child: OutlinedButton(
                                      style: OutlinedButton.styleFrom(
                                        minimumSize: const Size.fromHeight(44),
                                        textStyle: Theme.of(
                                          context,
                                        ).textTheme.labelLarge,
                                      ),
                                      onPressed: () =>
                                          Navigator.of(context).push(
                                            MaterialPageRoute<void>(
                                              builder: (_) =>
                                                  TicketPreviewScreen(
                                                    sessionId: session.id,
                                                    roomId: session.roomId,
                                                  ),
                                            ),
                                          ),
                                      child: const Text('Details & Rules'),
                                    ),
                                  ),
                                  const SizedBox(width: 12),
                                  Expanded(
                                    child: FilledButton(
                                      style: FilledButton.styleFrom(
                                        minimumSize: const Size.fromHeight(44),
                                        textStyle: Theme.of(
                                          context,
                                        ).textTheme.labelLarge,
                                      ),
                                      onPressed: () => _showPurchaseSheet(
                                        context,
                                        ref,
                                        session,
                                      ),
                                      child: const Text('Buy Tickets'),
                                    ),
                                  ),
                                ],
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

  Future<void> _showPurchaseSheet(
    BuildContext context,
    WidgetRef ref,
    GameSession session,
  ) async {
    final localizations = AppLocalizations.of(context)!;
    if (!ref.read(appConfigProvider).playerTicketPurchasesEnabled) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Ticket purchases are unavailable in this app build.'),
        ),
      );
      return;
    }
    late final GameSession purchaseSession;
    try {
      purchaseSession = await ref
          .read(lobbyRepositoryProvider)
          .getSession(session.id);
    } on DioException catch (error) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error.message ?? localizations.errorGeneric)),
      );
      return;
    }
    if (!context.mounted) return;
    var quantity = 1;
    final configuredLimit = purchaseSession.maxTicketsPerPlayer;
    if (configuredLimit == null || configuredLimit < 1) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Ticket purchase configuration is unavailable for this session.',
          ),
        ),
      );
      return;
    }
    final controllerSubscription = ref.listenManual(
      liveGameControllerProvider(session.id),
      (_, _) {},
    );
    final ownedTickets =
        ref
            .read(liveGameControllerProvider(session.id))
            .ticketPage
            ?.totalElements ??
        0;
    final maximum = [
      purchaseSession.availableSeats,
      (configuredLimit - ownedTickets).clamp(0, configuredLimit),
    ].reduce((left, right) => left < right ? left : right);
    if (maximum < 1) {
      controllerSubscription.close();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            ownedTickets >= configuredLimit
                ? 'You have reached the $configuredLimit-ticket limit for this game.'
                : localizations.errorGeneric,
          ),
        ),
      );
      return;
    }
    var submitting = false;
    String? outcomeMessage;
    var purchaseOutcomeUncertain = false;
    try {
      await showModalBottomSheet<void>(
        context: context,
        showDragHandle: true,
        builder: (sheetContext) => StatefulBuilder(
          builder: (context, setSheetState) => SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    purchaseSession.sessionName,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '${purchaseSession.ticketPrice} ${purchaseSession.currency} per ticket',
                  ),
                  const SizedBox(height: 20),
                  Row(
                    children: [
                      IconButton(
                        onPressed: submitting || quantity == 1
                            ? null
                            : () => setSheetState(() => quantity--),
                        icon: const Icon(Icons.remove_circle_outline),
                      ),
                      Expanded(
                        child: Text(
                          quantity.toString(),
                          textAlign: TextAlign.center,
                          style: Theme.of(context).textTheme.headlineMedium,
                        ),
                      ),
                      IconButton(
                        onPressed: submitting || quantity == maximum
                            ? null
                            : () => setSheetState(() => quantity++),
                        icon: const Icon(Icons.add_circle_outline),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Text(
                    'Total: ${(purchaseSession.ticketPrice * quantity).toStringAsFixed(2)} ${purchaseSession.currency}',
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  if (outcomeMessage != null) ...[
                    const SizedBox(height: 16),
                    Text(
                      outcomeMessage!,
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ],
                  const SizedBox(height: 16),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton(
                      onPressed: submitting
                          ? null
                          : () async {
                              if (!ref
                                  .read(appConfigProvider)
                                  .playerTicketPurchasesEnabled) {
                                return;
                              }
                              setSheetState(() => submitting = true);
                              final result = await ref
                                  .read(
                                    liveGameControllerProvider(
                                      session.id,
                                    ).notifier,
                                  )
                                  .purchaseTickets(quantity);
                              if (!sheetContext.mounted) return;
                              final errorMessage = result.error?.userMessage(
                                AppLocalizations.of(sheetContext)!,
                              );
                              setSheetState(() {
                                submitting = false;
                                purchaseOutcomeUncertain =
                                    result.error?.outcomeUnknown == true;
                                outcomeMessage = [
                                  if (result.purchasedCount > 0)
                                    'Purchased ${result.purchasedCount} ticket${result.purchasedCount == 1 ? '' : 's'}.',
                                  if (errorMessage != null) errorMessage,
                                ].join(' ');
                              });
                            },
                      child: const Text('Buy Tickets'),
                    ),
                  ),
                  if (purchaseOutcomeUncertain)
                    SizedBox(
                      width: double.infinity,
                      child: TextButton(
                        onPressed: submitting
                            ? null
                            : () async {
                                setSheetState(() => submitting = true);
                                final error = await ref
                                    .read(
                                      liveGameControllerProvider(
                                        session.id,
                                      ).notifier,
                                    )
                                    .resolvePendingPurchase();
                                if (!sheetContext.mounted) return;
                                setSheetState(() {
                                  submitting = false;
                                  purchaseOutcomeUncertain =
                                      error?.outcomeUnknown == true;
                                  outcomeMessage = error == null
                                      ? 'Purchase recovery completed. Your tickets have been refreshed.'
                                      : error.userMessage(
                                          AppLocalizations.of(sheetContext)!,
                                        );
                                });
                              },
                        child: const Text('Resolve purchase'),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
      );
    } finally {
      controllerSubscription.close();
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

class _LobbyMetric extends StatelessWidget {
  const _LobbyMetric({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text(label, style: Theme.of(context).textTheme.bodySmall),
      const SizedBox(height: 4),
      Text(
        value,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: Theme.of(context).textTheme.titleMedium,
      ),
    ],
  );
}
