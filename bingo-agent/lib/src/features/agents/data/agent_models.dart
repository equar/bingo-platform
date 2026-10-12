class AgentDashboard {
  AgentDashboard({required this.agentId, required this.status, required this.availableFloat, required this.clearingFloat, required this.ticketsSold, required this.commissionAccruals, required this.topUpRequests, required this.generatedAt});

  final int agentId;
  final String status;
  final String availableFloat;
  final String clearingFloat;
  final int ticketsSold;
  final int commissionAccruals;
  final int topUpRequests;
  final DateTime generatedAt;

  factory AgentDashboard.fromJson(Map<String, dynamic> json) => AgentDashboard(
        agentId: (json['agentId'] as num?)?.toInt() ?? 0,
        status: json['status']?.toString() ?? '',
        availableFloat: json['availableFloat']?.toString() ?? '0',
        clearingFloat: json['clearingFloat']?.toString() ?? '0',
        ticketsSold: (json['ticketsSold'] as num?)?.toInt() ?? 0,
        commissionAccruals: (json['commissionAccruals'] as num?)?.toInt() ?? 0,
        topUpRequests: (json['topUpRequests'] as num?)?.toInt() ?? 0,
        generatedAt: DateTime.tryParse(json['generatedAt']?.toString() ?? '') ?? DateTime.now(),
      );
}

class AgentFloatBalance {
  AgentFloatBalance({required this.agentId, required this.floatId, required this.currency, required this.availableBalance, required this.clearingBalance, required this.updatedAt});

  final int agentId;
  final int floatId;
  final String currency;
  final String availableBalance;
  final String clearingBalance;
  final DateTime updatedAt;

  factory AgentFloatBalance.fromJson(Map<String, dynamic> json) => AgentFloatBalance(
        agentId: (json['agentId'] as num?)?.toInt() ?? 0,
        floatId: (json['floatId'] as num?)?.toInt() ?? 0,
        currency: json['currency']?.toString() ?? '',
        availableBalance: json['availableBalance']?.toString() ?? '0',
        clearingBalance: json['clearingBalance']?.toString() ?? '0',
        updatedAt: DateTime.tryParse(json['updatedAt']?.toString() ?? '') ?? DateTime.now(),
      );
}

class AgentFloatTransaction {
  AgentFloatTransaction({required this.id, required this.transactionType, required this.amount, required this.availableBefore, required this.availableAfter, required this.clearingBefore, required this.clearingAfter, required this.relatedEntityType, required this.relatedEntityId, required this.operationId, required this.note, required this.createdAt});

  final int id;
  final String transactionType;
  final String amount;
  final String availableBefore;
  final String availableAfter;
  final String clearingBefore;
  final String clearingAfter;
  final String? relatedEntityType;
  final int? relatedEntityId;
  final String operationId;
  final String? note;
  final DateTime createdAt;

  factory AgentFloatTransaction.fromJson(Map<String, dynamic> json) => AgentFloatTransaction(
        id: (json['id'] as num?)?.toInt() ?? 0,
        transactionType: json['transactionType']?.toString() ?? '',
        amount: json['amount']?.toString() ?? '0',
        availableBefore: json['availableBefore']?.toString() ?? '0',
        availableAfter: json['availableAfter']?.toString() ?? '0',
        clearingBefore: json['clearingBefore']?.toString() ?? '0',
        clearingAfter: json['clearingAfter']?.toString() ?? '0',
        relatedEntityType: json['relatedEntityType']?.toString(),
        relatedEntityId: (json['relatedEntityId'] as num?)?.toInt(),
        operationId: json['operationId']?.toString() ?? '',
        note: json['note']?.toString(),
        createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? '') ?? DateTime.now(),
      );
}

class CommissionAccrual {
  CommissionAccrual({required this.id, required this.ticketId, required this.sessionId, required this.saleAmount, required this.commissionAmount, required this.status, required this.operationId, required this.createdAt, required this.settledAt, required this.settlementReference, required this.settlementNote});

  final int id;
  final int ticketId;
  final int sessionId;
  final String saleAmount;
  final String commissionAmount;
  final String status;
  final String operationId;
  final DateTime createdAt;
  final DateTime? settledAt;
  final String? settlementReference;
  final String? settlementNote;

  factory CommissionAccrual.fromJson(Map<String, dynamic> json) => CommissionAccrual(
        id: (json['id'] as num?)?.toInt() ?? 0,
        ticketId: (json['ticketId'] as num?)?.toInt() ?? 0,
        sessionId: (json['sessionId'] as num?)?.toInt() ?? 0,
        saleAmount: json['saleAmount']?.toString() ?? '0',
        commissionAmount: json['commissionAmount']?.toString() ?? '0',
        status: json['status']?.toString() ?? '',
        operationId: json['operationId']?.toString() ?? '',
        createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? '') ?? DateTime.now(),
        settledAt: DateTime.tryParse(json['settledAt']?.toString() ?? ''),
        settlementReference: json['settlementReference']?.toString(),
        settlementNote: json['settlementNote']?.toString(),
      );
}

class FloatTopUpRequest {
  FloatTopUpRequest({required this.id, required this.amount, required this.currency, required this.status, required this.operationId, required this.reviewNote, required this.reviewedByUserId, required this.reviewedAt, required this.createdAt});

  final int id;
  final String amount;
  final String currency;
  final String status;
  final String operationId;
  final String? reviewNote;
  final int? reviewedByUserId;
  final DateTime? reviewedAt;
  final DateTime createdAt;

  factory FloatTopUpRequest.fromJson(Map<String, dynamic> json) => FloatTopUpRequest(
        id: (json['id'] as num?)?.toInt() ?? 0,
        amount: json['amount']?.toString() ?? '0',
        currency: json['currency']?.toString() ?? '',
        status: json['status']?.toString() ?? '',
        operationId: json['operationId']?.toString() ?? '',
        reviewNote: json['reviewNote']?.toString(),
        reviewedByUserId: (json['reviewedByUserId'] as num?)?.toInt(),
        reviewedAt: DateTime.tryParse(json['reviewedAt']?.toString() ?? ''),
        createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? '') ?? DateTime.now(),
      );
}

class PayoutLookup {
  PayoutLookup({required this.entitlementId, required this.ticketId, required this.claimantId, required this.prizeAmount, required this.currency, required this.payoutCode, required this.status});

  final int entitlementId;
  final int ticketId;
  final int claimantId;
  final String prizeAmount;
  final String currency;
  final String payoutCode;
  final String status;

  factory PayoutLookup.fromJson(Map<String, dynamic> json) => PayoutLookup(
        entitlementId: (json['entitlementId'] as num?)?.toInt() ?? 0,
        ticketId: (json['ticketId'] as num?)?.toInt() ?? 0,
        claimantId: (json['claimantId'] as num?)?.toInt() ?? 0,
        prizeAmount: json['prizeAmount']?.toString() ?? '0',
        currency: json['currency']?.toString() ?? '',
        payoutCode: json['payoutCode']?.toString() ?? '',
        status: json['status']?.toString() ?? '',
      );
}

class PayoutVerificationResult {
  PayoutVerificationResult({required this.recordId, required this.entitlementId, required this.agentId, required this.payoutCode, required this.status, required this.disbursedAmount, required this.clearingReference, required this.verifiedAt, required this.floatTransactionId});

  final int recordId;
  final int entitlementId;
  final int agentId;
  final String payoutCode;
  final String status;
  final String disbursedAmount;
  final String? clearingReference;
  final DateTime? verifiedAt;
  final int? floatTransactionId;

  factory PayoutVerificationResult.fromJson(Map<String, dynamic> json) => PayoutVerificationResult(
        recordId: (json['recordId'] as num?)?.toInt() ?? 0,
        entitlementId: (json['entitlementId'] as num?)?.toInt() ?? 0,
        agentId: (json['agentId'] as num?)?.toInt() ?? 0,
        payoutCode: json['payoutCode']?.toString() ?? '',
        status: json['status']?.toString() ?? '',
        disbursedAmount: json['disbursedAmount']?.toString() ?? '0',
        clearingReference: json['clearingReference']?.toString(),
        verifiedAt: DateTime.tryParse(json['verifiedAt']?.toString() ?? ''),
        floatTransactionId: (json['floatTransactionId'] as num?)?.toInt(),
      );
}