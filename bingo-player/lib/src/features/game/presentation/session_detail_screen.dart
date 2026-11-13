import 'dart:async';
import 'dart:math' as math;

import 'live_call_panel.dart';
import 'winner_celebration_dialog.dart';

import 'package:bingo_player/src/core/widgets/responsive_content.dart';

import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/core/config/app_config.dart';
import 'package:bingo_player/src/core/device/screen_awake.dart';
import 'package:bingo_player/src/core/storage/player_preferences.dart';
import 'package:bingo_player/src/core/widgets/app_status_view.dart';
import 'package:bingo_player/src/core/widgets/section_card.dart';
import 'package:bingo_player/src/features/game/application/live_game_controller.dart';
import 'package:bingo_player/src/features/game/domain/live_game_models.dart';
import 'package:bingo_player/src/features/game/domain/ticket_models.dart';
import 'package:bingo_player/src/features/game/domain/ticket_ranking.dart';
import 'package:bingo_player/src/features/lobby/domain/lobby_models.dart';
import 'package:bingo_player/src/features/wallet/data/wallet_repository.dart';
import 'package:flutter/material.dart';
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
  late final ScreenAwakeController _screenAwakeController;

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
    _screenAwakeController = ScreenAwakeController(
      ref.read(screenAwakeProvider),
    );
  }

  @override
  void dispose() {
    unawaited(_syncScreenAwake(false));
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
      final session =
          ref.read(liveGameControllerProvider(widget.session.id)).session ??
          widget.session;
      unawaited(_syncScreenAwake(_isLiveGame(session)));
    } else if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.inactive) {
      unawaited(controller.pause());
      unawaited(_syncScreenAwake(false));
    }
  }

  bool _isLiveGame(GameSession session) =>
      session.state == 'STARTED' || session.state == 'DRAWING';

  Future<void> _syncScreenAwake(bool active) async {
    try {
      await _screenAwakeController.setActiveGame(active);
    } catch (error, stackTrace) {
      debugPrint(
        'Unable to ${active ? 'enable' : 'disable'} screen wake lock: $error',
      );
      debugPrintStack(stackTrace: stackTrace);
    }
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final state = ref.watch(liveGameControllerProvider(widget.session.id));
    _maybeCelebrate(state);
    final controller = ref.read(
      liveGameControllerProvider(widget.session.id).notifier,
    );
    final purchasesEnabled = ref
        .watch(appConfigProvider)
        .playerTicketPurchasesEnabled;
    final session = state.session ?? widget.session;
    final isPreGame = session.state == 'DRAFT' || session.state == 'OPEN';
    unawaited(_syncScreenAwake(_isLiveGame(session)));

    return Scaffold(
      appBar: AppBar(
        title: Text(localizations.liveGameTitle),
        actions: [
          if (!isPreGame)
            IconButton(
              key: const ValueKey('live-game-settings'),
              tooltip: localizations.gameSettingsTitle,
              icon: const Icon(Icons.tune),
              onPressed: () => showModalBottomSheet<void>(
                context: context,
                isScrollControlled: true,
                useSafeArea: true,
                showDragHandle: true,
                builder: (context) =>
                    _GameSettingsSheet(sessionId: widget.session.id),
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
          _ConnectionChip(state: state, localizations: localizations),
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
                          soundEnabled: ref
                              .watch(playerPreferencesProvider)
                              .soundEnabled,
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

class _LivePlayView extends ConsumerWidget {
  const _LivePlayView({
    required this.state,
    required this.controller,
    required this.localizations,
  });
  final LiveGameState state;
  final LiveGameController controller;
  final AppLocalizations localizations;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final preferences = ref.watch(playerPreferencesProvider);
    final ticketList = state.allTickets.isNotEmpty
        ? state.allTickets
        : state.ticketPage?.content ?? const <TicketResponse>[];
    final accountId =
        state.selectedTicket?.ownerId ??
        (ticketList.isEmpty ? 0 : ticketList.first.ownerId);
    final preferenceScope =
        'account-$accountId-session-${state.session?.id ?? 0}';
    if (preferences.loaded && ticketList.isNotEmpty) {
      final availableTicketIds = ticketList.map((ticket) => ticket.id).toList();
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!context.mounted) return;
        unawaited(() async {
          await preferences.setScope(preferenceScope);
          if (context.mounted) {
            await preferences.ensureBoardSelection(
              availableTicketIds,
              expectedScope: preferenceScope,
            );
          }
        }());
      });
    } else {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (context.mounted) unawaited(preferences.setScope(preferenceScope));
      });
    }
    final onBoard = preferences.cardsOnBoard > 1
        ? _visibleTickets(ticketList, preferences, state)
        : state.selectedTicket == null
        ? const <TicketResponse>[]
        : <TicketResponse>[state.selectedTicket!];
    final visibleCardCount = onBoard.length;
    final missedCalls = preferences.autoMark
        ? 0
        : _countMissedCalls(onBoard, state.calledNumbers, preferences);
    return SafeArea(
      child: LayoutBuilder(
        builder: (context, constraints) {
          final scale = MediaQuery.textScalerOf(context).scale(14) / 14;
          // Only short viewports or large accessibility text need scrolling.
          final controlsHeight =
              ((state.ticketPage?.content.length ?? 0) > 1 ? 48.0 : 0.0) +
              (state.canPageBackTickets || state.canPageForwardTickets
                  ? 48.0
                  : 0.0) +
              ((state.session?.configuredPatterns.length ?? 0) > 1
                  ? 72.0
                  : 0.0);
          final height = math.max(
            constraints.maxHeight,
            (visibleCardCount == 1
                    ? 820.0
                    : visibleCardCount > 2
                    ? 800.0
                    : 580.0) +
                controlsHeight +
                math.max(0.0, scale - 1) * 1100,
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
                        eligibleWinCount: state.allTickets
                            .where(
                              (ticket) =>
                                  ticket.eligibleClaimPatterns.isNotEmpty,
                            )
                            .length,
                        soundEnabled: ref
                            .watch(playerPreferencesProvider)
                            .soundEnabled,
                        notificationsEnabled: preferences.notificationsEnabled,
                        missedCallCount: missedCalls,
                        onMarkMissedCalls: missedCalls == 0
                            ? null
                            : () async {
                                for (final ticket in onBoard) {
                                  final calledOnTicket = ticket.grid
                                      .expand((row) => row)
                                      .where(
                                        (number) =>
                                            number != 0 &&
                                            state.calledNumbers.contains(
                                              number,
                                            ),
                                      );
                                  await ref
                                      .read(playerPreferencesProvider)
                                      .markNumbers(ticket.id, calledOnTicket);
                                }
                              },
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
                        ),
                      ),
                      _ClaimReviewCard(state: state, controller: controller),
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
}

class _ClaimReviewCard extends ConsumerWidget {
  const _ClaimReviewCard({required this.state, required this.controller});
  final LiveGameState state;
  final LiveGameController controller;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context)!;
    final candidates = state.allTickets
        .where((ticket) => ticket.eligibleClaimPatterns.isNotEmpty)
        .toList();
    if (candidates.isEmpty) return const SizedBox.shrink();
    return Card(
      child: ExpansionTile(
        key: const ValueKey('eligible-claim-review'),
        title: Text(l.claimReviewTitle(candidates.length)),
        subtitle: Text(l.claimReviewHint),
        children: [
          for (final ticket in candidates)
            ListTile(
              title: Text(ticket.publicTicketCode),
              subtitle: Text(
                ticket.eligibleClaimPatterns
                    .map(
                      (pattern) => _claimReviewPatternStatus(
                        l,
                        ticket,
                        pattern,
                        state.claimPage?.content ?? const [],
                      ),
                    )
                    .join(' · '),
              ),
              trailing: const Icon(Icons.open_in_new),
              onTap: () async {
                await ref
                    .read(playerPreferencesProvider)
                    .selectTicketForBoard(ticket.id);
                await controller.selectTicketById(ticket.id);
              },
            ),
        ],
      ),
    );
  }
}

String _claimReviewPatternStatus(
  AppLocalizations l,
  TicketResponse ticket,
  String pattern,
  List<WinClaimRecord> claims,
) {
  WinClaimRecord? claim;
  for (final item in claims) {
    if (item.ticketId == ticket.id && item.claimedPattern == pattern) {
      claim = item;
    }
  }
  final status = claim == null
      ? l.winningTicketReady(_patternLabel(l, pattern))
      : _claimStatusLabel(l, claim.status);
  return '${_patternLabel(l, pattern)} · $status';
}

class _GameSettingsSheet extends ConsumerWidget {
  const _GameSettingsSheet({required this.sessionId});
  final int sessionId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final strings = AppLocalizations.of(context)!;
    final preferences = ref.watch(playerPreferencesProvider);
    final settings = ref.read(playerPreferencesProvider);
    return FractionallySizedBox(
      heightFactor: .9,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
        children: [
          Row(
            children: [
              const Icon(Icons.tune),
              const SizedBox(width: 12),
              Text(
                strings.gameSettingsTitle,
                style: Theme.of(context).textTheme.titleLarge,
              ),
            ],
          ),
          const SizedBox(height: 16),
          const Text('Board'),
          SegmentedButton<int>(
            segments: const [
              ButtonSegment(value: 1, label: Text('1')),
              ButtonSegment(value: 2, label: Text('2')),
              ButtonSegment(value: 3, label: Text('3')),
              ButtonSegment(value: 4, label: Text('4')),
            ],
            selected: {preferences.cardsOnBoard},
            onSelectionChanged: (values) => settings.setBoardSize(values.first),
          ),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(strings.autoMarkNumbers),
            subtitle: Text(strings.autoMarkNumbersDescription),
            value: preferences.autoMark,
            onChanged: settings.setAutoMark,
          ),
          ExpansionTile(
            tilePadding: EdgeInsets.zero,
            title: Text(strings.sortCards),
            subtitle: Text(strings.sortCardsChooseThree),
            children: [
              if (preferences.sortPriorities.isNotEmpty)
                Wrap(
                  spacing: 6,
                  children: [
                    for (var i = 0; i < preferences.sortPriorities.length; i++)
                      Chip(
                        avatar: CircleAvatar(child: Text('${i + 1}')),
                        label: Text(
                          _patternLabel(strings, preferences.sortPriorities[i]),
                        ),
                        onDeleted: () => settings.toggleSortPriority(
                          preferences.sortPriorities[i],
                        ),
                      ),
                  ],
                ),
              for (final option in PlayerPreferences.sortOptions)
                CheckboxListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  title: Text(_patternLabel(strings, option)),
                  value: preferences.sortPriorities.contains(option),
                  onChanged: (_) => settings.toggleSortPriority(option),
                ),
            ],
          ),
          ListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(strings.selectionColor),
            trailing: Wrap(
              spacing: 0,
              children: [
                for (
                  var i = 0;
                  i < PlayerPreferences.selectionColors.length;
                  i++
                )
                  IconButton(
                    tooltip: strings.selectionColorOption(i + 1),
                    onPressed: () => settings.setSelectionColor(i),
                    icon: Icon(
                      preferences.selectionColorIndex == i
                          ? Icons.radio_button_checked
                          : Icons.circle,
                      color: PlayerPreferences.selectionColors[i],
                    ),
                  ),
              ],
            ),
          ),
          OutlinedButton.icon(
            onPressed: () async {
              final confirmed = await showDialog<bool>(
                context: context,
                builder: (context) => AlertDialog(
                  title: Text(strings.resetMarkedNumbers),
                  content: Text(strings.clearManualMarksConfirm),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.pop(context, false),
                      child: Text(strings.cancel),
                    ),
                    FilledButton(
                      onPressed: () => Navigator.pop(context, true),
                      child: Text(strings.resetMarkedNumbers),
                    ),
                  ],
                ),
              );
              if (confirmed == true) await settings.clearManualMarks();
            },
            icon: const Icon(Icons.restart_alt),
            label: Text(strings.resetMarkedNumbers),
          ),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(),
            child: Text(strings.claimDialogDismiss),
          ),
        ],
      ),
    );
  }
}

class _SessionSummaryCard extends ConsumerWidget {
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

  Future<void> _confirmPurchase(BuildContext context, WidgetRef ref) async {
    final owned = state.ticketPage?.totalElements ?? 0;
    final playerRemaining = session.maxTicketsPerPlayer == null
        ? 10
        : (session.maxTicketsPerPlayer! - owned).clamp(0, 10);
    final maxQuantity = [
      10,
      session.availableSeats,
      playerRemaining,
    ].reduce((a, b) => a < b ? a : b);
    if (maxQuantity < 1) return;
    var quantity = 1;
    final selectedQuantity = await showDialog<int>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text(localizations.purchaseTicketTitle),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              DropdownButton<int>(
                key: const ValueKey('purchase-quantity'),
                value: quantity,
                items: [
                  for (var i = 1; i <= maxQuantity; i++)
                    DropdownMenuItem(
                      value: i,
                      child: Text(localizations.purchaseQuantityLabel(i)),
                    ),
                ],
                onChanged: (value) {
                  if (value != null) setDialogState(() => quantity = value);
                },
              ),
              Text(
                localizations.purchaseTotalLabel(
                  (session.ticketPrice * quantity).toStringAsFixed(2),
                  session.currency,
                ),
              ),
              const SizedBox(height: 8),
              Text(localizations.purchaseTicketBody),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(),
              child: Text(localizations.cancel),
            ),
            FilledButton(
              key: const ValueKey('confirm-ticket-purchase'),
              onPressed: () => Navigator.of(dialogContext).pop(quantity),
              child: Text(localizations.purchaseTicket),
            ),
          ],
        ),
      ),
    );
    if (selectedQuantity != null && context.mounted) {
      final result = await controller.purchaseTickets(selectedQuantity);
      final purchased = result.purchasedCount;
      final purchaseError = result.error;
      if (purchased > 0 || purchaseError?.outcomeUnknown == true) {
        try {
          await ref.read(walletRepositoryProvider).getBalance();
        } catch (_) {
          // Wallet screens fetch a fresh balance when opened.
        }
      }
      if (!context.mounted) {
        return;
      }
      final (title, body) = switch (purchaseError?.code) {
        'PLAYER_TICKET_LIMIT_REACHED' => (
          localizations.ticketLimitReachedTitle,
          session.maxTicketsPerPlayer == null
              ? localizations.errorPlayerTicketLimitReached
              : localizations.ticketLimitReachedBody(
                  session.maxTicketsPerPlayer!,
                ),
        ),
        'SESSION_CAPACITY_REACHED' => (
          localizations.ticketsSoldOutTitle,
          localizations.ticketsSoldOutBody,
        ),
        _ => (null, null),
      };
      if (purchaseError == null) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(localizations.ticketsPurchasedCount(purchased)),
          ),
        );
        return;
      }
      await showDialog<void>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: Text(title ?? localizations.errorGeneric),
          content: Text(
            '${purchased > 0 ? '${localizations.ticketsPurchasedCount(purchased)}\n\n' : ''}'
            '${body ?? purchaseError.userMessage(localizations)}',
          ),
          actions: [
            FilledButton(
              onPressed: () => Navigator.of(dialogContext).pop(),
              child: Text(localizations.claimDialogDismiss),
            ),
          ],
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
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
              onPressed:
                  !purchasesEnabled ||
                      state.isPurchasingTicket ||
                      state.purchaseOutcomeUncertain
                  ? null
                  : () => _confirmPurchase(context, ref),
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
          if (state.purchaseOutcomeUncertain) ...[
            const SizedBox(height: 8),
            Text(
              state.apiError?.userMessage(localizations) ??
                  localizations.errorTransactionUnknown,
            ),
            OutlinedButton(
              key: const ValueKey('resolve-purchase-button'),
              onPressed:
                  state.isPurchasingTicket || state.purchaseRecoveryBlocked
                  ? null
                  : () async {
                      try {
                        await ref.read(walletRepositoryProvider).getBalance();
                      } catch (_) {
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text(
                                localizations.errorTransactionUnknown,
                              ),
                            ),
                          );
                        }
                        return;
                      }
                      final error = await controller.resolvePendingPurchase();
                      if (error == null && context.mounted) {
                        try {
                          await ref.read(walletRepositoryProvider).getBalance();
                        } catch (_) {}
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text(localizations.purchaseSuccess),
                            ),
                          );
                        }
                      }
                    },
              child: Text(localizations.resolvePurchase),
            ),
          ],
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

class _TicketsCard extends ConsumerWidget {
  const _TicketsCard({
    required this.state,
    required this.controller,
    required this.localizations,
    this.fillAvailableSpace = false,
  });

  final bool fillAvailableSpace;
  final LiveGameState state;
  final LiveGameController controller;
  final AppLocalizations localizations;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final preferences = ref.watch(playerPreferencesProvider);
    final ticketPage = state.ticketPage;
    final tickets = ticketPage?.content ?? const [];
    final allTickets = state.allTickets.isNotEmpty ? state.allTickets : tickets;
    final boardTickets = _visibleTickets(allTickets, preferences, state);
    final selectedTicket = state.selectedTicket;

    return SectionCard(
      key: const ValueKey('live-tickets'),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (tickets.isEmpty)
            AppStatusView(
              title: localizations.emptyState,
              message: localizations.contractGapBody,
            )
          else ...[
            if (allTickets.length > 1) ...[
              SizedBox(
                height: 44,
                child: ListView.separated(
                  scrollDirection: Axis.horizontal,
                  itemCount: allTickets.length,
                  separatorBuilder: (_, _) => const SizedBox(width: 8),
                  itemBuilder: (context, index) {
                    final item = allTickets[index];
                    return ChoiceChip(
                      selected: preferences.selectedTicketIds.isEmpty
                          ? boardTickets.any((ticket) => ticket.id == item.id)
                          : preferences.selectedTicketIds.contains(item.id),
                      label: Text(item.publicTicketCode),
                      onSelected: (_) {
                        ref
                            .read(playerPreferencesProvider)
                            .toggleSelectedTicket(item.id);
                        controller.selectTicketById(item.id);
                      },
                    );
                  },
                ),
              ),
              const SizedBox(height: 12),
            ],
            if (selectedTicket != null) ...[
              if (fillAvailableSpace)
                Expanded(
                  child: GridView.builder(
                    key: const ValueKey('multi-ticket-board'),
                    itemCount: boardTickets.length,
                    gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                      crossAxisCount: boardTickets.length == 1 ? 1 : 2,
                      crossAxisSpacing: 8,
                      mainAxisSpacing: 8,
                      childAspectRatio: 5 / 7,
                    ),
                    itemBuilder: (context, index) => _TicketGridCard(
                      ticket: boardTickets[index],
                      calledNumbers: state.calledNumbers,
                      state: state,
                      localizations: localizations,
                    ),
                  ),
                )
              else
                _TicketGridCard(
                  ticket: selectedTicket,
                  calledNumbers: state.calledNumbers,
                  state: state,
                  localizations: localizations,
                ),
            ],
            if (allTickets.length > 1)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(
                  '${boardTickets.length} / ${preferences.cardsOnBoard} cards on board',
                  style: Theme.of(context).textTheme.labelMedium,
                ),
              ),
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
}

int _countMissedCalls(
  List<TicketResponse> tickets,
  List<int> calledNumbers,
  PlayerPreferences preferences,
) {
  var count = 0;
  for (final ticket in tickets) {
    final manual = preferences.manualMarksForTicket(ticket.id);
    for (final number in ticket.grid.expand((row) => row)) {
      if (number != 0 &&
          calledNumbers.contains(number) &&
          !manual.contains(number)) {
        count++;
      }
    }
  }
  return count;
}

List<TicketResponse> _visibleTickets(
  List<TicketResponse> tickets,
  PlayerPreferences preferences,
  LiveGameState state,
) {
  final selected = tickets
      .where((ticket) => preferences.selectedTicketIds.contains(ticket.id))
      .toList();
  final candidates = selected.isEmpty
      ? (List<TicketResponse>.of(tickets)..sort((a, b) => a.id.compareTo(b.id)))
      : selected;
  final ranked = TicketRanking.rank(
    tickets: candidates,
    called: state.calledNumbers.toSet(),
    configuredPatterns: state.session?.configuredPatterns ?? const [],
    configuredPrizePatterns: _activePrizePatterns(state),
    priorities: preferences.sortPriorities,
    claimStatuses: _ticketClaimStatuses(state),
    claimWindowOpen: state.isClaimWindowOpen,
  );
  final ordered = ranked.isEmpty
      ? List<TicketResponse>.of(candidates)
      : ranked.map((rank) => rank.ticket).toList();
  if (ranked.isEmpty) ordered.sort((a, b) => a.id.compareTo(b.id));
  return ordered.take(preferences.cardsOnBoard).toList();
}

List<String> _activePrizePatterns(LiveGameState state) =>
    (state.session?.configuredPrizes ?? const [])
        .where(
          (prize) =>
              prize.prizeType.isNotEmpty &&
              prize.amount.isFinite &&
              prize.amount > 0 &&
              prize.settlementPath.isNotEmpty,
        )
        .map((prize) => prize.prizeType.toUpperCase())
        .toSet()
        .toList();

Map<String, String> _ticketClaimStatuses(LiveGameState state) {
  final statuses = <String, String>{};
  final claims =
      (state.claimPage?.content ?? const <WinClaimRecord>[])
          .where((claim) => claim.sessionId == state.session?.id)
          .toList()
        ..sort((a, b) {
          final sequenceOrder = a.drawSequenceSnapshot.compareTo(
            b.drawSequenceSnapshot,
          );
          return sequenceOrder != 0
              ? sequenceOrder
              : a.updatedAt.compareTo(b.updatedAt);
        });
  for (final claim in claims) {
    statuses['${claim.ticketId}:${claim.claimedPattern}'] = claim.status;
  }
  return statuses;
}

String _patternLabel(AppLocalizations l, String pattern) => switch (pattern) {
  'LINE' => l.patternLine,
  'TWO_LINES' => l.patternTwoLines,
  'FULL_HOUSE' => l.patternFullHouse,
  'FOUR_CORNERS' => l.patternFourCorners,
  'POSTAGE_STAMP' => l.patternPostageStamp,
  'X_SHAPE' => l.patternXShape,
  'T_SHAPE' => l.patternTShape,
  'L_SHAPE' => l.patternLShape,
  'OUTER_FRAME' => l.patternOuterFrame,
  _ => pattern,
};

class _TicketGridCard extends ConsumerWidget {
  const _TicketGridCard({
    required this.ticket,
    required this.calledNumbers,
    required this.state,
    required this.localizations,
  });

  final TicketResponse ticket;
  final List<int> calledNumbers;
  final LiveGameState state;
  final AppLocalizations localizations;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final preferences = ref.watch(playerPreferencesProvider);
    final rank = _ticketRank(ticket, state, preferences);
    final calledSet = calledNumbers.toSet();
    final calledOnTicket = ticket.grid
        .expand((row) => row)
        .where((number) => number != 0 && calledSet.contains(number))
        .length;
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
                (preferences.autoMark
                    ? calledNumbers.contains(value)
                    : preferences
                          .manualMarksForTicket(ticket.id)
                          .contains(value));
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
              markColor: PlayerPreferences
                  .selectionColors[preferences.selectionColorIndex],
              onTap:
                  !preferences.autoMark &&
                      !isFree &&
                      calledNumbers.contains(value)
                  ? () => ref
                        .read(playerPreferencesProvider)
                        .toggleManualMark(ticket.id, value)
                  : null,
            );
          },
        ),
      ),
    );
    return AspectRatio(
      key: ValueKey('live-ticket-grid-${ticket.id}'),
      aspectRatio: 5 / 7,
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    ticket.publicTicketCode,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.labelMedium,
                  ),
                ),
                if (rank != null)
                  Expanded(
                    child: Text(
                      _ticketRankLabel(localizations, rank),
                      textAlign: TextAlign.center,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.labelSmall,
                    ),
                  ),
                Text('$calledOnTicket/24'),
                if (ticket.eligibleClaimPatterns.isNotEmpty) ...[
                  const SizedBox(width: 4),
                  Icon(Icons.emoji_events, size: 16, color: colors.primary),
                ],
              ],
            ),
          ),
          Expanded(child: grid),
        ],
      ),
    );
  }
}

String _ticketRankLabel(AppLocalizations l, TicketRank rank) =>
    switch (rank.claimState) {
      TicketClaimState.claimable => l.winningTicketReady(
        _patternLabel(l, rank.pattern),
      ),
      TicketClaimState.submitted => l.claimSubmitted,
      TicketClaimState.rejected => l.claimPatternRejected,
      TicketClaimState.awarded => l.errorTicketPrizeAwarded,
      TicketClaimState.closed => l.prizeClosed,
      TicketClaimState.approaching =>
        rank.remainingCells == 0
            ? l.patternWaitingForServer
            : l.patternToGo(
                _patternLabel(l, rank.pattern),
                rank.remainingCells,
              ),
    };

TicketRank? _ticketRank(
  TicketResponse ticket,
  LiveGameState state,
  PlayerPreferences preferences,
) {
  final result = TicketRanking.rank(
    tickets: [ticket],
    called: state.calledNumbers.toSet(),
    configuredPatterns: state.session?.configuredPatterns ?? const [],
    configuredPrizePatterns: _activePrizePatterns(state),
    priorities: preferences.sortPriorities,
    claimStatuses: _ticketClaimStatuses(state),
    claimWindowOpen: state.isClaimWindowOpen,
  );
  return result.isEmpty ? null : result.first;
}

class _ClaimCard extends ConsumerWidget {
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
  Widget build(BuildContext context, WidgetRef ref) {
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
          if (!compact) ...[
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
  const _ConnectionChip({required this.state, required this.localizations});

  final LiveGameState state;
  final AppLocalizations localizations;

  @override
  Widget build(BuildContext context) {
    return Chip(
      label: Text(_connectionLabel(state.connectionState, localizations)),
    );
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
    required this.markColor,
    this.onTap,
  });

  final int value;
  final String columnLetter;
  final bool isFree;
  final bool isCalled;
  final bool isLatest;
  final Border border;
  final AppLocalizations localizations;
  final Color markColor;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final background = isCalled
        ? markColor.withValues(alpha: isLatest ? 0.95 : 0.72)
        : isLatest
        ? colors.primaryContainer
        : isCalled
        ? colors.secondaryContainer
        : colors.surface;
    final numberColor = isLatest
        ? Colors.white
        : isCalled
        ? colors.onSecondaryContainer
        : colors.onSurface;

    return Semantics(
      label: '$columnLetter ${isFree ? localizations.freeCenterLabel : value}',
      selected: isCalled && !isFree,
      child: ExcludeSemantics(
        child: AnimatedContainer(
          duration: MediaQuery.disableAnimationsOf(context)
              ? Duration.zero
              : const Duration(milliseconds: 180),
          decoration: BoxDecoration(color: background, border: border),
          child: InkWell(
            onTap: onTap,
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
                                color: numberColor,
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
                    child: Icon(Icons.check, size: 13, color: colors.primary),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
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
