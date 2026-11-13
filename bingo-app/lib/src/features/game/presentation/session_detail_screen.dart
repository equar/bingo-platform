import 'dart:async';
import 'dart:math' as math;

import 'live_call_panel.dart';
import 'winner_celebration_dialog.dart';

import 'package:bingo_app/src/core/widgets/responsive_content.dart';

import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/core/config/app_config.dart';
import 'package:bingo_app/src/core/storage/player_preferences.dart';
import 'package:bingo_app/src/core/widgets/app_status_view.dart';
import 'package:bingo_app/src/core/widgets/section_card.dart';
import 'package:bingo_app/src/features/game/application/live_game_controller.dart';
import 'package:bingo_app/src/features/game/domain/live_game_models.dart';
import 'package:bingo_app/src/features/game/domain/ticket_models.dart';
import 'package:bingo_app/src/features/lobby/domain/lobby_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class SessionDetailScreen extends ConsumerStatefulWidget {
  const SessionDetailScreen({super.key, required this.session});

  final GameSession session;

  @override
  ConsumerState<SessionDetailScreen> createState() =>
      _SessionDetailScreenState();
}

class _SessionDetailScreenState extends ConsumerState<SessionDetailScreen>
    with WidgetsBindingObserver {
  final Set<String> _celebratedWins = {};
  bool _celebrationQueued = false;

  Set<String> _winKeys(List<TicketResponse> tickets) => {
    for (final ticket in tickets)
      for (final pattern in ticket.confirmedPrizes.keys)
        '${ticket.id}:$pattern',
  };

  void _maybeCelebrate(LiveGameState state) {
    if (_celebrationQueued ||
        state.isLoading ||
        state.session?.state == 'CANCELLED' ||
        _winKeys(state.winningTickets).difference(_celebratedWins).isEmpty) {
      return;
    }
    _celebrationQueued = true;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      final latest = ref.read(liveGameControllerProvider(widget.session.id));
      final tickets = latest.winningTickets;
      if (tickets.isNotEmpty && latest.session?.state != 'CANCELLED') {
        _celebratedWins.addAll(_winKeys(tickets));
        await showDialog<void>(
          context: context,
          barrierDismissible: false,
          builder: (context) => WinnerCelebrationDialog(
            tickets: tickets,
            totalTickets: latest.ticketPage?.totalElements ?? tickets.length,
          ),
        );
      }
      _celebrationQueued = false;
      if (mounted) {
        _maybeCelebrate(
          ref.read(liveGameControllerProvider(widget.session.id)),
        );
      }
    });
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final controller = ref.read(
      liveGameControllerProvider(widget.session.id).notifier,
    );
    if (state == AppLifecycleState.resumed) {
      unawaited(controller.resume());
    } else if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.inactive) {
      unawaited(controller.pause());
    }
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final state = ref.watch(liveGameControllerProvider(widget.session.id));
    ref.listen(liveGameControllerProvider(widget.session.id), (previous, next) {
      if (ref.read(playerPreferencesProvider).soundEnabled &&
          previous != null &&
          next.draws.length > previous.draws.length) {
        SystemSound.play(SystemSoundType.click);
      }
    });
    _maybeCelebrate(state);
    final controller = ref.read(
      liveGameControllerProvider(widget.session.id).notifier,
    );
    final purchasesEnabled = ref
        .watch(appConfigProvider)
        .playerTicketPurchasesEnabled;
    final playerPreferences = ref.watch(playerPreferencesProvider);
    final session = state.session ?? widget.session;
    final isPreGame = session.state == 'DRAFT' || session.state == 'OPEN';

    return Scaffold(
      appBar: AppBar(
        title: Text(localizations.liveGameTitle),
        actions: [
          IconButton(
            tooltip: 'Game settings',
            icon: const Icon(Icons.tune_rounded),
            onPressed: () => showModalBottomSheet<void>(
              context: context,
              isScrollControlled: true,
              useSafeArea: true,
              showDragHandle: true,
              builder: (_) => _GameSettingsSheet(
                preferences: playerPreferences,
                activeTicketId: state.selectedTicket?.id,
              ),
            ),
          ),
          IconButton(
            tooltip: 'Sort cards',
            icon: const Icon(Icons.sort_rounded),
            onPressed: () => showModalBottomSheet<void>(
              context: context,
              isScrollControlled: true,
              useSafeArea: true,
              showDragHandle: true,
              builder: (_) => _SortCardsSheet(preferences: playerPreferences),
            ),
          ),
          if (!isPreGame)
            IconButton(
              key: const ValueKey('live-game-details'),
              tooltip: localizations.liveGameDetails,
              icon: const Icon(Icons.info_outline),
              onPressed: () => showModalBottomSheet<void>(
                context: context,
                isScrollControlled: true,
                useSafeArea: true,
                showDragHandle: true,
                builder: (context) => FractionallySizedBox(
                  heightFactor: .8,
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      children: [
                        _SessionSummaryCard(
                          session: session,
                          state: state,
                          controller: controller,
                          localizations: localizations,
                          purchasesEnabled: purchasesEnabled,
                        ),
                        _ClaimHistoryCard(
                          state: state,
                          localizations: localizations,
                        ),
                        Padding(
                          padding: const EdgeInsets.all(16),
                          child: Text(
                            localizations.claimSettlementUnavailableBody,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          _ConnectionChip(
            state: state,
            localizations: localizations,
            compact: true,
          ),
          IconButton(
            tooltip: localizations.refresh,
            onPressed: controller.refresh,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: ResponsiveContent(
        child: !isPreGame && !state.isLoading
            ? _LivePlayView(
                state: state,
                controller: controller,
                localizations: localizations,
                preferences: playerPreferences,
              )
            : RefreshIndicator(
                onRefresh: controller.refresh,
                child: ListView(
                  key: PageStorageKey('live-game-${widget.session.id}'),
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.all(16),
                  children: [
                    if (state.isLoading) ...[
                      const SizedBox(height: 72),
                      Center(child: Text(localizations.loading)),
                    ] else ...[
                      if (state.errorMessage != null ||
                          state.apiError != null) ...[
                        SectionCard(
                          child: Text(
                            state.apiError?.userMessage(localizations) ??
                                state.errorMessage!,
                            style: TextStyle(
                              color: Theme.of(context).colorScheme.error,
                            ),
                          ),
                        ),
                        const SizedBox(height: 16),
                      ],
                      if (isPreGame)
                        _SessionSummaryCard(
                          session: session,
                          state: state,
                          controller: controller,
                          localizations: localizations,
                          purchasesEnabled: purchasesEnabled,
                        ),
                      if (!isPreGame)
                        LiveCallPanel(
                          draws: state.draws,
                          isLive:
                              session.state == 'STARTED' ||
                              session.state == 'DRAWING',
                          connected:
                              state.connectionState ==
                              LiveGameConnectionState.connected,
                          finished: state.isTerminalSession,
                        ),
                      const SizedBox(height: 8),
                      _TicketsCard(
                        state: state,
                        controller: controller,
                        localizations: localizations,
                        preferences: playerPreferences,
                      ),
                      const SizedBox(height: 16),
                      if (!isPreGame)
                        _ClaimCard(
                          state: state,
                          controller: controller,
                          localizations: localizations,
                        ),
                      const SizedBox(height: 16),
                      if (!isPreGame)
                        _SessionSummaryCard(
                          session: session,
                          state: state,
                          controller: controller,
                          localizations: localizations,
                          purchasesEnabled: purchasesEnabled,
                        ),
                      const SizedBox(height: 16),
                      _ClaimHistoryCard(
                        state: state,
                        localizations: localizations,
                      ),
                      const SizedBox(height: 16),
                      SectionCard(
                        child: Text(
                          localizations.claimSettlementUnavailableBody,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
      ),
    );
  }
}

class _LivePlayView extends StatelessWidget {
  const _LivePlayView({
    required this.state,
    required this.controller,
    required this.localizations,
    required this.preferences,
  });
  final LiveGameState state;
  final LiveGameController controller;
  final AppLocalizations localizations;
  final PlayerPreferences preferences;

  @override
  Widget build(BuildContext context) => SafeArea(
    child: LayoutBuilder(
      builder: (context, constraints) {
        final scale = MediaQuery.textScalerOf(context).scale(14) / 14;
        // Only short viewports or large accessibility text need scrolling.
        final controlsHeight =
            ((state.ticketPage?.content.length ?? 0) > 1 ? 48.0 : 0.0) +
            (state.canPageBackTickets || state.canPageForwardTickets
                ? 48.0
                : 0.0) +
            ((state.session?.configuredPatterns.length ?? 0) > 1 ? 72.0 : 0.0);
        final height = math.max(
          constraints.maxHeight,
          580.0 + controlsHeight + math.max(0.0, scale - 1) * 540,
        );
        return RefreshIndicator(
          onRefresh: controller.refresh,
          child: SingleChildScrollView(
            key: const ValueKey('live-play-screen'),
            physics: const AlwaysScrollableScrollPhysics(),
            child: SizedBox(
              height: height,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(8, 4, 8, 4),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    LiveCallPanel(
                      draws: state.draws,
                      isLive: !state.isTerminalSession,
                      connected:
                          state.connectionState ==
                          LiveGameConnectionState.connected,
                      finished: state.isTerminalSession,
                    ),
                    Expanded(
                      child: _TicketsCard(
                        state: state,
                        controller: controller,
                        localizations: localizations,
                        fillAvailableSpace: true,
                        preferences: preferences,
                      ),
                    ),
                    _ClaimCard(
                      state: state,
                      controller: controller,
                      localizations: localizations,
                      compact: true,
                    ),
                  ],
                ),
              ),
            ),
          ),
        );
      },
    ),
  );
}

class _SessionSummaryCard extends StatelessWidget {
  const _SessionSummaryCard({
    required this.session,
    required this.state,
    required this.controller,
    required this.localizations,
    required this.purchasesEnabled,
  });

  final GameSession session;
  final LiveGameState state;
  final LiveGameController controller;
  final AppLocalizations localizations;
  final bool purchasesEnabled;

  Future<void> _confirmPurchase(BuildContext context) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(localizations.purchaseTicketTitle),
        content: Text(
          '${session.ticketPrice} ${session.currency}\n\n${localizations.purchaseTicketBody}',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: Text(localizations.cancel),
          ),
          FilledButton(
            key: const ValueKey('confirm-ticket-purchase'),
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: Text(localizations.purchaseTicket),
          ),
        ],
      ),
    );
    if (confirmed == true && context.mounted) {
      await controller.purchaseTicket();
    }
  }

  @override
  Widget build(BuildContext context) {
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      session.sessionName,
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    const SizedBox(height: 6),
                    Text(session.roomName),
                  ],
                ),
              ),
              _ConnectionChip(state: state, localizations: localizations),
            ],
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 12,
            runSpacing: 8,
            children: [
              _InfoChip(
                label: localizations.sessionStatusLabel,
                value: session.state,
              ),
              _InfoChip(
                label: localizations.ticketCountLabel,
                value: session.ticketCount.toString(),
              ),
              _InfoChip(
                label: localizations.drawCountLabel,
                value: state.draws.length.toString(),
              ),
              _InfoChip(
                label: localizations.roomCapacityLabel,
                value: session.sessionCapacity.toString(),
              ),
              _InfoChip(
                label: localizations.availableSeatsLabel,
                value: session.availableSeats.toString(),
              ),
            ],
          ),
          const SizedBox(height: 12),
          if (session.state == 'OPEN')
            FilledButton.icon(
              key: const ValueKey('buy-ticket-button'),
              onPressed: !purchasesEnabled || state.isPurchasingTicket
                  ? null
                  : () => _confirmPurchase(context),
              icon: state.isPurchasingTicket
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.confirmation_number_outlined),
              label: Text(localizations.purchaseTicket),
            )
          else
            Text(
              localizations.purchaseTicketBody,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          if (state.infoMessage == 'TICKET_PURCHASED') ...[
            const SizedBox(height: 8),
            Text(
              localizations.purchaseSuccess,
              style: TextStyle(color: Theme.of(context).colorScheme.primary),
            ),
          ],
          if (session.state == 'OPEN' && !purchasesEnabled) ...[
            const SizedBox(height: 8),
            Text(localizations.ticketPurchaseDisabledBody),
          ],
          if (state.isTerminalSession) ...[
            const SizedBox(height: 12),
            Text(
              localizations.liveConnectionFinished,
              style: TextStyle(color: Theme.of(context).colorScheme.primary),
            ),
          ],
        ],
      ),
    );
  }
}

class _TicketsCard extends StatelessWidget {
  const _TicketsCard({
    required this.state,
    required this.controller,
    required this.localizations,
    required this.preferences,
    this.fillAvailableSpace = false,
  });

  final bool fillAvailableSpace;
  final LiveGameState state;
  final LiveGameController controller;
  final AppLocalizations localizations;
  final PlayerPreferences preferences;

  @override
  Widget build(BuildContext context) {
    final ticketPage = state.ticketPage;
    final tickets = ticketPage?.content ?? const [];
    final selectedTicket = state.selectedTicket;
    final orderedTickets = [...tickets]
      ..sort((a, b) {
        for (final priority in preferences.sortPriorities) {
          final score = _ticketPatternScore(
            b,
            state.calledNumbers,
            priority,
          ).compareTo(_ticketPatternScore(a, state.calledNumbers, priority));
          if (score != 0) return score;
        }
        return 0;
      });

    return SectionCard(
      key: const ValueKey('live-tickets'),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  localizations.myTicketsTitle,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
              if (ticketPage != null)
                Text(
                  localizations.ticketPosition(
                    ticketPage.totalElements == 0
                        ? 0
                        : ticketPage.page * ticketPage.size +
                              state.selectedTicketIndex +
                              1,
                    ticketPage.totalElements,
                  ),
                ),
            ],
          ),
          const SizedBox(height: 12),
          if (tickets.isEmpty)
            AppStatusView(
              title: localizations.emptyState,
              message: localizations.contractGapBody,
            )
          else ...[
            if (tickets.length > 1) ...[
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  spacing: 8,
                  children: [
                    for (final ticket in orderedTickets)
                      ChoiceChip(
                        selected: preferences.multipleCardSelection
                            ? preferences.selectedTicketIds.contains(ticket.id)
                            : ticket.id == selectedTicket?.id,
                        label: Text(ticket.publicTicketCode),
                        onSelected: (_) {
                          controller.selectTicketIndex(tickets.indexOf(ticket));
                          if (preferences.multipleCardSelection) {
                            preferences.toggleSelectedTicket(ticket.id);
                          }
                        },
                      ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
            ],
            if (selectedTicket != null) ...[
              if (fillAvailableSpace)
                Expanded(
                  child: _buildTicketBoard(
                    orderedTickets,
                    tickets,
                    state,
                    controller,
                    localizations,
                    preferences,
                    true,
                  ),
                )
              else
                _buildTicketBoard(
                  orderedTickets,
                  tickets,
                  state,
                  controller,
                  localizations,
                  preferences,
                  false,
                ),
              const SizedBox(height: 12),
            ],
            if (state.canPageBackTickets || state.canPageForwardTickets)
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  TextButton(
                    onPressed: state.canPageBackTickets
                        ? () => controller.selectTicketPage(
                            state.ticketPageIndex - 1,
                          )
                        : null,
                    child: Text(localizations.previous),
                  ),
                  TextButton(
                    onPressed: state.canPageForwardTickets
                        ? () => controller.selectTicketPage(
                            state.ticketPageIndex + 1,
                          )
                        : null,
                    child: Text(localizations.next),
                  ),
                ],
              ),
          ],
        ],
      ),
    );
  }

  Widget _buildTicketBoard(
    List<TicketResponse> ordered,
    List<TicketResponse> source,
    LiveGameState state,
    LiveGameController controller,
    AppLocalizations localizations,
    PlayerPreferences preferences,
    bool fill,
  ) {
    final boardCapacity = preferences.cardsOnBoard * 2;
    // Keep unselected tickets visible in selection mode so the player can add
    // or remove any ticket without it disappearing from the grid.
    final visible = preferences.multipleCardSelection
        ? ordered
        : ordered.take(boardCapacity).toList();
    final selected = state.selectedTicket;
    if (selected != null &&
        !visible.any((ticket) => ticket.id == selected.id) &&
        visible.isNotEmpty) {
      visible[visible.length - 1] = selected;
    }
    return GridView.builder(
      shrinkWrap: !fill,
      physics: fill
          ? const AlwaysScrollableScrollPhysics()
          : const NeverScrollableScrollPhysics(),
      padding: EdgeInsets.zero,
      itemCount: preferences.multipleCardSelection
          ? visible.length
          : preferences.hideUnregisteredCards
          ? visible.length
          : boardCapacity,
      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: preferences.cardsOnBoard,
        crossAxisSpacing: 8,
        mainAxisSpacing: 8,
        childAspectRatio: .72,
      ),
      itemBuilder: (context, index) {
        if (index >= visible.length) {
          return _UnregisteredTicketCard(localizations: localizations);
        }
        final ticket = visible[index];
        return _TicketGridCard(
          ticket: ticket,
          calledNumbers: state.calledNumbers,
          localizations: localizations,
          autoMark: preferences.autoMark,
          manualMarks: preferences.manualMarksForTicket(ticket.id),
          onToggleManualMark: (number) =>
              preferences.toggleManualMark(ticket.id, number),
          selectionColor: PlayerPreferences
              .selectionColors[preferences.selectionColorIndex],
          selected: ticket.id == state.selectedTicket?.id,
          multiSelected: preferences.selectedTicketIds.contains(ticket.id),
          onTap: () {
            controller.selectTicketIndex(source.indexOf(ticket));
            if (preferences.multipleCardSelection) {
              preferences.toggleSelectedTicket(ticket.id);
            }
          },
        );
      },
    );
  }
}

class _TicketGridCard extends StatelessWidget {
  const _TicketGridCard({
    required this.ticket,
    required this.calledNumbers,
    required this.localizations,
    this.autoMark = true,
    this.manualMarks = const {},
    this.onToggleManualMark,
    this.selectionColor = const Color(0xFF245AD8),
    this.selected = false,
    this.multiSelected = false,
    this.onTap,
  });

  final TicketResponse ticket;
  final List<int> calledNumbers;
  final AppLocalizations localizations;
  final bool autoMark;
  final Set<int> manualMarks;
  final ValueChanged<int>? onToggleManualMark;
  final Color selectionColor;
  final bool selected;
  final bool multiSelected;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final latestCalled = calledNumbers.isEmpty ? null : calledNumbers.last;
    final colors = Theme.of(context).colorScheme;
    final grid = LayoutBuilder(
      builder: (context, constraints) => Container(
        clipBehavior: Clip.hardEdge,
        decoration: BoxDecoration(
          color: colors.surface,
          border: Border.all(color: colors.outline, width: 1),
        ),
        padding: const EdgeInsets.all(1),
        child: GridView.builder(
          padding: EdgeInsets.zero,
          physics: const NeverScrollableScrollPhysics(),
          itemCount: 30,
          gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: 5,
            crossAxisSpacing: 0,
            mainAxisSpacing: 0,
            childAspectRatio:
                constraints.maxWidth / 5 / ((constraints.maxHeight - 2) / 6),
          ),
          itemBuilder: (context, index) {
            final row = index ~/ 5;
            final column = index % 5;
            final cellBorder = Border(
              right: column == 4
                  ? BorderSide.none
                  : BorderSide(color: colors.outline, width: 1),
              bottom: row == 5
                  ? BorderSide.none
                  : BorderSide(color: colors.outline, width: 1),
            );
            if (row == 0) {
              return _BingoHeaderCell(
                letter: const ['B', 'I', 'N', 'G', 'O'][column],
                border: cellBorder,
              );
            }

            final ticketRow = row - 1;
            final value = ticket.grid[ticketRow][column];
            final isFree = value == 0;
            final isCalled =
                isFree ||
                manualMarks.contains(value) ||
                (autoMark && calledNumbers.contains(value));
            final isLatest = value == latestCalled;
            return _TicketCell(
              key: ValueKey('ticket-cell-$ticketRow-$column'),
              value: value,
              columnLetter: const ['B', 'I', 'N', 'G', 'O'][column],
              isFree: isFree,
              isCalled: isCalled,
              isLatest: isLatest,
              border: cellBorder,
              localizations: localizations,
              selectionColor: selectionColor,
              isManuallyMarked: manualMarks.contains(value),
              onTap: isFree ? null : () => onToggleManualMark?.call(value),
            );
          },
        ),
      ),
    );
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Container(
        decoration: BoxDecoration(
          border: selected || multiSelected
              ? Border.all(color: selectionColor, width: 2)
              : null,
          borderRadius: BorderRadius.circular(8),
        ),
        padding: const EdgeInsets.all(3),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              ticket.publicTicketCode,
              style: Theme.of(
                context,
              ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 4),
            Expanded(key: const ValueKey('live-ticket-grid'), child: grid),
          ],
        ),
      ),
    );
  }
}

class _ClaimCard extends StatelessWidget {
  const _ClaimCard({
    required this.state,
    required this.controller,
    required this.localizations,
    this.compact = false,
  });

  final bool compact;
  final LiveGameState state;
  final LiveGameController controller;
  final AppLocalizations localizations;

  @override
  Widget build(BuildContext context) {
    final session = state.session;
    final ticket = state.selectedTicket;
    final patterns = session?.configuredPatterns ?? const [];

    return SectionCard(
      padding: compact
          ? const EdgeInsets.symmetric(horizontal: 12, vertical: 4)
          : null,
      child: Column(
        crossAxisAlignment: compact
            ? CrossAxisAlignment.stretch
            : CrossAxisAlignment.start,
        children: [
          if (state.selectedTicketIsWinner)
            Padding(
              key: const ValueKey('winning-ticket-message'),
              padding: const EdgeInsets.only(bottom: 4),
              child: Text(
                localizations.winningTicketReady(state.selectedClaimPattern!),
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: Theme.of(context).colorScheme.primary,
                  fontWeight: FontWeight.bold,
                ),
              ),
            )
          else if (state.isClaimWindowOpen)
            Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: Text(
                state.isTerminalSession
                    ? localizations.ticketDidNotWin
                    : localizations.claimWaitingForWin,
                textAlign: TextAlign.center,
              ),
            ),

          if (!compact)
            Text(
              localizations.claimSettlementLabel,
              style: Theme.of(context).textTheme.titleMedium,
            ),
          if (!compact && session?.rulesVersion == 'EARLIEST_SPLIT_V1') ...[
            const SizedBox(height: 12),
            Text(localizations.earliestSplitRule),
            if (session?.claimWindowSeconds != null)
              Text(
                localizations.gameClaimWindow(
                  (session!.claimWindowSeconds! / 3600).toStringAsFixed(1),
                ),
              ),
          ],
          if (!compact || patterns.length > 1) const SizedBox(height: 12),
          if (!compact || patterns.length > 1)
            DropdownButtonFormField<String>(
              key: const ValueKey('claim-pattern-field'),
              initialValue: state.selectedClaimPattern,
              decoration: InputDecoration(
                labelText: localizations.claimPatternLabel,
              ),
              items: [
                for (final pattern in patterns)
                  DropdownMenuItem<String>(
                    value: pattern,
                    child: Text(pattern),
                  ),
              ],
              onChanged: patterns.isEmpty
                  ? null
                  : (value) => value == null
                        ? null
                        : controller.selectClaimPattern(value),
            ),
          if (!compact || patterns.length > 1) const SizedBox(height: 12),
          FilledButton(
            key: const ValueKey('claim-submit-button'),
            onPressed:
                state.isSubmittingClaim ||
                    ticket == null ||
                    state.selectedClaimPattern == null ||
                    !state.canSubmitClaim
                ? null
                : () async {
                    final outcome = await controller.submitClaim();
                    if (!context.mounted) return;
                    if (outcome == ClaimSubmissionOutcome.submitted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(content: Text(localizations.claimSubmitted)),
                      );
                      return;
                    }
                    if (outcome != ClaimSubmissionOutcome.alreadySubmitted) {
                      return;
                    }
                    await showDialog<void>(
                      context: context,
                      builder: (context) => AlertDialog(
                        key: const ValueKey('claim-already-submitted-dialog'),
                        content: Text(localizations.claimAlreadySubmitted),
                        actions: [
                          TextButton(
                            onPressed: () => Navigator.pop(context),
                            child: Text(localizations.claimDialogDismiss),
                          ),
                        ],
                      ),
                    );
                  },
            child: state.isSubmittingClaim
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Text(localizations.submitClaim),
          ),
          if (ticket == null) ...[
            const SizedBox(height: 12),
            Text(localizations.contractGapBody),
          ],
          if (!compact &&
              state.infoMessage != null &&
              state.infoMessage != 'CLAIM_ALREADY_SUBMITTED') ...[
            const SizedBox(height: 12),
            Text(
              state.infoMessage == 'CLAIM_SUBMITTED'
                  ? localizations.claimSubmitted
                  : state.infoMessage!,
              style: TextStyle(color: Theme.of(context).colorScheme.primary),
            ),
          ],
          if (state.errorMessage != null || state.apiError != null) ...[
            const SizedBox(height: 12),
            Text(
              state.apiError?.userMessage(localizations) ?? state.errorMessage!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ],
        ],
      ),
    );
  }
}

class _ClaimHistoryCard extends StatelessWidget {
  const _ClaimHistoryCard({required this.state, required this.localizations});

  final LiveGameState state;
  final AppLocalizations localizations;

  @override
  Widget build(BuildContext context) {
    final claims = List<WinClaimRecord>.from(
      state.claimPage?.content ?? const <WinClaimRecord>[],
    )..sort((left, right) => right.createdAt.compareTo(left.createdAt));
    final sessionId = state.session?.id;
    final sessionClaims = sessionId == null
        ? claims
        : claims.where((claim) => claim.sessionId == sessionId).toList();

    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            localizations.claimHistoryTitle,
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 12),
          if (sessionClaims.isEmpty)
            AppStatusView(
              title: localizations.emptyState,
              message: localizations.contractGapBody,
            )
          else
            Column(
              children: [
                for (final claim in sessionClaims)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: SectionCard(
                      padding: const EdgeInsets.all(12),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Expanded(
                                child: Text(
                                  '${claim.claimedPattern} • ${_claimStatusLabel(localizations, claim.status)}',
                                  style: Theme.of(context).textTheme.titleSmall,
                                ),
                              ),
                              _ClaimStatusChip(
                                status: claim.status,
                                localizations: localizations,
                              ),
                            ],
                          ),
                          const SizedBox(height: 8),
                          Text(
                            '${localizations.drawSnapshotLabel}: ${claim.drawSequenceSnapshot}',
                          ),
                          Text(
                            '${localizations.calledNumbersLabel}: ${claim.calledNumbersSnapshot.join(', ')}',
                          ),
                          Text(
                            '${localizations.serverValidationLabel}: ${claim.serverValidationPassed ? localizations.claimPatternApproved : localizations.claimPatternFailed}',
                          ),
                          if (claim.reviewNote != null)
                            Text(
                              '${localizations.reviewNoteLabel}: ${claim.reviewNote}',
                            ),
                          if (claim.prizeAmount != null)
                            Text(
                              '${claim.prizeAmount} ${claim.prizeCurrency ?? ""} · ${claim.prizeStatus == "SETTLED" ? localizations.prizePaid : localizations.prizeAwaitingPayment}',
                            ),
                          if (claim.payoutCode != null)
                            SelectableText(
                              '${localizations.prizeCashCode}: ${claim.payoutCode}',
                            ),
                          if (claim.entitlementId != null)
                            Text(
                              '${localizations.claimSettlementLabel}: #${claim.entitlementId}',
                            ),
                        ],
                      ),
                    ),
                  ),
              ],
            ),
        ],
      ),
    );
  }
}

String _claimStatusLabel(AppLocalizations localizations, String status) {
  switch (status.toUpperCase()) {
    case 'PENDING_REVIEW':
      return localizations.claimPatternPending;
    case 'APPROVED':
      return localizations.claimPatternApproved;
    case 'REJECTED':
      return localizations.claimPatternRejected;
    case 'FAILED_SERVER_VALIDATION':
      return localizations.claimPatternFailed;
    default:
      return status;
  }
}

class _ConnectionChip extends StatelessWidget {
  const _ConnectionChip({
    required this.state,
    required this.localizations,
    this.compact = false,
  });

  final LiveGameState state;
  final AppLocalizations localizations;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final label = _connectionLabel(state.connectionState, localizations);
    if (compact && MediaQuery.sizeOf(context).width < 480) {
      final icon = switch (state.connectionState) {
        LiveGameConnectionState.idle ||
        LiveGameConnectionState.disconnected => Icons.wifi_off_rounded,
        LiveGameConnectionState.connecting ||
        LiveGameConnectionState.reconnecting => Icons.sync_rounded,
        LiveGameConnectionState.connected => Icons.wifi_rounded,
        LiveGameConnectionState.paused => Icons.pause_rounded,
        LiveGameConnectionState.finished => Icons.check_circle_outline_rounded,
        LiveGameConnectionState.expired => Icons.error_outline_rounded,
      };
      return Tooltip(
        message: label,
        child: Chip(label: Icon(icon, size: 18)),
      );
    }
    return Chip(label: Text(label));
  }
}

String _connectionLabel(
  LiveGameConnectionState connectionState,
  AppLocalizations localizations,
) {
  switch (connectionState) {
    case LiveGameConnectionState.idle:
      return localizations.liveConnectionDisconnected;
    case LiveGameConnectionState.connecting:
      return localizations.liveConnectionConnecting;
    case LiveGameConnectionState.connected:
      return localizations.liveConnectionConnected;
    case LiveGameConnectionState.reconnecting:
      return localizations.liveConnectionReconnecting;
    case LiveGameConnectionState.paused:
      return localizations.liveConnectionPaused;
    case LiveGameConnectionState.finished:
      return localizations.liveConnectionFinished;
    case LiveGameConnectionState.expired:
      return localizations.liveConnectionExpired;
    case LiveGameConnectionState.disconnected:
      return localizations.liveConnectionDisconnected;
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

class _TicketCell extends StatelessWidget {
  const _TicketCell({
    super.key,
    required this.value,
    required this.columnLetter,
    required this.isFree,
    required this.isCalled,
    required this.isLatest,
    required this.border,
    required this.localizations,
    required this.selectionColor,
    required this.isManuallyMarked,
    this.onTap,
  });

  final int value;
  final String columnLetter;
  final bool isFree;
  final bool isCalled;
  final bool isLatest;
  final Border border;
  final AppLocalizations localizations;
  final Color selectionColor;
  final bool isManuallyMarked;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final background = isLatest
        ? selectionColor.withValues(alpha: .34)
        : isCalled
        ? selectionColor.withValues(alpha: isManuallyMarked ? .32 : .2)
        : colors.surface;
    final numberColor = isLatest
        ? selectionColor
        : isCalled
        ? selectionColor
        : colors.onSurface;

    return Semantics(
      label: '$columnLetter ${isFree ? localizations.freeCenterLabel : value}',
      selected: isCalled && !isFree,
      button: onTap != null,
      child: ExcludeSemantics(
        child: InkWell(
          onTap: onTap,
          child: AnimatedContainer(
            duration: MediaQuery.disableAnimationsOf(context)
                ? Duration.zero
                : const Duration(milliseconds: 180),
            decoration: BoxDecoration(color: background, border: border),
            child: Stack(
              fit: StackFit.expand,
              children: [
                if (isFree)
                  Center(
                    child: Padding(
                      padding: const EdgeInsets.all(2),
                      child: FittedBox(
                        fit: BoxFit.scaleDown,
                        child: Text(
                          localizations.freeCenterLabel.toUpperCase(),
                          style: Theme.of(context).textTheme.labelLarge
                              ?.copyWith(
                                color: colors.onSecondaryContainer,
                                fontWeight: FontWeight.w800,
                              ),
                        ),
                      ),
                    ),
                  )
                else
                  Center(
                    child: Padding(
                      padding: const EdgeInsets.all(2),
                      child: FittedBox(
                        fit: BoxFit.scaleDown,
                        child: Text(
                          value.toString(),
                          style: Theme.of(context).textTheme.titleMedium
                              ?.copyWith(
                                color: numberColor,
                                fontWeight: isCalled
                                    ? FontWeight.w700
                                    : FontWeight.w500,
                              ),
                        ),
                      ),
                    ),
                  ),
                if (isCalled && !isFree)
                  Positioned(
                    top: 1,
                    right: 1,
                    child: Icon(Icons.check, size: 13, color: selectionColor),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _UnregisteredTicketCard extends StatelessWidget {
  const _UnregisteredTicketCard({required this.localizations});
  final AppLocalizations localizations;

  @override
  Widget build(BuildContext context) => DecoratedBox(
    decoration: BoxDecoration(
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      borderRadius: BorderRadius.circular(10),
      border: Border.all(color: Theme.of(context).colorScheme.outlineVariant),
    ),
    child: Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.add_card_outlined, color: Colors.grey, size: 32),
          const SizedBox(height: 4),
          Text(
            localizations.unregisteredCard,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.labelSmall,
          ),
        ],
      ),
    ),
  );
}

class _BingoHeaderCell extends StatelessWidget {
  const _BingoHeaderCell({required this.letter, required this.border});

  final String letter;
  final Border border;

  @override
  Widget build(BuildContext context) {
    return Container(
      key: ValueKey('bingo-header-$letter'),
      decoration: BoxDecoration(color: const Color(0xFF102B4E), border: border),
      alignment: Alignment.center,
      child: FittedBox(
        fit: BoxFit.scaleDown,
        child: Text(
          letter,
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
            color: const Color(0xFFFFD174),
            fontWeight: FontWeight.w800,
          ),
        ),
      ),
    );
  }
}

class _ClaimStatusChip extends StatelessWidget {
  const _ClaimStatusChip({required this.status, required this.localizations});

  final String status;
  final AppLocalizations localizations;

  @override
  Widget build(BuildContext context) {
    return Chip(label: Text(_claimStatusLabel(localizations, status)));
  }
}

double _ticketPatternScore(
  TicketResponse ticket,
  List<int> called,
  String pattern,
) {
  final calledSet = {...called, 0};
  final masks = _sortMasks(pattern);
  if (pattern == 'Most called') {
    return ticket.grid.expand((row) => row).where(calledSet.contains).length /
        25;
  }
  if (masks.isEmpty) return 0;
  return masks
      .map(
        (mask) =>
            mask
                .where(
                  (cell) =>
                      calledSet.contains(ticket.grid[cell ~/ 5][cell % 5]),
                )
                .length /
            mask.length,
      )
      .reduce(math.max);
}

List<List<int>> _sortMasks(String name) {
  List<int> row(int r) => List.generate(5, (c) => r * 5 + c);
  List<int> column(int c) => List.generate(5, (r) => r * 5 + c);
  final rows = List.generate(5, row);
  final columns = List.generate(5, column);
  final diagonals = [
    List.generate(5, (i) => i * 6),
    List.generate(5, (i) => i * 5 + 4),
  ];
  if (name.startsWith('Lines')) {
    var masks = [...rows, ...columns, ...diagonals];
    if (name == 'Lines touching free') {
      masks = masks.where((mask) => mask.contains(12)).toList();
    }
    if (name == 'Lines not touching free') {
      masks = masks.where((mask) => !mask.contains(12)).toList();
    }
    if (name == 'Non-crossing lines') {
      masks = masks.where((mask) => !diagonals.contains(mask)).toList();
    }
    return masks;
  }
  if (name == 'Horizontal lines') {
    return rows;
  }
  if (name == 'Vertical lines') {
    return columns;
  }
  if (name == 'Diagonal lines') {
    return diagonals;
  }
  if (name == 'Corner balls') {
    return [
      [0, 4, 20, 24],
    ];
  }
  if (name == 'Big O (border)') {
    return [
      [
        ...rows.first,
        ...rows.last,
        ...List.generate(3, (i) => (i + 1) * 5),
        ...List.generate(3, (i) => (i + 1) * 5 + 4),
      ],
    ];
  }
  if (name == 'Big T') {
    return [
      [...rows.first, 6, 11, 16, 21],
    ];
  }
  if (name == 'Big Z') {
    return [
      [...rows.first, ...rows.last, 4, 8, 12, 16],
    ];
  }
  if (name == 'Big H') {
    return [
      [...column(0), ...column(4), ...rows[2]],
    ];
  }
  if (name == 'Big L') {
    return [
      [...column(0), ...rows.last],
    ];
  }
  if (name == 'Big U') {
    return [
      [...column(0), ...column(4), ...rows.last],
    ];
  }
  if (name == 'Small X') {
    return [
      [0, 6, 12, 18, 24, 4, 8, 16, 20],
    ];
  }
  if (name == 'Small T') {
    return [
      [0, 1, 2, 6, 11, 16, 21],
    ];
  }
  if (name == 'Small cross (+)') {
    return [
      [2, 7, 10, 11, 12, 13, 14, 17, 22],
    ];
  }
  if (name == 'Half house') {
    return [
      [...rows[0], ...rows[1], ...rows[2]],
    ];
  }
  if (name == 'Squares' || name == 'Rectangles') {
    final masks = <List<int>>[];
    for (var height = 2; height <= 4; height++) {
      for (var width = 2; width <= 4; width++) {
        if ((height == width) != (name == 'Squares')) continue;
        for (var r = 0; r + height <= 5; r++) {
          for (var c = 0; c + width <= 5; c++) {
            masks.add([
              for (var y = r; y < r + height; y++)
                for (var x = c; x < c + width; x++) y * 5 + x,
            ]);
          }
        }
      }
    }
    return masks;
  }
  return [];
}

class _GameSettingsSheet extends StatelessWidget {
  const _GameSettingsSheet({required this.preferences, this.activeTicketId});
  final PlayerPreferences preferences;
  final int? activeTicketId;

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: preferences,
    builder: (context, _) {
      final localizations = AppLocalizations.of(context)!;
      return SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                localizations.gameSettingsTitle,
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              const SizedBox(height: 16),
              Text('Board', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              SegmentedButton<int>(
                segments: const [
                  ButtonSegment(value: 2, label: Text('2')),
                  ButtonSegment(value: 3, label: Text('3')),
                  ButtonSegment(value: 4, label: Text('4')),
                ],
                selected: {preferences.cardsOnBoard},
                onSelectionChanged: (value) =>
                    preferences.setBoardSize(value.first),
              ),
              ListTile(
                leading: const Icon(Icons.library_add_check_outlined),
                title: Text(localizations.selectMultipleCards),
                subtitle: Text(localizations.selectMultipleCardsDescription),
                trailing: Switch(
                  value: preferences.multipleCardSelection,
                  onChanged: (value) => preferences.setMultipleCardSelection(
                    value,
                    initialTicketId: activeTicketId,
                  ),
                ),
                onTap: () => preferences.setMultipleCardSelection(
                  !preferences.multipleCardSelection,
                  initialTicketId: activeTicketId,
                ),
              ),
              ListTile(
                leading: const Icon(Icons.sort_rounded),
                title: Text(localizations.sortCards),
                subtitle: Text(
                  preferences.sortPriorities.isEmpty
                      ? localizations.sortCardsChooseThree
                      : preferences.sortPriorities.join(' · '),
                ),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => showModalBottomSheet<void>(
                  context: context,
                  isScrollControlled: true,
                  useSafeArea: true,
                  showDragHandle: true,
                  builder: (_) => _SortCardsSheet(preferences: preferences),
                ),
              ),
              const Divider(),
              ListTile(
                leading: const Icon(Icons.restart_alt_rounded),
                title: Text(localizations.resetMarkedNumbers),
                subtitle: Text(localizations.clearManualMarksDescription),
                onTap: preferences.clearManualMarks,
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(localizations.autoMarkNumbers),
                subtitle: Text(localizations.autoMarkNumbersDescription),
                value: preferences.autoMark,
                onChanged: preferences.setAutoMark,
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(localizations.hideUnregisteredCards),
                subtitle: Text(localizations.hideUnregisteredCardsDescription),
                value: preferences.hideUnregisteredCards,
                onChanged: preferences.setHideUnregistered,
              ),
              const SizedBox(height: 8),
              Text(
                localizations.selectionColor,
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 10),
              Wrap(
                spacing: 10,
                runSpacing: 10,
                children: [
                  for (
                    var i = 0;
                    i < PlayerPreferences.selectionColors.length;
                    i++
                  )
                    InkWell(
                      onTap: () => preferences.setSelectionColor(i),
                      customBorder: const CircleBorder(),
                      child: Container(
                        width: 38,
                        height: 38,
                        decoration: BoxDecoration(
                          color: PlayerPreferences.selectionColors[i],
                          shape: BoxShape.circle,
                          border: Border.all(
                            color: preferences.selectionColorIndex == i
                                ? Theme.of(context).colorScheme.onSurface
                                : Colors.transparent,
                            width: 3,
                          ),
                        ),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 18),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: () => Navigator.pop(context),
                  child: const Text('Close'),
                ),
              ),
            ],
          ),
        ),
      );
    },
  );
}

class _SortCardsSheet extends StatelessWidget {
  const _SortCardsSheet({required this.preferences});
  final PlayerPreferences preferences;

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: preferences,
    builder: (context, _) {
      const groups = <String, List<String>>{
        'Lines': [
          'Lines (anywhere)',
          'Lines touching free',
          'Lines not touching free',
          'Non-crossing lines',
        ],
        'Squares': ['Squares'],
        'Rectangles': ['Rectangles'],
        'Horizontal lines': ['Horizontal lines'],
        'Vertical lines': ['Vertical lines'],
        'Big T': ['Big T'],
        'Big Z': ['Big Z'],
        'Big H': ['Big H'],
        'Big L': ['Big L'],
        'Big U': ['Big U'],
        'Other patterns': [
          'Diagonal lines',
          'Most called',
          'Small X',
          'Small T',
          'Small cross (+)',
          'Corner balls',
          'Half house',
          'Big O (border)',
        ],
      };
      return SafeArea(
        child: SizedBox(
          height: MediaQuery.sizeOf(context).height * .82,
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 4, 20, 12),
                child: Row(
                  children: [
                    const Icon(Icons.sort_rounded),
                    const SizedBox(width: 12),
                    Text(
                      'Sort by',
                      style: Theme.of(context).textTheme.headlineSmall,
                    ),
                  ],
                ),
              ),
              if (preferences.sortPriorities.isNotEmpty)
                const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 20),
                  child: Align(
                    alignment: Alignment.centerLeft,
                    child: Text(
                      'The order you tick them sets the priority. Up to 3.',
                    ),
                  ),
                ),
              if (preferences.sortPriorities.isNotEmpty)
                Wrap(
                  spacing: 6,
                  children: [
                    for (var i = 0; i < preferences.sortPriorities.length; i++)
                      InputChip(
                        label: Text(
                          '${i + 1}. ${preferences.sortPriorities[i]}',
                        ),
                        onDeleted: () => preferences.toggleSortPriority(
                          preferences.sortPriorities[i],
                        ),
                      ),
                  ],
                ),
              Expanded(
                child: ListView(
                  children: [
                    for (final group in groups.entries)
                      ExpansionTile(
                        title: Text(
                          '${group.key}${group.key == 'Lines' ? '  ${preferences.sortPriorities.where(group.value.contains).length}' : ''}',
                        ),
                        initiallyExpanded:
                            group.key == 'Lines' &&
                            preferences.sortPriorities.isNotEmpty,
                        children: [
                          for (final option in group.value)
                            CheckboxListTile(
                              value: preferences.sortPriorities.contains(
                                option,
                              ),
                              title: Text(option),
                              secondary: Text(
                                option
                                    .split(' ')
                                    .map((word) => word.isEmpty ? '' : word[0])
                                    .take(2)
                                    .join()
                                    .toUpperCase(),
                              ),
                              onChanged: (_) {
                                if (!preferences.sortPriorities.contains(
                                      option,
                                    ) &&
                                    preferences.sortPriorities.length >= 3) {
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    const SnackBar(
                                      content: Text(
                                        'Choose up to 3 priorities.',
                                      ),
                                    ),
                                  );
                                  return;
                                }
                                preferences.toggleSortPriority(option);
                              },
                            ),
                        ],
                      ),
                  ],
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(16),
                child: SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    onPressed: () => Navigator.pop(context),
                    child: const Text('Done'),
                  ),
                ),
              ),
            ],
          ),
        ),
      );
    },
  );
}
