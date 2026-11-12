import 'dart:async';
import 'dart:math' as math;

import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/core/widgets/section_card.dart';
import 'package:bingo_app/src/features/game/domain/live_game_models.dart';
import 'package:flutter/material.dart';

/// Keeps the ticket's position independent of the number of calls received.
class LiveCallPanel extends StatefulWidget {
  const LiveCallPanel({
    super.key,
    required this.draws,
    required this.isLive,
    required this.connected,
    required this.finished,
  });

  final List<LiveDrawEvent> draws;
  final bool isLive;
  final bool connected;
  final bool finished;

  @override
  State<LiveCallPanel> createState() => _LiveCallPanelState();
}

class _LiveCallPanelState extends State<LiveCallPanel> {
  Timer? _timer;
  late final ValueNotifier<List<LiveDrawEvent>> _history;

  @override
  void initState() {
    super.initState();
    _history = ValueNotifier(widget.draws);
    _syncTimer();
  }

  @override
  void didUpdateWidget(covariant LiveCallPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.draws, widget.draws)) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _history.value = widget.draws;
      });
    }
    _syncTimer();
  }

  void _syncTimer() {
    if (!widget.isLive || !widget.connected || widget.draws.length < 2) {
      _timer?.cancel();
      _timer = null;
    } else {
      _timer ??= Timer.periodic(const Duration(milliseconds: 250), (_) {
        if (mounted) setState(() {});
      });
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    _history.dispose();
    super.dispose();
  }

  // The API does not publish the next draw time. Estimate the cadence from
  // recent server timestamps; never invent a five-second schedule or a call.
  Duration? get _observedInterval {
    final recent = widget.draws
        .skip(math.max(0, widget.draws.length - 6))
        .toList();
    final intervals = <int>[];
    for (var index = 1; index < recent.length; index++) {
      final milliseconds = recent[index].drawnAt
          .difference(recent[index - 1].drawnAt)
          .inMilliseconds;
      if (milliseconds > 0) intervals.add(milliseconds);
    }
    if (intervals.isEmpty) return null;
    intervals.sort();
    return Duration(milliseconds: intervals[intervals.length ~/ 2]);
  }

  Future<void> _showHistory(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (context) => SizedBox(
        height: MediaQuery.sizeOf(context).height * .7,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      l.calledNumbersTitle,
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                  ),
                  IconButton(
                    tooltip: l.closeCallHistory,
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.close),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Expanded(
                child: ValueListenableBuilder<List<LiveDrawEvent>>(
                  valueListenable: _history,
                  builder: (context, calls, _) => SingleChildScrollView(
                    key: const ValueKey('full-call-history'),
                    child: Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        for (final call in calls.reversed)
                          Chip(
                            key: ValueKey('history-call-${call.sequenceNo}'),
                            avatar: CircleAvatar(
                              backgroundColor: Theme.of(
                                context,
                              ).colorScheme.primaryContainer,
                              child: Text(call.sequenceNo.toString()),
                            ),
                            label: Text(
                              '${bingoLetter(call.numberDrawn)} ${call.numberDrawn}',
                            ),
                          ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final colors = Theme.of(context).colorScheme;
    final latest = widget.draws.isEmpty ? null : widget.draws.last;
    final recent = widget.draws.reversed.take(5).toList(growable: false);
    final scale = math.max(
      1.0,
      MediaQuery.textScalerOf(context).scale(14) / 14,
    );
    final interval = _observedInterval;
    final canEstimate =
        widget.isLive && widget.connected && interval != null && latest != null;
    final elapsed = latest == null
        ? 0
        : math.max(0, DateTime.now().difference(latest.drawnAt).inMilliseconds);
    final remaining = canEstimate
        ? math.max(0, interval.inMilliseconds - elapsed)
        : 0;
    final progress = canEstimate
        ? (elapsed / interval.inMilliseconds).clamp(0.0, 1.0)
        : 0.0;
    final timing = widget.finished
        ? l.liveConnectionFinished
        : !widget.isLive || latest == null
        ? l.waitingForFirstCall
        : !widget.connected
        ? l.liveConnectionReconnecting
        : remaining > 0
        ? l.nextCallEstimate((remaining / 1000).ceil())
        : l.waitingForNextCall;

    return Column(
      key: const ValueKey('live-call-panel'),
      children: [
        SizedBox(
          key: const ValueKey('current-call-area'),
          height: 148 + (scale - 1) * 88,
          child: SectionCard(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  l.currentNumber,
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.labelMedium?.copyWith(
                    color: colors.onSurfaceVariant,
                  ),
                ),
                Center(
                  child: Semantics(
                    liveRegion: true,
                    label: latest == null
                        ? l.waitingForFirstCall
                        : '${l.currentNumber}: ${bingoLetter(latest.numberDrawn)} ${latest.numberDrawn}',
                    child: ExcludeSemantics(
                      child: CircleAvatar(
                        radius: 32,
                        backgroundColor: colors.primaryContainer,
                        foregroundColor: colors.onPrimaryContainer,
                        child: Padding(
                          padding: const EdgeInsets.all(6),
                          child: FittedBox(
                            child: latest == null
                                ? const Text(
                                    '—',
                                    style: TextStyle(fontSize: 32),
                                  )
                                : Column(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      Text(
                                        bingoLetter(latest.numberDrawn),
                                        style: Theme.of(context)
                                            .textTheme
                                            .labelMedium
                                            ?.copyWith(
                                              color: colors.onPrimaryContainer,
                                            ),
                                      ),
                                      Text(
                                        '${latest.numberDrawn}',
                                        key: const ValueKey(
                                          'current-call-number',
                                        ),
                                        style: Theme.of(context)
                                            .textTheme
                                            .headlineMedium
                                            ?.copyWith(
                                              color: colors.onPrimaryContainer,
                                              fontWeight: FontWeight.w800,
                                            ),
                                      ),
                                    ],
                                  ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
                Text(
                  timing,
                  key: const ValueKey('call-timing'),
                  textAlign: TextAlign.center,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.labelSmall,
                ),
                LinearProgressIndicator(
                  key: const ValueKey('call-progress'),
                  value: progress,
                  minHeight: 3,
                  borderRadius: BorderRadius.circular(4),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 8),
        SectionCard(
          padding: const EdgeInsets.fromLTRB(12, 0, 12, 10),
          child: Column(
            children: [
              SizedBox(
                height: 42 * scale,
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        l.recentCalls,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.titleSmall,
                      ),
                    ),
                    ConstrainedBox(
                      constraints: BoxConstraints(
                        maxWidth: MediaQuery.sizeOf(context).width * .48,
                      ),
                      child: TextButton(
                        key: const ValueKey('view-all-calls'),
                        onPressed: widget.draws.isEmpty
                            ? null
                            : () => _showHistory(context),
                        child: Text(
                          l.viewAllCalls(widget.draws.length),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              SizedBox(
                height: 36 * scale,
                child: Row(
                  key: const ValueKey('recent-calls'),
                  children: [
                    for (var index = 0; index < 5; index++) ...[
                      if (index > 0) const SizedBox(width: 6),
                      Expanded(
                        child: Container(
                          key: index < recent.length
                              ? ValueKey(
                                  'recent-call-${recent[index].sequenceNo}',
                                )
                              : null,
                          alignment: Alignment.center,
                          padding: const EdgeInsets.symmetric(horizontal: 4),
                          decoration: BoxDecoration(
                            color: index == 0 && recent.isNotEmpty
                                ? colors.primaryContainer
                                : colors.surfaceContainerHighest,
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: FittedBox(
                            fit: BoxFit.scaleDown,
                            child: Text(
                              index < recent.length
                                  ? '${bingoLetter(recent[index].numberDrawn)} ${recent[index].numberDrawn}'
                                  : '—',
                              style: Theme.of(context).textTheme.labelLarge
                                  ?.copyWith(
                                    color: index == 0 && recent.isNotEmpty
                                        ? colors.onPrimaryContainer
                                        : colors.onSurface,
                                  ),
                            ),
                          ),
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

String bingoLetter(int number) =>
    const ['B', 'I', 'N', 'G', 'O'][((number - 1) ~/ 15).clamp(0, 4)];
