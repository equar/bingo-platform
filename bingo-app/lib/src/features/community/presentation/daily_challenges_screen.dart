import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/features/community/data/community_repository.dart';
import 'package:bingo_app/src/features/community/domain/community_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

class DailyChallengesScreen extends ConsumerStatefulWidget {
  const DailyChallengesScreen({super.key});
  @override
  ConsumerState<DailyChallengesScreen> createState() =>
      _DailyChallengesScreenState();
}

class _DailyChallengesScreenState extends ConsumerState<DailyChallengesScreen> {
  late Future<DailyProgress> _future;
  final _keys = <String, String>{};
  String? _busy;
  @override
  void initState() {
    super.initState();
    _future = ref.read(communityRepositoryProvider).daily();
  }

  void _reload() {
    setState(() {
      _future = ref.read(communityRepositoryProvider).daily();
    });
  }

  Future<void> _claim(DailyChallenge challenge, String day) async {
    if (_busy != null) return;
    setState(() => _busy = challenge.code);
    try {
      final key = _keys.putIfAbsent(
        '$day/${challenge.code}',
        () => const Uuid().v4(),
      );
      await ref
          .read(communityRepositoryProvider)
          .claim(challenge.code, day, key);
      if (mounted) _reload();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    } finally {
      if (mounted) setState(() => _busy = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: AppBar(
        title: Text(l.homeDailyChallenge),
        actions: [
          IconButton(
            onPressed: _reload,
            tooltip: l.retry,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: FutureBuilder<DailyProgress>(
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
                Card(
                  color: const Color(0xFFEAF8EE),
                  child: Padding(
                    padding: const EdgeInsets.all(20),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '${data.totalPoints} ${l.communityPoints}',
                          style: Theme.of(context).textTheme.headlineMedium,
                        ),
                        const SizedBox(height: 8),
                        Text('${data.businessDate} · ${data.timezone}'),
                        Text(
                          '${l.challengeResets}: ${data.resetsAt.toLocal()}',
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                Text(l.challengeRules),
                const SizedBox(height: 16),
                for (final challenge in data.challenges)
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(18),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Expanded(
                                child: Text(
                                  switch (challenge.code) {
                                    'FINISH_GAME' => l.challengeFinish,
                                    'EXPLORE_ROOMS' => l.challengeExplore,
                                    _ => l.challengeBingo,
                                  },
                                  style: Theme.of(
                                    context,
                                  ).textTheme.titleMedium,
                                ),
                              ),
                              const SizedBox(width: 8),
                              Text('+${challenge.points} ${l.communityPoints}'),
                            ],
                          ),
                          const SizedBox(height: 8),
                          Text(switch (challenge.code) {
                            'FINISH_GAME' => l.challengeFinishBody,
                            'EXPLORE_ROOMS' => l.challengeExploreBody,
                            _ => l.challengeBingoBody,
                          }),
                          const SizedBox(height: 14),
                          LinearProgressIndicator(
                            value: challenge.target == 0
                                ? 0
                                : challenge.progress / challenge.target,
                          ),
                          const SizedBox(height: 8),
                          Text('${challenge.progress}/${challenge.target}'),
                          Align(
                            alignment: Alignment.centerRight,
                            child: challenge.claimed
                                ? Chip(
                                    avatar: const Icon(Icons.check, size: 18),
                                    label: Text(l.challengeClaimed),
                                  )
                                : FilledButton(
                                    onPressed:
                                        _busy != null || !challenge.completed
                                        ? null
                                        : () => _claim(
                                            challenge,
                                            data.businessDate,
                                          ),
                                    child: Text(
                                      _busy == challenge.code
                                          ? l.loading
                                          : l.challengeClaim,
                                    ),
                                  ),
                          ),
                        ],
                      ),
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
