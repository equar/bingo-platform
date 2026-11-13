class PrivateGame {
  const PrivateGame({
    required this.roomId,
    required this.sessionId,
    required this.name,
    required this.state,
    required this.host,
    required this.active,
    required this.memberCount,
    required this.capacity,
    required this.scheduledStartAt,
  });
  final int roomId, sessionId, memberCount, capacity;
  final String name, state;
  final bool host, active;
  final DateTime scheduledStartAt;
  bool get open => state == 'OPEN' && active;
  factory PrivateGame.fromJson(Map<String, dynamic> j) => PrivateGame(
    roomId: (j['roomId'] as num).toInt(),
    sessionId: (j['sessionId'] as num).toInt(),
    name: j['name'] as String,
    state: j['state'] as String,
    host: j['host'] == true,
    active: j['active'] == true,
    memberCount: (j['memberCount'] as num).toInt(),
    capacity: (j['capacity'] as num).toInt(),
    scheduledStartAt: DateTime.parse(j['scheduledStartAt'] as String),
  );
}

class DailyChallenge {
  const DailyChallenge({
    required this.code,
    required this.target,
    required this.progress,
    required this.points,
    required this.completed,
    required this.claimed,
  });
  final String code;
  final int target, progress, points;
  final bool completed, claimed;
  factory DailyChallenge.fromJson(Map<String, dynamic> j) => DailyChallenge(
    code: j['code'] as String,
    target: (j['target'] as num).toInt(),
    progress: (j['progress'] as num).toInt(),
    points: (j['points'] as num).toInt(),
    completed: j['completed'] == true,
    claimed: j['claimed'] == true,
  );
}

class DailyProgress {
  const DailyProgress({
    required this.businessDate,
    required this.timezone,
    required this.resetsAt,
    required this.totalPoints,
    required this.challenges,
  });
  final String businessDate, timezone;
  final DateTime resetsAt;
  final int totalPoints;
  final List<DailyChallenge> challenges;
  factory DailyProgress.fromJson(Map<String, dynamic> j) => DailyProgress(
    businessDate: j['businessDate'] as String,
    timezone: j['timezone'] as String,
    resetsAt: DateTime.parse(j['resetsAt'] as String),
    totalPoints: (j['totalPoints'] as num).toInt(),
    challenges: (j['challenges'] as List)
        .map(
          (v) => DailyChallenge.fromJson(Map<String, dynamic>.from(v as Map)),
        )
        .toList(),
  );
}

class RankingEntry {
  const RankingEntry({
    required this.rank,
    required this.alias,
    required this.points,
    required this.me,
  });
  final int rank, points;
  final String alias;
  final bool me;
  factory RankingEntry.fromJson(Map<String, dynamic> j) => RankingEntry(
    rank: (j['rank'] as num).toInt(),
    alias: j['alias'] as String,
    points: (j['points'] as num).toInt(),
    me: j['me'] == true,
  );
}

class Ranking {
  const Ranking({
    required this.entries,
    required this.totalPlayers,
    required this.page,
    required this.size,
    required this.myPoints,
    required this.myRank,
    required this.alias,
    required this.visible,
    required this.timezone,
  });
  final List<RankingEntry> entries;
  final int totalPlayers, page, size, myPoints;
  final int? myRank;
  final String? alias;
  final bool visible;
  final String timezone;
  factory Ranking.fromJson(Map<String, dynamic> j) {
    final profile = Map<String, dynamic>.from(j['profile'] as Map);
    return Ranking(
      entries: (j['entries'] as List)
          .map(
            (v) => RankingEntry.fromJson(Map<String, dynamic>.from(v as Map)),
          )
          .toList(),
      totalPlayers: (j['totalPlayers'] as num).toInt(),
      page: (j['page'] as num).toInt(),
      size: (j['size'] as num).toInt(),
      myPoints: (j['myPoints'] as num).toInt(),
      myRank: (j['myRank'] as num?)?.toInt(),
      alias: profile['alias'] as String?,
      visible: profile['visible'] == true,
      timezone: j['timezone'] as String,
    );
  }
}
