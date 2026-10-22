import 'dart:async';

import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/core/widgets/app_status_view.dart';
import 'package:bingo_app/src/core/widgets/section_card.dart';
import 'package:bingo_app/src/features/game/application/live_game_controller.dart';
import 'package:bingo_app/src/features/game/domain/live_game_models.dart';
import 'package:bingo_app/src/features/game/domain/ticket_models.dart';
import 'package:bingo_app/src/features/lobby/domain/lobby_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class SessionDetailScreen extends ConsumerStatefulWidget {
  const SessionDetailScreen({super.key, required this.session});

  final GameSession session;

  @override
  ConsumerState<SessionDetailScreen> createState() => _SessionDetailScreenState();
}

class _SessionDetailScreenState extends ConsumerState<SessionDetailScreen> with WidgetsBindingObserver {
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
    final controller = ref.read(liveGameControllerProvider(widget.session.id).notifier);
    if (state == AppLifecycleState.resumed) {
      unawaited(controller.resume());
    } else if (state == AppLifecycleState.paused || state == AppLifecycleState.inactive) {
      unawaited(controller.pause());
    }
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final state = ref.watch(liveGameControllerProvider(widget.session.id));
    final controller = ref.read(liveGameControllerProvider(widget.session.id).notifier);
    final session = state.session ?? widget.session;

    return Scaffold(
      appBar: AppBar(
        title: Text(localizations.liveGameTitle),
        actions: [
          IconButton(
            tooltip: localizations.refresh,
            onPressed: controller.refresh,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: controller.refresh,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(16),
          children: [
            if (state.isLoading) ...[
              const SizedBox(height: 72),
              Center(child: Text(localizations.loading)),
            ] else ...[
              if (state.errorMessage != null) ...[
                SectionCard(
                  child: Text(state.errorMessage!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                ),
                const SizedBox(height: 16),
              ],
              _SessionSummaryCard(session: session, state: state, controller: controller, localizations: localizations),
              const SizedBox(height: 16),
              _CalledNumbersCard(draws: state.draws, localizations: localizations),
              const SizedBox(height: 16),
              _TicketsCard(state: state, controller: controller, localizations: localizations),
              const SizedBox(height: 16),
              _ClaimCard(state: state, controller: controller, localizations: localizations),
              const SizedBox(height: 16),
              _ClaimHistoryCard(state: state, localizations: localizations),
              const SizedBox(height: 16),
              SectionCard(child: Text(localizations.claimSettlementUnavailableBody)),
            ],
          ],
        ),
      ),
    );
  }
}

class _SessionSummaryCard extends StatelessWidget {
  const _SessionSummaryCard({required this.session, required this.state, required this.controller, required this.localizations});

  final GameSession session;
  final LiveGameState state;
  final LiveGameController controller;
  final AppLocalizations localizations;

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
                    Text(session.sessionName, style: Theme.of(context).textTheme.titleLarge),
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
              _InfoChip(label: localizations.sessionStatusLabel, value: session.state),
              _InfoChip(label: localizations.ticketCountLabel, value: session.ticketCount.toString()),
              _InfoChip(label: localizations.drawCountLabel, value: state.draws.length.toString()),
              _InfoChip(label: localizations.roomCapacityLabel, value: session.sessionCapacity.toString()),
              _InfoChip(label: localizations.availableSeatsLabel, value: session.availableSeats.toString()),
            ],
          ),
          const SizedBox(height: 12),
          if (session.state == 'OPEN')
            FilledButton.icon(
              key: const ValueKey('buy-ticket-button'),
              onPressed: state.isPurchasingTicket ? null : controller.purchaseTicket,
              icon: state.isPurchasingTicket
                  ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
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
            Text(localizations.purchaseSuccess, style: TextStyle(color: Theme.of(context).colorScheme.primary)),
          ],
          if (state.isTerminalSession) ...[
            const SizedBox(height: 12),
            Text(localizations.liveConnectionFinished, style: TextStyle(color: Theme.of(context).colorScheme.primary)),
          ],
        ],
      ),
    );
  }
}

class _CalledNumbersCard extends StatelessWidget {
  const _CalledNumbersCard({required this.draws, required this.localizations});

  final List<LiveDrawEvent> draws;
  final AppLocalizations localizations;

  @override
  Widget build(BuildContext context) {
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(localizations.calledNumbersTitle, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 12),
          if (draws.isEmpty)
            AppStatusView(title: localizations.emptyState, message: localizations.contractGapBody)
          else
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final draw in draws)
                  _DrawChip(
                    number: draw.numberDrawn,
                    sequenceNo: draw.sequenceNo,
                    isLatest: draw.sequenceNo == draws.last.sequenceNo,
                  ),
              ],
            ),
        ],
      ),
    );
  }
}

class _TicketsCard extends StatelessWidget {
  const _TicketsCard({required this.state, required this.controller, required this.localizations});

  final LiveGameState state;
  final LiveGameController controller;
  final AppLocalizations localizations;

  @override
  Widget build(BuildContext context) {
    final ticketPage = state.ticketPage;
    final tickets = ticketPage?.content ?? const [];
    final selectedTicket = state.selectedTicket;

    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(child: Text(localizations.myTicketsTitle, style: Theme.of(context).textTheme.titleMedium)),
              if (ticketPage != null)
                Text('${localizations.ticketPageLabel} ${ticketPage.page + 1}/${ticketPage.totalPages == 0 ? 1 : ticketPage.totalPages}'),
            ],
          ),
          const SizedBox(height: 12),
          if (tickets.isEmpty)
            AppStatusView(title: localizations.emptyState, message: localizations.contractGapBody)
          else ...[
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (var index = 0; index < tickets.length; index += 1)
                  ChoiceChip(
                    selected: index == state.selectedTicketIndex,
                    label: Text(tickets[index].publicTicketCode),
                    onSelected: (_) => controller.selectTicketIndex(index),
                  ),
              ],
            ),
            const SizedBox(height: 12),
            if (selectedTicket != null) ...[
              _TicketGridCard(ticket: selectedTicket, calledNumbers: state.calledNumbers, localizations: localizations),
              const SizedBox(height: 12),
            ],
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                TextButton(
                  onPressed: state.canPageBackTickets ? () => controller.selectTicketPage(state.ticketPageIndex - 1) : null,
                  child: Text(localizations.previous),
                ),
                TextButton(
                  onPressed: state.canPageForwardTickets ? () => controller.selectTicketPage(state.ticketPageIndex + 1) : null,
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

class _TicketGridCard extends StatelessWidget {
  const _TicketGridCard({required this.ticket, required this.calledNumbers, required this.localizations});

  final TicketResponse ticket;
  final List<int> calledNumbers;
  final AppLocalizations localizations;

  @override
  Widget build(BuildContext context) {
    final latestCalled = calledNumbers.isEmpty ? null : calledNumbers.last;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(ticket.publicTicketCode, style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 12),
        AspectRatio(
          aspectRatio: 1,
          child: GridView.builder(
            physics: const NeverScrollableScrollPhysics(),
            itemCount: 25,
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 5,
              crossAxisSpacing: 8,
              mainAxisSpacing: 8,
            ),
            itemBuilder: (context, index) {
              final row = index ~/ 5;
              final column = index % 5;
              final value = ticket.grid[row][column];
              final isFree = value == 0;
              final isCalled = isFree || calledNumbers.contains(value);
              final isLatest = value == latestCalled;
              return _TicketCell(
                key: ValueKey('ticket-cell-$row-$column'),
                value: value,
                isFree: isFree,
                isCalled: isCalled,
                isLatest: isLatest,
                localizations: localizations,
              );
            },
          ),
        ),
      ],
    );
  }
}

class _ClaimCard extends StatelessWidget {
  const _ClaimCard({required this.state, required this.controller, required this.localizations});

  final LiveGameState state;
  final LiveGameController controller;
  final AppLocalizations localizations;

  @override
  Widget build(BuildContext context) {
    final session = state.session;
    final ticket = state.selectedTicket;
    final patterns = session?.configuredPatterns ?? const [];

    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(localizations.claimSettlementLabel, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            key: const ValueKey('claim-pattern-field'),
            initialValue: state.selectedClaimPattern,
            decoration: InputDecoration(labelText: localizations.claimPatternLabel),
            items: [
              for (final pattern in patterns)
                DropdownMenuItem<String>(
                  value: pattern,
                  child: Text(pattern),
                ),
            ],
            onChanged: patterns.isEmpty ? null : (value) => value == null ? null : controller.selectClaimPattern(value),
          ),
          const SizedBox(height: 12),
          FilledButton(
            key: const ValueKey('claim-submit-button'),
            onPressed: state.isSubmittingClaim || ticket == null || state.selectedClaimPattern == null || state.isTerminalSession
                ? null
                : controller.submitClaim,
            child: state.isSubmittingClaim
                ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                : Text(localizations.submitClaim),
          ),
          if (ticket == null) ...[
            const SizedBox(height: 12),
            Text(localizations.contractGapBody),
          ],
          if (state.infoMessage != null) ...[
            const SizedBox(height: 12),
            Text(
              state.infoMessage == 'CLAIM_SUBMITTED' ? localizations.claimSubmitted : state.infoMessage!,
              style: TextStyle(color: Theme.of(context).colorScheme.primary),
            ),
          ],
          if (state.errorMessage != null) ...[
            const SizedBox(height: 12),
            Text(state.errorMessage!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
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
    final claims = List<WinClaimRecord>.from(state.claimPage?.content ?? const <WinClaimRecord>[])
      ..sort((left, right) => right.createdAt.compareTo(left.createdAt));
    final sessionId = state.session?.id;
    final sessionClaims = sessionId == null ? claims : claims.where((claim) => claim.sessionId == sessionId).toList();

    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(localizations.claimHistoryTitle, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 12),
          if (sessionClaims.isEmpty)
            AppStatusView(title: localizations.emptyState, message: localizations.contractGapBody)
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
                              _ClaimStatusChip(status: claim.status, localizations: localizations),
                            ],
                          ),
                          const SizedBox(height: 8),
                          Text('${localizations.drawSnapshotLabel}: ${claim.drawSequenceSnapshot}'),
                          Text('${localizations.calledNumbersLabel}: ${claim.calledNumbersSnapshot.join(', ')}'),
                          Text('${localizations.serverValidationLabel}: ${claim.serverValidationPassed ? localizations.claimPatternApproved : localizations.claimPatternFailed}'),
                          if (claim.reviewNote != null) Text('${localizations.reviewNoteLabel}: ${claim.reviewNote}'),
                          if (claim.entitlementId != null) Text('${localizations.claimSettlementLabel}: #${claim.entitlementId}'),
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

String _connectionLabel(LiveGameConnectionState connectionState, AppLocalizations localizations) {
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

class _DrawChip extends StatelessWidget {
  const _DrawChip({required this.number, required this.sequenceNo, required this.isLatest});

  final int number;
  final int sequenceNo;
  final bool isLatest;

  @override
  Widget build(BuildContext context) {
    return AnimatedScale(
      duration: const Duration(milliseconds: 220),
      scale: isLatest ? 1.04 : 1.0,
      child: Chip(
        avatar: CircleAvatar(
          backgroundColor: Theme.of(context).colorScheme.primaryContainer,
          child: Text(sequenceNo.toString()),
        ),
        label: Text(number.toString()),
      ),
    );
  }
}

class _TicketCell extends StatelessWidget {
  const _TicketCell({
    super.key,
    required this.value,
    required this.isFree,
    required this.isCalled,
    required this.isLatest,
    required this.localizations,
  });

  final int value;
  final bool isFree;
  final bool isCalled;
  final bool isLatest;
  final AppLocalizations localizations;

  @override
  Widget build(BuildContext context) {
    final background = isFree
      ? Theme.of(context).colorScheme.primaryContainer.withValues(alpha: 0.75)
      : isCalled
        ? Theme.of(context).colorScheme.secondaryContainer.withValues(alpha: 0.9)
        : Theme.of(context).colorScheme.surfaceContainerHighest;
    return AnimatedScale(
      duration: const Duration(milliseconds: 220),
      scale: isLatest ? 1.04 : 1.0,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 220),
        decoration: BoxDecoration(
          color: background,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
            color: isCalled ? Theme.of(context).colorScheme.primary : Theme.of(context).dividerColor,
            width: isLatest ? 2 : 1,
          ),
        ),
        child: Center(
          child: Text(
            isFree ? localizations.freeCenterLabel : value.toString(),
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
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
