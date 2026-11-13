class WalletBalance {
  const WalletBalance({
    required this.walletId,
    required this.currency,
    required this.availableBalance,
    required this.promotionalBalance,
    required this.totalBalance,
    required this.updatedAt,
  });

  final int walletId;
  final String currency;
  final num availableBalance;
  final num promotionalBalance;
  final num totalBalance;
  final DateTime updatedAt;

  factory WalletBalance.fromJson(Map<String, dynamic> json) {
    return WalletBalance(
      walletId: (json['walletId'] as num?)?.toInt() ?? 0,
      currency: json['currency']?.toString() ?? '',
      availableBalance: json['availableBalance'] as num? ?? 0,
      promotionalBalance: json['promotionalBalance'] as num? ?? 0,
      totalBalance: json['totalBalance'] as num? ?? 0,
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
    this.transferableBalanceBefore,
    this.transferableBalanceAfter,
    this.promotionalBalanceBefore,
    this.promotionalBalanceAfter,
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
  final num? transferableBalanceBefore;
  final num? transferableBalanceAfter;
  final num? promotionalBalanceBefore;
  final num? promotionalBalanceAfter;
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
      transferableBalanceBefore: json['transferableBalanceBefore'] as num?,
      transferableBalanceAfter: json['transferableBalanceAfter'] as num?,
      promotionalBalanceBefore: json['promotionalBalanceBefore'] as num?,
      promotionalBalanceAfter: json['promotionalBalanceAfter'] as num?,
      currency: json['currency']?.toString() ?? '',
      referenceType: json['referenceType']?.toString(),
      referenceId: (json['referenceId'] as num?)?.toInt(),
      note: json['note']?.toString(),
      createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? '') ?? DateTime.fromMillisecondsSinceEpoch(0),
    );
  }
}

class WalletRecipientLookup {
  const WalletRecipientLookup({required this.userId, required this.displayName, required this.normalizedPhone});

  final int userId;
  final String displayName;
  final String normalizedPhone;

  factory WalletRecipientLookup.fromJson(Map<String, dynamic> json) {
    return WalletRecipientLookup(
      userId: (json['userId'] as num?)?.toInt() ?? 0,
      displayName: json['displayName']?.toString() ?? '',
      normalizedPhone: json['normalizedPhone']?.toString() ?? '',
    );
  }
}

class WalletTransferRequest {
  const WalletTransferRequest({required this.recipientPhoneNumber, required this.amount, required this.currency, this.note});

  final String recipientPhoneNumber;
  final num amount;
  final String currency;
  final String? note;

  Map<String, dynamic> toJson() => {
        'recipientPhoneNumber': recipientPhoneNumber,
        'amount': amount,
        'currency': currency,
        'note': note,
      };
}

class WalletTransferResponse {
  const WalletTransferResponse({
    required this.id,
    required this.operationId,
    required this.senderId,
    required this.senderPhoneNumber,
    required this.recipientId,
    required this.recipientPhoneNumber,
    required this.amount,
    required this.currency,
    required this.status,
    required this.senderBalanceBefore,
    required this.senderBalanceAfter,
    required this.recipientBalanceBefore,
    required this.recipientBalanceAfter,
    required this.createdAt,
    this.note,
    this.completedAt,
    this.failureReason,
  });

  final int id;
  final String operationId;
  final int senderId;
  final String senderPhoneNumber;
  final int recipientId;
  final String recipientPhoneNumber;
  final num amount;
  final String currency;
  final String status;
  final num senderBalanceBefore;
  final num senderBalanceAfter;
  final num recipientBalanceBefore;
  final num recipientBalanceAfter;
  final String? note;
  final DateTime createdAt;
  final DateTime? completedAt;
  final String? failureReason;

  factory WalletTransferResponse.fromJson(Map<String, dynamic> json) {
    return WalletTransferResponse(
      id: (json['id'] as num?)?.toInt() ?? 0,
      operationId: json['operationId']?.toString() ?? '',
      senderId: (json['senderId'] as num?)?.toInt() ?? 0,
      senderPhoneNumber: json['senderPhoneNumber']?.toString() ?? '',
      recipientId: (json['recipientId'] as num?)?.toInt() ?? 0,
      recipientPhoneNumber: json['recipientPhoneNumber']?.toString() ?? '',
      amount: json['amount'] as num? ?? 0,
      currency: json['currency']?.toString() ?? '',
      status: json['status']?.toString() ?? '',
      senderBalanceBefore: json['senderBalanceBefore'] as num? ?? 0,
      senderBalanceAfter: json['senderBalanceAfter'] as num? ?? 0,
      recipientBalanceBefore: json['recipientBalanceBefore'] as num? ?? 0,
      recipientBalanceAfter: json['recipientBalanceAfter'] as num? ?? 0,
      note: json['note']?.toString(),
      createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? '') ?? DateTime.fromMillisecondsSinceEpoch(0),
      completedAt: DateTime.tryParse(json['completedAt']?.toString() ?? ''),
      failureReason: json['failureReason']?.toString(),
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