import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/features/community/data/community_repository.dart';
import 'package:bingo_app/src/features/community/domain/community_models.dart';
import 'package:bingo_app/src/features/lobby/data/lobby_repository.dart';
import 'package:bingo_app/src/features/lobby/domain/lobby_models.dart';
import 'package:bingo_app/src/features/lobby/presentation/lobby_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

class PrivateGamesScreen extends ConsumerStatefulWidget {
  const PrivateGamesScreen({super.key});
  @override
  ConsumerState<PrivateGamesScreen> createState() => _PrivateGamesScreenState();
}

class _PrivateGamesScreenState extends ConsumerState<PrivateGamesScreen> {
  late Future<List<PrivateGame>> _future;
  @override
  void initState() {
    super.initState();
    _future = ref.read(communityRepositoryProvider).privateGames();
  }

  void _reload() => setState(
    () => _future = ref.read(communityRepositoryProvider).privateGames(),
  );
  Future<void> _open(PrivateGame game) async {
    await Navigator.push<void>(
      context,
      MaterialPageRoute(builder: (_) => PrivateGameScreen(roomId: game.roomId)),
    );
    if (mounted) _reload();
  }

  Future<void> _create() async {
    final result = await showDialog<Map<String, dynamic>>(
      context: context,
      barrierDismissible: false,
      builder: (_) => const _CreateGameDialog(),
    );
    if (!mounted || result == null) return;
    final game = PrivateGame.fromJson(
      Map<String, dynamic>.from(result['game'] as Map),
    );
    try {
      final invite = await ref
          .read(communityRepositoryProvider)
          .manage(game.roomId, 'invite');
      if (mounted) {
        await _showInvite(
          context,
          invite['code'] as String,
          invite['expiresAt'] as String?,
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    }
    if (mounted) await _open(game);
  }

  Future<void> _join() async {
    final game = await showDialog<PrivateGame>(
      context: context,
      builder: (_) => const _JoinGameDialog(),
    );
    if (mounted && game != null) await _open(game);
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: AppBar(
        title: Text(l.homePrivateGame),
        actions: [
          IconButton(
            onPressed: _reload,
            tooltip: l.retry,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          _reload();
          await _future;
        },
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Text(l.privateIntro),
            const SizedBox(height: 16),
            Wrap(
              spacing: 12,
              runSpacing: 8,
              children: [
                FilledButton.icon(
                  onPressed: _create,
                  icon: const Icon(Icons.add),
                  label: Text(l.privateCreate),
                ),
                OutlinedButton.icon(
                  onPressed: _join,
                  icon: const Icon(Icons.key),
                  label: Text(l.privateJoin),
                ),
              ],
            ),
            const SizedBox(height: 20),
            FutureBuilder<List<PrivateGame>>(
              future: _future,
              builder: (context, snapshot) {
                if (snapshot.hasError) {
                  return _ErrorMessage(
                    error: snapshot.error!,
                    onRetry: _reload,
                  );
                }
                if (!snapshot.hasData) {
                  return const Center(child: CircularProgressIndicator());
                }
                if (snapshot.data!.isEmpty) {
                  return Padding(
                    padding: const EdgeInsets.all(24),
                    child: Text(l.privateEmpty),
                  );
                }
                return Column(
                  children: snapshot.data!
                      .map(
                        (game) => Card(
                          child: ListTile(
                            leading: Icon(
                              game.host
                                  ? Icons.workspace_premium
                                  : Icons.lock_outline,
                            ),
                            title: Text(game.name),
                            subtitle: Text(
                              '${game.memberCount}/${game.capacity} · ${game.state}',
                            ),
                            trailing: const Icon(Icons.chevron_right),
                            onTap: () => _open(game),
                          ),
                        ),
                      )
                      .toList(),
                );
              },
            ),
          ],
        ),
      ),
    );
  }
}

class PrivateGameScreen extends ConsumerStatefulWidget {
  const PrivateGameScreen({super.key, required this.roomId});
  final int roomId;
  @override
  ConsumerState<PrivateGameScreen> createState() => _PrivateGameScreenState();
}

class _PrivateGameScreenState extends ConsumerState<PrivateGameScreen> {
  late Future<Map<String, dynamic>> _future;
  bool _busy = false;
  @override
  void initState() {
    super.initState();
    _future = ref.read(communityRepositoryProvider).details(widget.roomId);
  }

  void _reload() => setState(
    () =>
        _future = ref.read(communityRepositoryProvider).details(widget.roomId),
  );
  Future<void> _action(String action) async {
    final l = AppLocalizations.of(context)!;
    if (action == 'cancel' || action == 'invite') {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (c) => AlertDialog(
          title: Text(action == 'invite' ? l.privateInvite : l.privateCancel),
          content: Text(
            action == 'invite'
                ? l.privateRotateWarning
                : l.privateCancelWarning,
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(c, false),
              child: Text(l.communityBack),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(c, true),
              child: Text(l.communityContinue),
            ),
          ],
        ),
      );
      if (confirmed != true || !mounted) return;
    }
    setState(() => _busy = true);
    try {
      final result = await ref
          .read(communityRepositoryProvider)
          .manage(widget.roomId, action);
      if (!mounted) return;
      if (action == 'invite') {
        await _showInvite(
          context,
          result['code'] as String,
          result['expiresAt'] as String?,
        );
      }
      if (mounted) _reload();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: AppBar(
        title: Text(l.homePrivateGame),
        actions: [
          IconButton(
            onPressed: _reload,
            icon: const Icon(Icons.refresh),
            tooltip: l.retry,
          ),
        ],
      ),
      body: FutureBuilder<Map<String, dynamic>>(
        future: _future,
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return _ErrorMessage(error: snapshot.error!, onRetry: _reload);
          }
          if (!snapshot.hasData) {
            return const Center(child: CircularProgressIndicator());
          }
          final data = snapshot.data!;
          final game = PrivateGame.fromJson(
            Map<String, dynamic>.from(data['game'] as Map),
          );
          final session = GameSession.fromJson(
            Map<String, dynamic>.from(data['session'] as Map),
          );
          return ListView(
            padding: const EdgeInsets.all(20),
            children: [
              Text(game.name, style: Theme.of(context).textTheme.headlineSmall),
              const SizedBox(height: 8),
              Text('${game.memberCount}/${game.capacity} · ${game.state}'),
              const SizedBox(height: 16),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(l.privateAgentReference),
                      const SizedBox(height: 8),
                      SelectableText(
                        '${l.communitySession}: ${game.sessionId}',
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                      const SizedBox(height: 12),
                      Text(
                        '${l.roomPriceLabel}: ${session.ticketPrice} ${session.currency}',
                      ),
                      Text(
                        '${l.gamePatterns}: ${session.configuredPatterns.join(', ')}',
                      ),
                      const SizedBox(height: 8),
                      Text(l.privateRulesFixed),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: () => Navigator.push<void>(
                  context,
                  MaterialPageRoute(
                    builder: (_) => RoomDetailsScreen(roomId: game.roomId),
                  ),
                ),
                child: Text(l.privateOpenRoom),
              ),
              if (game.host && game.open) ...[
                const SizedBox(height: 12),
                Text(l.privateStartHelp),
                const SizedBox(height: 8),
                OutlinedButton.icon(
                  onPressed: _busy ? null : () => _action('invite'),
                  icon: const Icon(Icons.share_outlined),
                  label: Text(l.privateInvite),
                ),
                FilledButton.icon(
                  onPressed: _busy ? null : () => _action('start'),
                  icon: const Icon(Icons.play_arrow),
                  label: Text(l.privateStart),
                ),
                TextButton(
                  onPressed: _busy ? null : () => _action('cancel'),
                  child: Text(l.privateCancel),
                ),
              ],
              if (_busy) const LinearProgressIndicator(),
            ],
          );
        },
      ),
    );
  }
}

Future<void> _showInvite(BuildContext context, String code, String? expiry) {
  final l = AppLocalizations.of(context)!;
  return showDialog<void>(
    context: context,
    builder: (c) => AlertDialog(
      title: Text(l.privateInvite),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SelectableText(
            code,
            style: const TextStyle(
              fontSize: 25,
              fontWeight: FontWeight.bold,
              letterSpacing: 2,
            ),
          ),
          const SizedBox(height: 12),
          Text(l.privateInviteHelp),
          if (expiry != null) Text(DateTime.parse(expiry).toLocal().toString()),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(c),
          child: Text(l.communityBack),
        ),
        FilledButton.icon(
          onPressed: () async {
            await Clipboard.setData(ClipboardData(text: code));
            if (c.mounted) {
              ScaffoldMessenger.of(
                c,
              ).showSnackBar(SnackBar(content: Text(l.communityCopied)));
            }
          },
          icon: const Icon(Icons.copy),
          label: Text(l.communityCopy),
        ),
      ],
    ),
  );
}

class _ErrorMessage extends StatelessWidget {
  const _ErrorMessage({required this.error, required this.onRetry});
  final Object error;
  final VoidCallback onRetry;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.all(20),
    child: Column(
      children: [
        Text(error.toString()),
        TextButton(
          onPressed: onRetry,
          child: Text(AppLocalizations.of(context)!.retry),
        ),
      ],
    ),
  );
}

class _JoinGameDialog extends ConsumerStatefulWidget {
  const _JoinGameDialog();
  @override
  ConsumerState<_JoinGameDialog> createState() => _JoinGameDialogState();
}

class _JoinGameDialogState extends ConsumerState<_JoinGameDialog> {
  final _code = TextEditingController();
  String _key = const Uuid().v4();
  bool _busy = false;
  String? _error;
  @override
  void dispose() {
    _code.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_code.text.trim().isEmpty) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final game = await ref
          .read(communityRepositoryProvider)
          .join(_code.text.trim(), _key);
      if (mounted) Navigator.pop(context, game);
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return AlertDialog(
      title: Text(l.privateJoin),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextField(
            controller: _code,
            textCapitalization: TextCapitalization.characters,
            maxLength: 24,
            enabled: !_busy,
            onChanged: (_) => _key = const Uuid().v4(),
            decoration: InputDecoration(labelText: l.privateCode),
          ),
          if (_error != null) Text(_error!),
        ],
      ),
      actions: [
        TextButton(
          onPressed: _busy ? null : () => Navigator.pop(context),
          child: Text(l.communityBack),
        ),
        FilledButton(
          onPressed: _busy ? null : _submit,
          child: Text(_busy ? l.loading : l.privateJoin),
        ),
      ],
    );
  }
}

class _CreateGameDialog extends ConsumerStatefulWidget {
  const _CreateGameDialog();
  @override
  ConsumerState<_CreateGameDialog> createState() => _CreateGameDialogState();
}

class _CreateGameDialogState extends ConsumerState<_CreateGameDialog> {
  final _name = TextEditingController();
  late final Future<List<BingoRoom>> _rooms;
  int? _template;
  int _minutes = 10;
  bool _busy = false;
  String? _error;
  String _key = const Uuid().v4();
  DateTime? _fixedStart;
  @override
  void initState() {
    super.initState();
    _rooms = ref.read(lobbyRepositoryProvider).getRooms();
  }

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  void _changed() {
    _key = const Uuid().v4();
    _fixedStart = null;
  }

  Future<void> _submit() async {
    if (_template == null || _name.text.trim().isEmpty) return;
    _fixedStart ??= DateTime.now().add(Duration(minutes: _minutes));
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final result = await ref
          .read(communityRepositoryProvider)
          .create(_template!, _name.text.trim(), _fixedStart!, _key);
      if (mounted) Navigator.pop(context, result);
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return AlertDialog(
      title: Text(l.privateCreate),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(l.privateTemplateHelp),
            const SizedBox(height: 12),
            TextField(
              controller: _name,
              maxLength: 80,
              enabled: !_busy,
              onChanged: (_) => _changed(),
              decoration: InputDecoration(labelText: l.privateName),
            ),
            FutureBuilder<List<BingoRoom>>(
              future: _rooms,
              builder: (context, snapshot) {
                if (snapshot.hasError) return Text(l.errorGeneric);
                if (!snapshot.hasData) return const LinearProgressIndicator();
                if (snapshot.data!.isEmpty) return Text(l.lobbyEmptyTitle);
                return DropdownButtonFormField<int>(
                  initialValue: _template,
                  isExpanded: true,
                  decoration: InputDecoration(labelText: l.privateTemplate),
                  items: snapshot.data!
                      .map(
                        (room) => DropdownMenuItem(
                          value: room.id,
                          child: Text(
                            '${room.name} · ${room.ticketPrice} ${room.currency}',
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      )
                      .toList(),
                  onChanged: _busy
                      ? null
                      : (v) => setState(() {
                          _template = v;
                          _changed();
                        }),
                );
              },
            ),
            DropdownButtonFormField<int>(
              initialValue: _minutes,
              decoration: InputDecoration(labelText: l.privateStartsIn),
              items: [5, 10, 15, 30, 60]
                  .map(
                    (m) => DropdownMenuItem(
                      value: m,
                      child: Text('$m ${l.communityMinutes}'),
                    ),
                  )
                  .toList(),
              onChanged: _busy
                  ? null
                  : (v) => setState(() {
                      _minutes = v!;
                      _changed();
                    }),
            ),
            if (_error != null) Text(_error!),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: _busy ? null : () => Navigator.pop(context),
          child: Text(l.communityBack),
        ),
        FilledButton(
          onPressed: _busy ? null : _submit,
          child: Text(_busy ? l.loading : l.privateCreate),
        ),
      ],
    );
  }
}
