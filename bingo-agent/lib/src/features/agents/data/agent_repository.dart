import 'package:bingo_agent/src/core/network/api_client.dart';
import 'package:bingo_agent/src/core/models/page_response.dart';
import 'package:bingo_agent/src/features/agents/data/agent_models.dart';
import 'package:bingo_agent/src/features/auth/data/auth_models.dart';
import 'package:bingo_agent/src/features/history/data/financial_history_entry.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final agentRepositoryProvider = Provider<AgentRepository>((ref) => AgentRepository(ref.watch(dioProvider)));

class AgentRepository {
  AgentRepository(this._dio);

  final Dio _dio;

  Future<AgentDashboard> dashboard() async =>
      AgentDashboard.fromJson((await _dio.get<Map<String, dynamic>>('/api/v1/agent/dashboard')).data ?? {});

  Future<AgentFloatBalance> floatBalance() async =>
      AgentFloatBalance.fromJson((await _dio.get<Map<String, dynamic>>('/api/v1/agent/float')).data ?? {});

  Future<List<AgentFloatTransaction>> floatHistory() async {
    final response = await _dio.get<List<dynamic>>('/api/v1/agent/float/history');
    return (response.data ?? const []).whereType<Map>().map((item) => AgentFloatTransaction.fromJson(Map<String, dynamic>.from(item))).toList();
  }

  Future<List<CommissionAccrual>> commissionHistory() async {
    final response = await _dio.get<List<dynamic>>('/api/v1/agent/commission-history');
    return (response.data ?? const []).whereType<Map>().map((item) => CommissionAccrual.fromJson(Map<String, dynamic>.from(item))).toList();
  }

  Future<List<FloatTopUpRequest>> topUpRequests() async {
    final response = await _dio.get<List<dynamic>>('/api/v1/agent/float-top-up-requests');
    return (response.data ?? const []).whereType<Map>().map((item) => FloatTopUpRequest.fromJson(Map<String, dynamic>.from(item))).toList();
  }

  Future<PageResponse<FinancialHistoryEntry>> financialHistory({
    int page = 0,
    int size = 20,
    String? type,
    String? status,
    String? from,
    String? to,
  }) async {
    final response = await _dio.get<Map<String, dynamic>>(
      '/api/v1/agent/financial-history',
      queryParameters: {
        'page': page,
        'size': size,
        if (type != null) 'type': type,
        if (status != null) 'status': status,
        if (from != null) 'from': from,
        if (to != null) 'to': to,
      },
    );
    return PageResponse.fromJson(
      response.data ?? const <String, dynamic>{},
      FinancialHistoryEntry.fromJson,
    );
  }

  Future<PayoutLookup> lookupPayout(String code) async =>
      PayoutLookup.fromJson((await _dio.get<Map<String, dynamic>>('/api/v1/agent/payouts/$code')).data ?? {});

  Future<PayoutVerificationResult> verifyPayout(Map<String, dynamic> body, String key) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/agent/payouts/verify',
      data: body,
      options: Options(headers: {'Idempotency-Key': key}),
    );
    return PayoutVerificationResult.fromJson(response.data ?? {});
  }

  Future<UserProfile> lookupPlayer(String phoneNumber) async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/agent/players/lookup', queryParameters: {'phoneNumber': phoneNumber});
    return UserProfile.fromJson(Map<String, dynamic>.from(response.data ?? {}));
  }

  Future<UserProfile> registerPlayer(Map<String, dynamic> body) async {
    final response = await _dio.post<Map<String, dynamic>>('/api/v1/agent/players', data: body);
    return UserProfile.fromJson(Map<String, dynamic>.from(response.data ?? {}));
  }

  Future<Map<String, dynamic>> sellTicket(Map<String, dynamic> body, String key) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/agent/tickets',
      data: body,
      options: Options(headers: {'Idempotency-Key': key}),
    );
    return response.data ?? {};
  }

  Future<Map<String, dynamic>> transferFloat(Map<String, dynamic> body, String key) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/agent/float/transfers',
      data: body,
      options: Options(headers: {'Idempotency-Key': key}),
    );
    return response.data ?? {};
  }

  Future<FloatTopUpRequest> submitTopUp(Map<String, dynamic> body, String key) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/agent/float-top-up-requests',
      data: body,
      options: Options(headers: {'Idempotency-Key': key}),
    );
    return FloatTopUpRequest.fromJson(response.data ?? {});
  }
}