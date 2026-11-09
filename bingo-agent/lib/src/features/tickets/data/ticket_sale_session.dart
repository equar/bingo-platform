class TicketSaleSession {
  const TicketSaleSession({
    required this.id,
    required this.roomName,
    required this.sessionName,
    required this.scheduledStartAt,
    required this.ticketPrice,
    required this.currency,
    required this.sessionCapacity,
    required this.ticketCount,
  });

  final int id;
  final String roomName;
  final String sessionName;
  final DateTime scheduledStartAt;
  final num ticketPrice;
  final String currency;
  final int sessionCapacity;
  final int ticketCount;

  int get seatsLeft => sessionCapacity - ticketCount;

  factory TicketSaleSession.fromJson(Map<String, dynamic> json) =>
      TicketSaleSession(
        id: (json['id'] as num?)?.toInt() ?? 0,
        roomName: json['roomName']?.toString() ?? '',
        sessionName: json['sessionName']?.toString() ?? '',
        scheduledStartAt: DateTime.tryParse(
              json['scheduledStartAt']?.toString() ?? '',
            ) ??
            DateTime.fromMillisecondsSinceEpoch(0),
        ticketPrice: json['ticketPrice'] as num? ?? 0,
        currency: json['currency']?.toString() ?? '',
        sessionCapacity: (json['sessionCapacity'] as num?)?.toInt() ?? 0,
        ticketCount: (json['ticketCount'] as num?)?.toInt() ?? 0,
      );
}
