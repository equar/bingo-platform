import 'dart:convert';

import 'package:bingo_agent/src/core/network/api_client.dart';
import 'package:bingo_agent/src/core/network/api_error.dart';
import 'package:bingo_agent/src/core/storage/session_storage.dart';
import 'package:bingo_agent/src/features/auth/data/auth_models.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository(ref.watch(dioProvider), ref.watch(sessionStorageProvider));
});

class AuthRepository {
  AuthRepository(this._dio, this._storage);

  final Dio _dio;
  final SessionStorage _storage;

  Future<AuthResponse> login(AgentLoginRequest request) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>('/api/v1/agent/login', data: request.toJson());
      return AuthResponse.fromJson(response.data ?? const <String, dynamic>{});
    } on DioException catch (error) {
      throw _mapError(error);
    }
  }

  Future<void> registerApplication(AgentApplicationRequest request) async {
    try {
      await _dio.post<Map<String, dynamic>>('/api/v1/agent/register', data: request.toJson());
    } on DioException catch (error) {
      throw _mapError(error);
    }
  }

  Future<void> persist(AuthResponse response) async {
    await _storage.saveSession(accessToken: response.accessToken, sessionJson: jsonEncode(response.toJson()));
  }

  Future<AuthResponse?> restore() async {
    final session = await _storage.readSession();
    final jsonValue = session['sessionJson'];
    if (jsonValue == null || jsonValue.isEmpty) {
      return null;
    }
    return AuthResponse.fromJson(jsonDecode(jsonValue) as Map<String, dynamic>);
  }

  Future<void> clear() => _storage.clear();

  ApiError _mapError(DioException error) {
    final response = error.response;
    final data = response?.data;
    if (data is Map) {
      return ApiError(
        code: data['code']?.toString() ?? 'AUTH_FAILED',
        message: data['message']?.toString() ?? error.message ?? 'Request failed',
        statusCode: response?.statusCode,
      );
    }
    return ApiError(code: 'NETWORK_ERROR', message: error.message ?? 'Network error', statusCode: response?.statusCode);
  }
}
