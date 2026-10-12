class WalletBalance {
  const WalletBalance({required this.walletId, required this.currency, required this.availableBalance, required this.updatedAt});

  final int walletId;
  final String currency;
  final num availableBalance;
  final DateTime updatedAt;

  factory WalletBalance.fromJson(Map<String, dynamic> json) {
    return WalletBalance(
      walletId: (json['walletId'] as num?)?.toInt() ?? 0,
      currency: json['currency']?.toString() ?? '',
      availableBalance: json['availableBalance'] as num? ?? 0,
      updatedAt: DateTime.tryParse(json['updatedAt']?.toString() ?? '') ?? DateTime.fromMillisecondsSinceEpoch(0),
    );
  }
}

class WalletTransaction {
  const WalletTransaction({
    required this.id,
    required this.operationId,
    required this.transactionType,
    required this.amount,
    required this.balanceBefore,
    required this.balanceAfter,
    required this.currency,
    required this.createdAt,
    this.referenceType,
    this.referenceId,
    this.note,
  });

  final int id;
  final String operationId;
  final String transactionType;
  final num amount;
  final num balanceBefore;
  final num balanceAfter;
  final String currency;
  final String? referenceType;
  final int? referenceId;
  final String? note;
  final DateTime createdAt;

  factory WalletTransaction.fromJson(Map<String, dynamic> json) {
    return WalletTransaction(
      id: (json['id'] as num?)?.toInt() ?? 0,
      operationId: json['operationId']?.toString() ?? '',
      transactionType: json['transactionType']?.toString() ?? '',
      amount: json['amount'] as num? ?? 0,
      balanceBefore: json['balanceBefore'] as num? ?? 0,
      balanceAfter: json['balanceAfter'] as num? ?? 0,
      currency: json['currency']?.toString() ?? '',
      referenceType: json['referenceType']?.toString(),
      referenceId: (json['referenceId'] as num?)?.toInt(),
      note: json['note']?.toString(),
      createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? '') ?? DateTime.fromMillisecondsSinceEpoch(0),
    );
  }
}

class PaymentInstructions {
  const PaymentInstructions({required this.providerName, required this.businessAccount, required this.paymentInstructions, required this.currency});

  final String providerName;
  final String businessAccount;
  final String paymentInstructions;
  final String currency;

  factory PaymentInstructions.fromJson(Map<String, dynamic> json) {
    return PaymentInstructions(
      providerName: json['providerName']?.toString() ?? '',
      businessAccount: json['businessAccount']?.toString() ?? '',
      paymentInstructions: json['paymentInstructions']?.toString() ?? '',
      currency: json['currency']?.toString() ?? '',
    );
  }
}

class DepositClaim {
  const DepositClaim({
    required this.id,
    required this.operationId,
    required this.playerId,
    required this.submittedCashAmount,
    required this.currency,
    required this.conversionRateSnapshot,
    required this.calculatedCreditAmount,
    required this.normalizedTelebirrReference,
    required this.status,
    required this.submittedAt,
    this.reviewedBy,
    this.reviewedAt,
    this.reviewNote,
    this.paymentInstructions,
  });

  final int id;
  final String operationId;
  final int playerId;
  final num submittedCashAmount;
  final String currency;
  final num conversionRateSnapshot;
  final num calculatedCreditAmount;
  final String normalizedTelebirrReference;
  final String status;
  final DateTime submittedAt;
  final int? reviewedBy;
  final DateTime? reviewedAt;
  final String? reviewNote;
  final PaymentInstructions? paymentInstructions;

  factory DepositClaim.fromJson(Map<String, dynamic> json) {
    return DepositClaim(
      id: (json['id'] as num?)?.toInt() ?? 0,
      operationId: json['operationId']?.toString() ?? '',
      playerId: (json['playerId'] as num?)?.toInt() ?? 0,
      submittedCashAmount: json['submittedCashAmount'] as num? ?? 0,
      currency: json['currency']?.toString() ?? '',
      conversionRateSnapshot: json['conversionRateSnapshot'] as num? ?? 0,
      calculatedCreditAmount: json['calculatedCreditAmount'] as num? ?? 0,
      normalizedTelebirrReference: json['normalizedTelebirrReference']?.toString() ?? '',
      status: json['status']?.toString() ?? '',
      submittedAt: DateTime.tryParse(json['submittedAt']?.toString() ?? '') ?? DateTime.fromMillisecondsSinceEpoch(0),
      reviewedBy: (json['reviewedBy'] as num?)?.toInt(),
      reviewedAt: DateTime.tryParse(json['reviewedAt']?.toString() ?? ''),
      reviewNote: json['reviewNote']?.toString(),
      paymentInstructions: json['paymentInstructions'] is Map ? PaymentInstructions.fromJson(Map<String, dynamic>.from(json['paymentInstructions'] as Map)) : null,
    );
  }
}

class DepositRequest {
  const DepositRequest({required this.submittedCashAmount, required this.currency, required this.telebirrReference});

  final num submittedCashAmount;
  final String currency;
  final String telebirrReference;

  Map<String, dynamic> toJson() => {
        'submittedCashAmount': submittedCashAmount,
        'currency': currency,
        'telebirrReference': telebirrReference,
      };
}