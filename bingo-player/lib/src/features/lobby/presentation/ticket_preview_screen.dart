import 'package:bingo_player/src/core/widgets/responsive_content.dart';
import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/core/widgets/app_status_view.dart';
import 'package:bingo_player/src/core/widgets/section_card.dart';
import 'package:bingo_player/src/features/lobby/data/lobby_repository.dart';
import 'package:bingo_player/src/features/lobby/domain/lobby_models.dart';
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

class GameRulesScreen extends StatelessWidget {
  const GameRulesScreen({super.key});

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Game Rules')),
    body: ResponsiveContent(
      child: ListView(
        key: ValueKey('game-rules-scroll'),
        physics: AlwaysScrollableScrollPhysics(),
        padding: EdgeInsets.all(16),
        children: [_GameRulesContent()],
      ),
    ),
  );
}

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
        title: Text(l10n.gameRules),
        actions: [
          IconButton(
            key: const ValueKey('ticket-preview-refresh'),
            tooltip: l10n.refresh,
            onPressed: () =>
                ref.invalidate(ticketPreviewProvider((sessionId, roomId))),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: ResponsiveContent(
        child: preview.when(
          loading: () => Center(child: Text(l10n.loading)),
          error: (error, stack) => AppStatusView(
            title: l10n.errorGeneric,
            message: l10n.ticketPreviewLoadError,
            onAction: () =>
                ref.invalidate(ticketPreviewProvider((sessionId, roomId))),
          ),
          data: (data) {
            final session = data.session;
            final activeRoom = data.room.active;
            final isOpen = session.state.toUpperCase() == 'OPEN';
            final hasCapacity = session.availableSeats > 0;

            return RefreshIndicator(
              onRefresh: () async =>
                  ref.invalidate(ticketPreviewProvider((sessionId, roomId))),
              child: ListView(
                key: const ValueKey('game-rules-scroll'),
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.all(16),
                children: [
                  SectionCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          session.sessionName,
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
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
                        if ((data.room.description ?? '').isNotEmpty) ...[
                          const SizedBox(height: 12),
                          Text(
                            'Game rules',
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                          const SizedBox(height: 4),
                          Text(data.room.description!),
                        ],
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),
                  const _GameRulesContent(),
                  const SizedBox(height: 12),
                  Text(l10n.earliestSplitRule),
                  if (session.claimWindowSeconds != null)
                    Text(
                      l10n.gameClaimWindow(
                        (session.claimWindowSeconds! / 3600).toStringAsFixed(1),
                      ),
                    ),
                  for (final prize in session.configuredPrizes)
                    Text(
                      '${prize.prizeType}: ${prize.amount} ${session.currency}',
                    ),
                  if (session.configuredPrizes.any(
                    (prize) => prize.settlementPath == 'AGENT_CASH',
                  ))
                    Text(
                      l10n.prizeCashUnresolved,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                  const SizedBox(height: 12),
                  SectionCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          l10n.ticketCardModel,
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
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
                                    Text(
                                      column.$1,
                                      style: Theme.of(
                                        context,
                                      ).textTheme.titleMedium,
                                    ),
                                    Text(
                                      column.$2,
                                      textAlign: TextAlign.center,
                                      style: Theme.of(
                                        context,
                                      ).textTheme.bodySmall,
                                    ),
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
                        Text(
                          l10n.ticketEligibilityTitle,
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        const SizedBox(height: 8),
                        Text(
                          !activeRoom
                              ? l10n.ticketRoomInactive
                              : !isOpen
                              ? l10n.ticketSessionIneligible(session.state)
                              : !hasCapacity
                              ? l10n.ticketSessionFull
                              : l10n.ticketSessionAvailable,
                        ),
                        const SizedBox(height: 12),
                        SizedBox(
                          width: double.infinity,
                          child: FilledButton.icon(
                            key: const ValueKey(
                              'ticket-preview-purchase-disabled',
                            ),
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

class _GameRulesContent extends StatelessWidget {
  const _GameRulesContent();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('General Game Rule', style: theme.textTheme.titleLarge),
          const Divider(height: 32),
          Text('How to play', style: theme.textTheme.titleLarge),
          const SizedBox(height: 12),
          const _RuleStep(
            number: '1',
            text: 'Buy tickets before ticket sales close.',
          ),
          const _RuleStep(
            number: '2',
            text: 'Each ticket has a 5 × 5 grid. The center square is FREE.',
          ),
          const _RuleStep(
            number: '3',
            text:
                'Numbers are called automatically and matching numbers are marked on your tickets.',
          ),
          const _RuleStep(
            number: '4',
            text:
                'Complete an eligible winning pattern to win. Winning tickets are checked automatically.',
          ),
          const SizedBox(height: 20),
          Text('Winning patterns', style: theme.textTheme.titleLarge),
          const SizedBox(height: 4),
          Text(
            'Complete one of the winning patterns shown below.',
            style: theme.textTheme.bodyMedium,
          ),
          const SizedBox(height: 12),
          const Wrap(
            spacing: 12,
            runSpacing: 16,
            alignment: WrapAlignment.spaceEvenly,
            children: [
              _WinningPatternDiagram(
                pattern: _WinningPattern.horizontal,
                label: 'Horizontal',
              ),
              _WinningPatternDiagram(
                pattern: _WinningPattern.vertical,
                label: 'Vertical',
              ),
              _WinningPatternDiagram(
                pattern: _WinningPattern.diagonal,
                label: 'Diagonal',
              ),
              _WinningPatternDiagram(
                pattern: _WinningPattern.fullHouse,
                label: 'Full House',
              ),
            ],
          ),
          const SizedBox(height: 20),
          Text('Full House', style: theme.textTheme.titleMedium),
          const SizedBox(height: 4),
          const Text(
            'Mark every number on your ticket. The FREE center square already counts as marked.',
          ),
          const Divider(height: 32),
          Text(
            'Prize sharing when there is a tie',
            style: theme.textTheme.titleLarge,
          ),
          const SizedBox(height: 8),
          const Text(
            'If two or more tickets win on the same called number, the prize is split equally among all winning tickets. '
            'Each winning ticket counts separately, even when one player owns multiple winning tickets.',
          ),
          const SizedBox(height: 12),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: theme.colorScheme.secondaryContainer,
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Text(
              'Example: 10,000 ETB prize\n\n'
              '1 winning ticket: 10,000 ETB\n'
              '2 winning tickets: 5,000 ETB each\n'
              '5 winning tickets: 2,000 ETB each',
            ),
          ),
          const Divider(height: 32),
          Text('Important rules', style: theme.textTheme.titleLarge),
          const SizedBox(height: 8),
          const _RuleBullet(
            'Tickets cannot be changed after the game starts.',
          ),
          const _RuleBullet(
            'If you disconnect, your tickets and called numbers remain recorded.',
          ),
          const _RuleBullet(
            'The game system verifies winning tickets and determines the official result.',
          ),
        ],
      ),
    );
  }
}

class _RuleStep extends StatelessWidget {
  const _RuleStep({required this.number, required this.text});

  final String number;
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 8),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 28,
          child: Text(
            '$number.',
            style: Theme.of(context).textTheme.titleSmall,
          ),
        ),
        Expanded(child: Text(text)),
      ],
    ),
  );
}

class _RuleBullet extends StatelessWidget {
  const _RuleBullet(this.text);

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 8),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Padding(
          padding: EdgeInsets.only(top: 7, right: 10),
          child: Icon(Icons.circle, size: 6),
        ),
        Expanded(child: Text(text)),
      ],
    ),
  );
}

enum _WinningPattern { horizontal, vertical, diagonal, fullHouse }

class _WinningPatternDiagram extends StatelessWidget {
  const _WinningPatternDiagram({required this.pattern, required this.label});

  final _WinningPattern pattern;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: '$label winning pattern',
      child: SizedBox(
        width: 132,
        child: Column(
          children: [
            SizedBox(
              height: 132,
              child: Column(
                children: [
                  for (var row = 0; row < 5; row++)
                    Expanded(
                      child: Row(
                        children: [
                          for (var column = 0; column < 5; column++)
                            Expanded(
                              child: Padding(
                                padding: const EdgeInsets.all(1.5),
                                child: _PatternCell(
                                  key: ValueKey(
                                    'winning-pattern-${pattern.name}-${row * 5 + column}',
                                  ),
                                  marked: switch (pattern) {
                                    _WinningPattern.horizontal => row == 2,
                                    _WinningPattern.vertical => column == 2,
                                    _WinningPattern.diagonal => row == column,
                                    _WinningPattern.fullHouse => true,
                                  },
                                  isFreeCenter: row == 2 && column == 2,
                                ),
                              ),
                            ),
                        ],
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            Text(label, style: Theme.of(context).textTheme.titleSmall),
          ],
        ),
      ),
    );
  }
}

class _PatternCell extends StatelessWidget {
  const _PatternCell({
    super.key,
    required this.marked,
    required this.isFreeCenter,
  });

  final bool marked;
  final bool isFreeCenter;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Container(
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: marked ? colors.primary : colors.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(3),
      ),
      child: isFreeCenter
          ? Text(
              'FREE',
              style: Theme.of(context).textTheme.labelSmall?.copyWith(
                color: marked ? colors.onPrimary : colors.onSurfaceVariant,
                fontSize: 7,
              ),
            )
          : null,
    );
  }
}

