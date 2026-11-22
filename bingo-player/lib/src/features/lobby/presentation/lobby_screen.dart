import 'dart:async';

import 'package:bingo_player/src/core/localization/ethiopian_date_time.dart';
import 'package:bingo_player/src/core/network/api_provider.dart';
import 'package:bingo_player/src/features/support/presentation/support_shortcut.dart';
import 'package:bingo_player/src/core/widgets/responsive_content.dart';
import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/core/widgets/app_status_view.dart';
import 'package:bingo_player/src/core/widgets/purchase_progress_dialog.dart';
import 'package:bingo_player/src/core/widgets/section_card.dart';
import 'package:bingo_player/src/features/lobby/data/lobby_repository.dart';
import 'package:bingo_player/src/features/lobby/domain/lobby_models.dart';
import 'package:bingo_player/src/features/game/presentation/session_detail_screen.dart';
import 'package:bingo_player/src/features/player_content/presentation/game_history_screen.dart';
import 'package:bingo_player/src/features/lobby/presentation/ticket_preview_screen.dart';
import 'package:bingo_player/src/core/config/app_config.dart';
import 'package:bingo_player/src/features/auth/data/auth_session_controller.dart';
import 'package:bingo_player/src/features/game/application/live_game_controller.dart';
import 'package:bingo_player/src/features/game/application/live_game_transport.dart';
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

final myGameSessionIdsProvider = FutureProvider.autoDispose<Set<int>>((
  ref,
) async {
  ref.watch(authSessionControllerProvider);
  final api = ref.read(playerContentApiProvider);
  final sessionIds = <int>{};
  var page = 0;
  while (true) {
    final result = await api.getGameHistory(page: page, size: 100);
    sessionIds.addAll(result.content.map((entry) => entry.sessionId));
    if (result.last || result.content.isEmpty) break;
    page++;
  }
  return sessionIds;
});

final lobbySessionUpdatesTransportProvider =
    Provider<LobbySessionUpdatesTransport>((ref) {
      return StompLiveGameTransport();
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

class LobbyScreen extends ConsumerStatefulWidget {
  const LobbyScreen({super.key});

  @override
  ConsumerState<LobbyScreen> createState() => _LobbyScreenState();
}

class _LobbyScreenState extends ConsumerState<LobbyScreen>
    with WidgetsBindingObserver {
  late final LobbySessionUpdatesTransport _sessionUpdatesTransport;
  ProviderSubscription<AuthSessionState>? _authSessionSubscription;
  Timer? _reconnectTimer;
  bool _isLobbyVisible = false;
  bool _isAppActive = true;
  bool _connecting = false;
  int _reconnectAttempts = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _sessionUpdatesTransport = ref.read(lobbySessionUpdatesTransportProvider);
    _authSessionSubscription = ref.listenManual(authSessionControllerProvider, (
      _,
      next,
    ) {
      if (next.isAuthenticated) {
        _connectSessionUpdates();
      } else {
        unawaited(_disconnectSessionUpdates());
      }
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final isVisible = TickerMode.valuesOf(context).enabled;
    if (_isLobbyVisible == isVisible) return;
    _isLobbyVisible = isVisible;
    if (_isLobbyVisible && _isAppActive) {
      _refreshSessions();
      _connectSessionUpdates();
    } else {
      unawaited(_disconnectSessionUpdates());
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _isAppActive = state == AppLifecycleState.resumed;
    if (_isAppActive && _isLobbyVisible) {
      _refreshSessions();
      _connectSessionUpdates();
    } else {
      unawaited(_disconnectSessionUpdates());
    }
  }

  void _refreshSessions() {
    if (!mounted || !_isAppActive || !_isLobbyVisible) return;
    ref.invalidate(activeSessionsProvider);
    ref.invalidate(myGameSessionIdsProvider);
  }

  void _connectSessionUpdates() {
    if (!mounted ||
        !_isAppActive ||
        !_isLobbyVisible ||
        _connecting ||
        _sessionUpdatesTransport.isConnected) {
      return;
    }
    final authSession = ref.read(authSessionControllerProvider).session;
    if (authSession == null || authSession.isExpired) return;
    final uri = ref.read(appConfigProvider).liveGameWebSocketUri;
    if (uri == null) {
      FlutterError.reportError(
        FlutterErrorDetails(
          exception: StateError('The live-game WebSocket URL is unavailable.'),
          library: 'bingo_player',
          context: ErrorDescription('connecting to lobby session updates'),
        ),
      );
      return;
    }

    _reconnectTimer?.cancel();
    _reconnectTimer = null;
    _connecting = true;
    unawaited(
      _sessionUpdatesTransport
          .connectToSessionUpdates(
            uri: uri,
            accessToken: authSession.accessToken,
            onSessionChanged: _refreshSessions,
            onError: _scheduleReconnect,
            onDisconnected: _scheduleReconnect,
          )
          .then((_) {
            if (!mounted) return;
            _reconnectAttempts = 0;
            _refreshSessions();
          })
          .catchError((Object error, StackTrace stackTrace) {
            _scheduleReconnect(error);
          })
          .whenComplete(() {
            _connecting = false;
          }),
    );
  }

  void _scheduleReconnect([Object? error]) {
    if (!mounted ||
        !_isAppActive ||
        !_isLobbyVisible ||
        _reconnectTimer != null) {
      return;
    }
    if (error != null) {
      FlutterError.reportError(
        FlutterErrorDetails(
          exception: error,
          library: 'bingo_player',
          context: ErrorDescription('connecting to lobby session updates'),
        ),
      );
    }
    final delaySeconds = 1 << _reconnectAttempts.clamp(0, 4).toInt();
    _reconnectAttempts++;
    _reconnectTimer = Timer(Duration(seconds: delaySeconds), () {
      _reconnectTimer = null;
      _connectSessionUpdates();
    });
  }

  Future<void> _disconnectSessionUpdates() async {
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
    try {
      await _sessionUpdatesTransport.disconnect();
    } catch (error, stackTrace) {
      FlutterError.reportError(
        FlutterErrorDetails(
          exception: error,
          stack: stackTrace,
          library: 'bingo_player',
          context: ErrorDescription('disconnecting lobby session updates'),
        ),
      );
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _authSessionSubscription?.close();
    unawaited(_disconnectSessionUpdates());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final sessionsAsync = ref.watch(activeSessionsProvider);
    final purchasesEnabled = ref
        .watch(appConfigProvider)
        .playerTicketPurchasesEnabled;

    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Bingo'),
          bottom: TabBar(
            isScrollable: true,
            tabs: [
              Tab(text: localizations.myGamesTab),
              Tab(text: localizations.upcomingGamesTab),
            ],
          ),
          actions: [
            const SupportShortcut(),
            IconButton(
              tooltip: localizations.gameHistoryTitle,
              onPressed: () => Navigator.of(context).push<void>(
                MaterialPageRoute<void>(
                  builder: (_) => const GameHistoryScreen(),
                ),
              ),
              icon: const Icon(Icons.sports_esports_outlined),
            ),
            IconButton(
              key: const ValueKey('lobby-refresh-button'),
              tooltip: localizations.refresh,
              onPressed: _refreshSessions,
              icon: const Icon(Icons.refresh),
            ),
          ],
        ),
        body: ResponsiveContent(
          child: sessionsAsync.when(
            skipLoadingOnRefresh: sessionsAsync.hasValue,
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
              onAction: _refreshSessions,
            ),
            data: (sessions) => TabBarView(
              children: [
                ref
                    .watch(myGameSessionIdsProvider)
                    .when(
                      loading: () =>
                          const Center(child: CircularProgressIndicator()),
                      error: (error, stack) => AppStatusView(
                        title: localizations.errorGeneric,
                        message: localizations.gameHistoryLoadError,
                        onAction: () =>
                            ref.invalidate(myGameSessionIdsProvider),
                      ),
                      data: (ownedIds) => _SessionList(
                        sessions:
                            sessions
                                .where(
                                  (s) =>
                                      ownedIds.contains(s.id) &&
                                      const {
                                        'OPEN',
                                        'STARTED',
                                        'DRAWING',
                                      }.contains(s.state.toUpperCase()),
                                )
                                .toList()
                              ..sort(
                                (a, b) => a.scheduledStartAt.compareTo(
                                  b.scheduledStartAt,
                                ),
                              ),
                        emptyTitle: localizations.noMyGames,
                        emptyBody: localizations.myGamesEmptyBody,
                        onRefresh: _refreshSessions,
                        onBuy: null,
                        purchasesEnabled: purchasesEnabled,
                      ),
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
                  onRefresh: _refreshSessions,
                  onBuy: (session) => _buyTickets(context, ref, session),
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
                final isLive = const {
                  'STARTED',
                  'DRAWING',
                }.contains(session.state.toUpperCase());
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
                        Text(
                          '${localizations.gameStarts}: ${EthiopianDateTime.dateTime(session.scheduledStartAt, languageCode: Localizations.localeOf(context).languageCode)}',
                        ),
                        const SizedBox(height: 8),
                        Wrap(
                          spacing: 12,
                          runSpacing: 4,
                          children: [
                            Text(
                              '${localizations.sessionStatusLabel}: ${session.state}',
                              style: Theme.of(context).textTheme.bodySmall,
                            ),
                            Text(
                              '${localizations.roomPriceLabel}: ${session.ticketPrice} ${session.currency}',
                              style: Theme.of(context).textTheme.bodySmall,
                            ),
                            Text(
                              '${localizations.availableSeatsLabel}: ${session.availableSeats}',
                              style: Theme.of(context).textTheme.bodySmall,
                            ),
                            if (onBuy != null) ...[
                              Text(
                                '${localizations.gamePatterns}: ${session.configuredPatterns.isEmpty ? localizations.ticketPatternsUnavailable : session.configuredPatterns.map((pattern) => pattern.replaceAll('_', ' ')).join(', ')}',
                                style: Theme.of(context).textTheme.bodySmall,
                              ),
                              Text(
                                '${localizations.prizeLabel}: ${session.configuredPrizes.isEmpty ? '—' : session.configuredPrizes.map((prize) => '${prize.prizeType.replaceAll('_', ' ')} ${prize.amount} ${session.currency}').join(' · ')}',
                                style: Theme.of(context).textTheme.bodySmall,
                              ),
                              if (session.configuredPrizes.any(
                                (prize) => prize.settlementPath == 'AGENT_CASH',
                              ))
                                Text(
                                  localizations.prizeCashUnresolved,
                                  style: Theme.of(context).textTheme.bodySmall
                                      ?.copyWith(
                                        color: Theme.of(
                                          context,
                                        ).colorScheme.error,
                                      ),
                                ),
                            ],
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
                                    builder: (_) => const GameRulesScreen(),
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
                            ] else ...[
                              const SizedBox(width: 8),
                              Expanded(
                                child: FilledButton.icon(
                                  key: ValueKey('live-game-${session.id}'),
                                  onPressed: () => Navigator.of(context).push(
                                    MaterialPageRoute<void>(
                                      builder: (_) =>
                                          SessionDetailScreen(session: session),
                                    ),
                                  ),
                                  icon: const Icon(
                                    Icons.sports_esports_outlined,
                                  ),
                                  label: Text(
                                    isLive
                                        ? localizations.liveGameTitle
                                        : localizations.myTicketsTitle,
                                  ),
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
    final result = await runWithProgressDialog<PurchaseFlowResult?>(
      context: context,
      message: localizations.loading,
      operation: () async {
        final wallet = await ref.read(walletRepositoryProvider).getBalance();
        final total = session.ticketPrice * selected;
        if (wallet.totalBalance < total) return null;
        final controllerSubscription = ref.listenManual(
          liveGameControllerProvider(session.id),
          (_, _) {},
        );
        try {
          return await ref
              .read(liveGameControllerProvider(session.id).notifier)
              .purchaseTickets(selected);
        } finally {
          controllerSubscription.close();
        }
      },
    );
    if (!context.mounted) return;
    if (result == null) {
      final total = session.ticketPrice * selected;
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
      return;
    }
    ref.invalidate(walletBalanceProvider);
    ref.invalidate(activeSessionsProvider);
    ref.invalidate(myGameSessionIdsProvider);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          result.error == null
              ? localizations.ticketsPurchasedCount(result.purchasedCount)
              : '${result.purchasedCount > 0 ? '${localizations.ticketsPurchasedCount(result.purchasedCount)}. ' : ''}${result.error!.userMessage(localizations)}',
        ),
      ),
    );
    if (result.purchasedCount > 0 && context.mounted) {
      await Navigator.of(context).push<void>(
        MaterialPageRoute<void>(
          builder: (_) => SessionDetailScreen(session: session),
        ),
      );
    }
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
