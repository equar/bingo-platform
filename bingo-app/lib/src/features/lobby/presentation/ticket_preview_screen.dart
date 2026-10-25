import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/core/widgets/app_status_view.dart';
import 'package:bingo_app/src/core/widgets/section_card.dart';
import 'package:bingo_app/src/features/lobby/data/lobby_repository.dart';
import 'package:bingo_app/src/features/lobby/domain/lobby_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class _TicketPreviewData {
  const _TicketPreviewData({required this.session, required this.room});

  final GameSession session;
  final BingoRoom room;
}

final ticketPreviewProvider = FutureProvider.autoDispose
    .family<_TicketPreviewData, (int, int)>((ref, ids) async {
  final repository = ref.read(lobbyRepositoryProvider);
  final results = await Future.wait([
    repository.getSession(ids.$1),
    repository.getRoom(ids.$2),
  ]);
  return _TicketPreviewData(
    session: results[0] as GameSession,
    room: results[1] as BingoRoom,
  );
});

class TicketPreviewScreen extends ConsumerWidget {
  const TicketPreviewScreen({
    super.key,
    required this.sessionId,
    required this.roomId,
  });

  final int sessionId;
  final int roomId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final preview = ref.watch(ticketPreviewProvider((sessionId, roomId)));

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.ticketPreviewTitle),
        actions: [
          IconButton(
            key: const ValueKey('ticket-preview-refresh'),
            tooltip: l10n.refresh,
            onPressed: () => ref.invalidate(
              ticketPreviewProvider((sessionId, roomId)),
            ),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: preview.when(
        loading: () => Center(child: Text(l10n.loading)),
        error: (error, stack) => AppStatusView(
          title: l10n.errorGeneric,
          message: l10n.ticketPreviewLoadError,
          onAction: () => ref.invalidate(
            ticketPreviewProvider((sessionId, roomId)),
          ),
        ),
        data: (data) {
          final session = data.session;
          final activeRoom = data.room.active;
          final isOpen = session.state.toUpperCase() == 'OPEN';
          final hasCapacity = session.availableSeats > 0;

          return RefreshIndicator(
            onRefresh: () async => ref.invalidate(
              ticketPreviewProvider((sessionId, roomId)),
            ),
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.all(16),
              children: [
                SectionCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(session.sessionName,
                          style: Theme.of(context).textTheme.titleLarge),
                      const SizedBox(height: 8),
                      Text(session.roomName),
                      const SizedBox(height: 12),
                      _PreviewLine(
                        label: l10n.roomPriceLabel,
                        value: '${session.ticketPrice} ${session.currency}',
                        key: const ValueKey('ticket-preview-price'),
                      ),
                      _PreviewLine(
                        label: l10n.sessionStatusLabel,
                        value: session.state,
                      ),
                      _PreviewLine(
                        label: l10n.ticketAvailabilityLabel,
                        value:
                            '${session.availableSeats} / ${session.sessionCapacity}',
                        key: const ValueKey('ticket-preview-availability'),
                      ),
                      _PreviewLine(
                        label: l10n.gamePatterns,
                        value: session.configuredPatterns.isEmpty
                            ? l10n.ticketPatternsUnavailable
                            : session.configuredPatterns.join(', '),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                SectionCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(l10n.ticketCardModel,
                          style: Theme.of(context).textTheme.titleMedium),
                      const SizedBox(height: 8),
                      Text(l10n.ticketGeneratedAtPurchase),
                      const SizedBox(height: 12),
                      Row(
                        children: [
                          for (final column in const [
                            ('B', '1–15'),
                            ('I', '16–30'),
                            ('N', '31–45'),
                            ('G', '46–60'),
                            ('O', '61–75'),
                          ])
                            Expanded(
                              child: Column(
                                children: [
                                  Text(column.$1,
                                      style: Theme.of(context)
                                          .textTheme
                                          .titleMedium),
                                  Text(column.$2,
                                      textAlign: TextAlign.center,
                                      style: Theme.of(context)
                                          .textTheme
                                          .bodySmall),
                                ],
                              ),
                            ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text(l10n.ticketFreeCenter),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                SectionCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(l10n.ticketEligibilityTitle,
                          style: Theme.of(context).textTheme.titleMedium),
                      const SizedBox(height: 8),
                      Text(!activeRoom
                          ? l10n.ticketRoomInactive
                          : !isOpen
                              ? l10n.ticketSessionIneligible(session.state)
                              : !hasCapacity
                                  ? l10n.ticketSessionFull
                                  : l10n.ticketSessionAvailable),
                      const SizedBox(height: 12),
                      SizedBox(
                        width: double.infinity,
                        child: FilledButton.icon(
                          key: const ValueKey('ticket-preview-purchase-disabled'),
                          onPressed: null,
                          icon: const Icon(Icons.lock_outline),
                          label: Text(l10n.purchaseTicket),
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(l10n.ticketPurchaseDisabledBody),
                    ],
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _PreviewLine extends StatelessWidget {
  const _PreviewLine({super.key, required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(top: 6),
        child: Text('$label: $value'),
      );
}
