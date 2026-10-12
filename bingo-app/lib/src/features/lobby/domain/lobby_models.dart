class BingoPrizeConfig {
  const BingoPrizeConfig({
    required this.prizeType,
    required this.amount,
    required this.settlementPath,
    this.maxWinners,
  });

  final String prizeType;
  final num amount;
  final String settlementPath;
  final int? maxWinners;

  factory BingoPrizeConfig.fromJson(Map<String, dynamic> json) {
    return BingoPrizeConfig(
      prizeType: json['prizeType']?.toString() ?? '',
      amount: json['amount'] as num? ?? 0,
      settlementPath: json['settlementPath']?.toString() ?? '',
      maxWinners: (json['maxWinners'] as num?)?.toInt(),
    );
  }
}

class BingoRoom {
  const BingoRoom({
    required this.id,
    required this.name,
    required this.ticketPrice,
    required this.currency,
    required this.timezone,
    required this.scheduleRule,
    required this.sessionCapacity,
    required this.maxTicketsPerPlayer,
    required this.ticketSalesCloseOnSessionStart,
    required this.allowMultipleWinnersPerPrize,
    required this.active,
    required this.configuredPatterns,
    required this.configuredPrizes,
    required this.createdAt,
    required this.updatedAt,
    this.description,
  });

  final int id;
  final String name;
  final String? description;
  final num ticketPrice;
  final String currency;
  final String timezone;
  final String scheduleRule;
  final int sessionCapacity;
  final int maxTicketsPerPlayer;
  final bool ticketSalesCloseOnSessionStart;
  final bool allowMultipleWinnersPerPrize;
  final bool active;
  final List<String> configuredPatterns;
  final List<BingoPrizeConfig> configuredPrizes;
  final DateTime createdAt;
  final DateTime updatedAt;

  factory BingoRoom.fromJson(Map<String, dynamic> json) {
    return BingoRoom(
      id: (json['id'] as num?)?.toInt() ?? 0,
      name: json['name']?.toString() ?? '',
      description: json['description']?.toString(),
      ticketPrice: json['ticketPrice'] as num? ?? 0,
      currency: json['currency']?.toString() ?? '',
      timezone: json['timezone']?.toString() ?? '',
      scheduleRule: json['scheduleRule']?.toString() ?? '',
      sessionCapacity: (json['sessionCapacity'] as num?)?.toInt() ?? 0,
      maxTicketsPerPlayer: (json['maxTicketsPerPlayer'] as num?)?.toInt() ?? 0,
      ticketSalesCloseOnSessionStart: json['ticketSalesCloseOnSessionStart'] == true,
      allowMultipleWinnersPerPrize: json['allowMultipleWinnersPerPrize'] == true,
      active: json['active'] == true,
      configuredPatterns: (json['configuredPatterns'] as List? ?? const []).map((item) => item.toString()).toList(),
      configuredPrizes: (json['configuredPrizes'] as List? ?? const [])
          .whereType<Map>()
          .map((item) => BingoPrizeConfig.fromJson(Map<String, dynamic>.from(item)))
          .toList(),
      createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? '') ?? DateTime.fromMillisecondsSinceEpoch(0),
      updatedAt: DateTime.tryParse(json['updatedAt']?.toString() ?? '') ?? DateTime.fromMillisecondsSinceEpoch(0),
    );
  }
}

class GameSession {
  const GameSession({
    required this.id,
    required this.roomId,
    required this.roomName,
    required this.sessionName,
    required this.state,
    required this.scheduledStartAt,
    required this.ticketPrice,
    required this.currency,
    required this.sessionCapacity,
    required this.ticketCount,
    required this.drawCount,
    required this.createdAt,
    required this.updatedAt,
    this.startedAt,
    this.endedAt,
    this.maxTicketsPerPlayer,
    this.ticketSalesCloseOnSessionStart,
    this.allowMultipleWinnersPerPrize,
    this.configuredPatterns = const [],
    this.configuredPrizes = const [],
  });

  final int id;
  final int roomId;
  final String roomName;
  final String sessionName;
  final String state;
  final DateTime scheduledStartAt;
  final DateTime? startedAt;
  final DateTime? endedAt;
  final num ticketPrice;
  final String currency;
  final int sessionCapacity;
  final int? maxTicketsPerPlayer;
  final bool? ticketSalesCloseOnSessionStart;
  final bool? allowMultipleWinnersPerPrize;
  final List<String> configuredPatterns;
  final List<BingoPrizeConfig> configuredPrizes;
  final int ticketCount;
  final int drawCount;
  final DateTime createdAt;
  final DateTime updatedAt;

  int get availableSeats => (sessionCapacity - ticketCount).clamp(0, sessionCapacity);

  factory GameSession.fromJson(Map<String, dynamic> json) {
    return GameSession(
      id: (json['id'] as num?)?.toInt() ?? 0,
      roomId: (json['roomId'] as num?)?.toInt() ?? 0,
      roomName: json['roomName']?.toString() ?? '',
      sessionName: json['sessionName']?.toString() ?? '',
      state: json['state']?.toString() ?? '',
      scheduledStartAt: DateTime.tryParse(json['scheduledStartAt']?.toString() ?? '') ?? DateTime.fromMillisecondsSinceEpoch(0),
      startedAt: DateTime.tryParse(json['startedAt']?.toString() ?? ''),
      endedAt: DateTime.tryParse(json['endedAt']?.toString() ?? ''),
      ticketPrice: json['ticketPrice'] as num? ?? 0,
      currency: json['currency']?.toString() ?? '',
      sessionCapacity: (json['sessionCapacity'] as num?)?.toInt() ?? 0,
      maxTicketsPerPlayer: (json['maxTicketsPerPlayer'] as num?)?.toInt(),
      ticketSalesCloseOnSessionStart: json['ticketSalesCloseOnSessionStart'] as bool?,
      allowMultipleWinnersPerPrize: json['allowMultipleWinnersPerPrize'] as bool?,
      configuredPatterns: (json['configuredPatterns'] as List? ?? const []).map((item) => item.toString()).toList(),
      configuredPrizes: (json['configuredPrizes'] as List? ?? const [])
          .whereType<Map>()
          .map((item) => BingoPrizeConfig.fromJson(Map<String, dynamic>.from(item)))
          .toList(),
      ticketCount: (json['ticketCount'] as num?)?.toInt() ?? 0,
      drawCount: (json['drawCount'] as num?)?.toInt() ?? 0,
      createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? '') ?? DateTime.fromMillisecondsSinceEpoch(0),
      updatedAt: DateTime.tryParse(json['updatedAt']?.toString() ?? '') ?? DateTime.fromMillisecondsSinceEpoch(0),
    );
  }
}