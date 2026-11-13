class PlayerAppContent {
  const PlayerAppContent({
    this.bannerTitleEn,
    this.bannerTitleAm,
    this.bannerBodyEn,
    this.bannerBodyAm,
    this.bannerImageUrl,
    this.termsUrl,
    this.telegramUrl,
    this.tiktokUrl,
    this.supportPhoneNumbers,
  });

  final String? bannerTitleEn;
  final String? bannerTitleAm;
  final String? bannerBodyEn;
  final String? bannerBodyAm;
  final String? bannerImageUrl;
  final String? termsUrl;
  final String? telegramUrl;
  final String? tiktokUrl;
  final String? supportPhoneNumbers;

  factory PlayerAppContent.fromJson(Map<String, dynamic> json) =>
      PlayerAppContent(
        bannerTitleEn: json['bannerTitleEn']?.toString(),
        bannerTitleAm: json['bannerTitleAm']?.toString(),
        bannerBodyEn: json['bannerBodyEn']?.toString(),
        bannerBodyAm: json['bannerBodyAm']?.toString(),
        bannerImageUrl: json['bannerImageUrl']?.toString(),
        termsUrl: json['termsUrl']?.toString(),
        telegramUrl: json['telegramUrl']?.toString(),
        tiktokUrl: json['tiktokUrl']?.toString(),
        supportPhoneNumbers: json['supportPhoneNumbers']?.toString(),
      );
}

class PlayerAnnouncement {
  const PlayerAnnouncement({
    required this.id,
    required this.titleEn,
    required this.titleAm,
    required this.bodyEn,
    required this.bodyAm,
    required this.published,
    required this.read,
    required this.createdAt,
  });

  final int id;
  final String titleEn;
  final String? titleAm;
  final String bodyEn;
  final String? bodyAm;
  final bool published;
  final bool read;
  final DateTime createdAt;

  factory PlayerAnnouncement.fromJson(Map<String, dynamic> json) =>
      PlayerAnnouncement(
        id: (json['id'] as num?)?.toInt() ?? 0,
        titleEn: json['titleEn']?.toString() ?? '',
        titleAm: json['titleAm']?.toString(),
        bodyEn: json['bodyEn']?.toString() ?? '',
        bodyAm: json['bodyAm']?.toString(),
        published: json['published'] == true,
        read: json['read'] == true,
        createdAt:
            DateTime.tryParse(json['createdAt']?.toString() ?? '') ??
            DateTime.fromMillisecondsSinceEpoch(0),
      );
}

class PlayerGameHistoryItem {
  const PlayerGameHistoryItem({
    required this.ticketId,
    required this.sessionId,
    required this.roomName,
    required this.sessionName,
    required this.sessionState,
    required this.ticketCode,
    required this.purchasedAt,
  });

  final int ticketId;
  final int sessionId;
  final String roomName;
  final String sessionName;
  final String sessionState;
  final String ticketCode;
  final DateTime purchasedAt;

  factory PlayerGameHistoryItem.fromJson(Map<String, dynamic> json) =>
      PlayerGameHistoryItem(
        ticketId: (json['ticketId'] as num?)?.toInt() ?? 0,
        sessionId: (json['sessionId'] as num?)?.toInt() ?? 0,
        roomName: json['roomName']?.toString() ?? '',
        sessionName: json['sessionName']?.toString() ?? '',
        sessionState: json['sessionState']?.toString() ?? '',
        ticketCode: json['ticketCode']?.toString() ?? '',
        purchasedAt:
            DateTime.tryParse(json['purchasedAt']?.toString() ?? '') ??
            DateTime.fromMillisecondsSinceEpoch(0),
      );
}
