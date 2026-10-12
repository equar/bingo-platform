class TicketResponse {
  const TicketResponse({
    required this.id,
    required this.sessionId,
    required this.ownerId,
    required this.publicTicketCode,
    required this.operationId,
    required this.grid,
    required this.purchasedAt,
  });

  final int id;
  final int sessionId;
  final int ownerId;
  final String publicTicketCode;
  final String operationId;
  final List<List<int>> grid;
  final DateTime purchasedAt;

  factory TicketResponse.fromJson(Map<String, dynamic> json) {
    return TicketResponse(
      id: (json['id'] as num?)?.toInt() ?? 0,
      sessionId: (json['sessionId'] as num?)?.toInt() ?? 0,
      ownerId: (json['ownerId'] as num?)?.toInt() ?? 0,
      publicTicketCode: json['publicTicketCode']?.toString() ?? '',
      operationId: json['operationId']?.toString() ?? '',
      grid: (json['grid'] as List? ?? const [])
          .map((row) => (row as List? ?? const []).map((cell) => (cell as num?)?.toInt() ?? 0).toList())
          .toList(),
      purchasedAt: DateTime.tryParse(json['purchasedAt']?.toString() ?? '') ?? DateTime.fromMillisecondsSinceEpoch(0),
    );
  }
}

class TicketPurchaseResponse {
  const TicketPurchaseResponse({required this.ticket});

  final TicketResponse ticket;

  factory TicketPurchaseResponse.fromJson(Map<String, dynamic> json) {
    return TicketPurchaseResponse(ticket: TicketResponse.fromJson(Map<String, dynamic>.from(json['ticket'] as Map)));
  }
}