import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/features/community/data/community_repository.dart';
import 'package:bingo_app/src/features/community/domain/community_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class LeaderboardScreen extends ConsumerStatefulWidget {
  const LeaderboardScreen({super.key});
  @override
  ConsumerState<LeaderboardScreen> createState() => _LeaderboardScreenState();
}

class _LeaderboardScreenState extends ConsumerState<LeaderboardScreen> {
  late Future<Ranking> _future;
  String _period = 'WEEKLY';
  int _page = 0;
  @override
  void initState() {
    super.initState();
    _future = ref.read(communityRepositoryProvider).leaderboard(_period, _page);
  }

  void _reload() {
    setState(() {
      _future = ref
          .read(communityRepositoryProvider)
          .leaderboard(_period, _page);
    });
  }

  Future<void> _profile(Ranking ranking) async {
    final changed = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (_) =>
          _PublicProfileDialog(alias: ranking.alias, visible: ranking.visible),
    );
    if (mounted && changed == true) {
      _page = 0;
      _reload();
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: AppBar(
        title: Text(l.homeLeaderboard),
        actions: [
          IconButton(
            onPressed: _reload,
            tooltip: l.retry,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 12, 20, 0),
            child: DropdownButtonFormField<String>(
              initialValue: _period,
              decoration: InputDecoration(labelText: l.leaderboardPeriod),
              items: [
                DropdownMenuItem(
                  value: 'DAILY',
                  child: Text(l.leaderboardDaily),
                ),
                DropdownMenuItem(
                  value: 'WEEKLY',
                  child: Text(l.leaderboardWeekly),
                ),
                DropdownMenuItem(
                  value: 'ALL_TIME',
                  child: Text(l.leaderboardAllTime),
                ),
              ],
              onChanged: (v) {
                if (v != null) {
                  _period = v;
                  _page = 0;
                  _reload();
                }
              },
            ),
          ),
          Expanded(
            child: FutureBuilder<Ranking>(
              future: _future,
              builder: (context, snapshot) {
                if (snapshot.hasError) {
                  return Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(snapshot.error.toString()),
                        TextButton(onPressed: _reload, child: Text(l.retry)),
                      ],
                    ),
                  );
                }
                if (snapshot.connectionState == ConnectionState.waiting ||
                    !snapshot.hasData) {
                  return const Center(child: CircularProgressIndicator());
                }
                final data = snapshot.data!;
                return RefreshIndicator(
                  onRefresh: () async {
                    _reload();
                    try {
                      await _future;
                    } catch (_) {
                      // The FutureBuilder presents the load error and retry action.
                    }
                  },
                  child: ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.all(20),
                    children: [
                      Text(l.leaderboardRules),
                      const SizedBox(height: 12),
                      Card(
                        color: const Color(0xFFF2ECFF),
                        child: Padding(
                          padding: const EdgeInsets.all(18),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                '${data.myPoints} ${l.communityPoints}',
                                style: Theme.of(
                                  context,
                                ).textTheme.headlineSmall,
                              ),
                              Text(
                                '${l.leaderboardYourRank}: ${data.myRank ?? '—'}',
                              ),
                              const SizedBox(height: 8),
                              Text(
                                data.visible
                                    ? l.leaderboardVisible
                                    : l.leaderboardHidden,
                              ),
                              TextButton.icon(
                                onPressed: () => _profile(data),
                                icon: const Icon(Icons.privacy_tip_outlined),
                                label: Text(l.leaderboardProfile),
                              ),
                            ],
                          ),
                        ),
                      ),
                      if (data.entries.isEmpty)
                        Padding(
                          padding: const EdgeInsets.all(24),
                          child: Text(l.leaderboardEmpty),
                        ),
                      for (final row in data.entries)
                        Card(
                          color: row.me ? const Color(0xFFEBF4FF) : null,
                          child: ListTile(
                            leading: CircleAvatar(child: Text('${row.rank}')),
                            title: Text(row.alias),
                            trailing: Text(
                              '${row.points} ${l.communityPoints}',
                            ),
                            subtitle: row.me ? Text(l.leaderboardYou) : null,
                          ),
                        ),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          TextButton(
                            onPressed: _page == 0
                                ? null
                                : () {
                                    _page--;
                                    _reload();
                                  },
                            child: Text(l.previous),
                          ),
                          Text('${_page + 1}'),
                          TextButton(
                            onPressed:
                                _page >= 1000 ||
                                    (_page + 1) * data.size >= data.totalPlayers
                                ? null
                                : () {
                                    _page++;
                                    _reload();
                                  },
                            child: Text(l.next),
                          ),
                        ],
                      ),
                      Text(
                        data.timezone,
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _PublicProfileDialog extends ConsumerStatefulWidget {
  const _PublicProfileDialog({required this.alias, required this.visible});
  final String? alias;
  final bool visible;
  @override
  ConsumerState<_PublicProfileDialog> createState() =>
      _PublicProfileDialogState();
}

class _PublicProfileDialogState extends ConsumerState<_PublicProfileDialog> {
  late final TextEditingController _alias;
  late bool _visible;
  bool _busy = false;
  String? _error;
  @override
  void initState() {
    super.initState();
    _alias = TextEditingController(text: widget.alias ?? '');
    _visible = widget.visible;
  }

  @override
  void dispose() {
    _alias.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final value = _alias.text.trim();
      await ref
          .read(communityRepositoryProvider)
          .saveProfile(value.isEmpty ? null : value, _visible);
      if (mounted) Navigator.pop(context, true);
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return PopScope(
      canPop: !_busy,
      child: AlertDialog(
        title: Text(l.leaderboardProfile),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(l.leaderboardPrivacy),
              const SizedBox(height: 12),
              TextField(
                controller: _alias,
                enabled: !_busy,
                maxLength: 24,
                decoration: InputDecoration(labelText: l.leaderboardAlias),
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(l.leaderboardOptIn),
                value: _visible,
                onChanged: _busy ? null : (v) => setState(() => _visible = v),
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
            onPressed: _busy ? null : _save,
            child: Text(_busy ? l.loading : l.communitySave),
          ),
        ],
      ),
    );
  }
}
