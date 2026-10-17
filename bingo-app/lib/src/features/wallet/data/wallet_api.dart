import 'package:bingo_app/src/core/models/page_result.dart';
import 'package:bingo_app/src/features/wallet/domain/wallet_models.dart';
import 'package:dio/dio.dart';

class WalletApi {
  WalletApi(this._dio);

  final Dio _dio;

  Future<WalletBalance> getBalance() async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/wallet/balance');
    return WalletBalance.fromJson(response.data ?? const <String, dynamic>{});
  }

  Future<PageResult<WalletTransaction>> getTransactions({required int page, required int size}) async {
    final response = await _dio.get<Map<String, dynamic>>(
      '/api/v1/wallet/transactions',
      queryParameters: {'page': page, 'size': size},
    );
    return PageResult.fromJson(response.data ?? const <String, dynamic>{}, WalletTransaction.fromJson);
  }

  Future<PageResult<DepositClaim>> getDeposits({required int page, required int size}) async {
    final response = await _dio.get<Map<String, dynamic>>(
      '/api/v1/wallet/deposits',
      queryParameters: {'page': page, 'size': size},
    );
    return PageResult.fromJson(response.data ?? const <String, dynamic>{}, DepositClaim.fromJson);
  }

  Future<DepositClaim> submitDeposit(DepositRequest request, String idempotencyKey) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/wallet/deposits',
      data: request.toJson(),
      options: Options(headers: {'Idempotency-Key': idempotencyKey}),
    );
    return DepositClaim.fromJson(response.data ?? const <String, dynamic>{});
  }

  Future<WalletRecipientLookup> lookupRecipient(String phoneNumber) async {
    final response = await _dio.get<Map<String, dynamic>>(
      '/api/v1/wallet/transfers/recipient',
      queryParameters: {'phoneNumber': phoneNumber},
    );
    return WalletRecipientLookup.fromJson(response.data ?? const <String, dynamic>{});
  }

  Future<WalletTransferResponse> submitTransfer(WalletTransferRequest request, String idempotencyKey) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/wallet/transfers',
      data: request.toJson(),
      options: Options(headers: {'Idempotency-Key': idempotencyKey}),
    );
    return WalletTransferResponse.fromJson(response.data ?? const <String, dynamic>{});
  }
}