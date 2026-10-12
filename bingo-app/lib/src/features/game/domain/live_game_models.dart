class LiveDrawEvent {
  const LiveDrawEvent({
    required this.sessionId,
    required this.sequenceNo,
    required this.numberDrawn,
    required this.drawnAt,
    this.id,
    this.operationId,
  });

  final int? id;
  final int sessionId;
  final int sequenceNo;
  final int numberDrawn;
  final String? operationId;
  final DateTime drawnAt;

  factory LiveDrawEvent.fromJson(Map<String, dynamic> json) {
    return LiveDrawEvent(
      id: (json['id'] as num?)?.toInt(),
      sessionId: (json['sessionId'] as num?)?.toInt() ?? 0,
      sequenceNo: (json['sequenceNo'] as num?)?.toInt() ?? 0,
      numberDrawn: (json['numberDrawn'] as num?)?.toInt() ?? 0,
      operationId: json['operationId']?.toString(),
      drawnAt: DateTime.tryParse(json['drawnAt']?.toString() ?? '') ?? DateTime.fromMillisecondsSinceEpoch(0),
    );
  }
}

class WinClaimRecord {
  const WinClaimRecord({
    required this.id,
    required this.ticketId,
    required this.sessionId,
    required this.claimantId,
    required this.claimedPattern,
    required this.status,
    required this.serverValidationPassed,
    required this.drawSequenceSnapshot,
    required this.calledNumbersSnapshot,
    required this.operationId,
    required this.createdAt,
    required this.updatedAt,
    this.serverValidationCode,
    this.serverValidationMessage,
    this.evidenceJson,
    this.reviewNote,
    this.reviewedBy,
    this.reviewedAt,
    this.entitlementId,
  });

  final int id;
  final int ticketId;
  final int sessionId;
  final int claimantId;
  final String claimedPattern;
  final String status;
  final bool serverValidationPassed;
  final String? serverValidationCode;
  final String? serverValidationMessage;
  final int drawSequenceSnapshot;
  final List<int> calledNumbersSnapshot;
  final String? evidenceJson;
  final String operationId;
  final String? reviewNote;
  final int? reviewedBy;
  final DateTime? reviewedAt;
  final int? entitlementId;
  final DateTime createdAt;
  final DateTime updatedAt;

  factory WinClaimRecord.fromJson(Map<String, dynamic> json) {
    return WinClaimRecord(
      id: (json['id'] as num?)?.toInt() ?? 0,
      ticketId: (json['ticketId'] as num?)?.toInt() ?? 0,
      sessionId: (json['sessionId'] as num?)?.toInt() ?? 0,
      claimantId: (json['claimantId'] as num?)?.toInt() ?? 0,
      claimedPattern: json['claimedPattern']?.toString() ?? '',
      status: json['status']?.toString() ?? '',
      serverValidationPassed: json['serverValidationPassed'] == true,
      serverValidationCode: json['serverValidationCode']?.toString(),
      serverValidationMessage: json['serverValidationMessage']?.toString(),
      drawSequenceSnapshot: (json['drawSequenceSnapshot'] as num?)?.toInt() ?? 0,
      calledNumbersSnapshot: (json['calledNumbersSnapshot'] as List? ?? const [])
          .map((item) => (item as num?)?.toInt() ?? 0)
          .toList(),
      evidenceJson: json['evidenceJson']?.toString(),
      operationId: json['operationId']?.toString() ?? '',
      reviewNote: json['reviewNote']?.toString(),
      reviewedBy: (json['reviewedBy'] as num?)?.toInt(),
      reviewedAt: DateTime.tryParse(json['reviewedAt']?.toString() ?? ''),
      entitlementId: (json['entitlementId'] as num?)?.toInt(),
      createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? '') ?? DateTime.fromMillisecondsSinceEpoch(0),
      updatedAt: DateTime.tryParse(json['updatedAt']?.toString() ?? '') ?? DateTime.fromMillisecondsSinceEpoch(0),
    );
  }

  bool get isFinalStatus => status == 'APPROVED' || status == 'REJECTED' || status == 'FAILED_SERVER_VALIDATION';

  bool get hasSettlementRecord => entitlementId != null;
}
