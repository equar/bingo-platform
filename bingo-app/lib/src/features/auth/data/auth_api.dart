import 'package:bingo_app/src/features/auth/data/auth_models.dart';
import 'package:bingo_app/src/core/network/api_client.dart';
import 'package:bingo_app/src/core/network/api_error.dart';
import 'package:dio/dio.dart';

class AuthApi {
  AuthApi(this._dio);

  final Dio _dio;

  Future<AuthResponse> login(LoginRequest request) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>('/api/v1/auth/login', data: request.toJson());
      return AuthResponse.fromJson(response.data ?? const <String, dynamic>{});
    } on DioException catch (error) {
      throw mapDioError(error);
    }
  }

  Future<AuthResponse> register(RegisterRequest request) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>('/api/v1/auth/register', data: request.toJson());
      return AuthResponse.fromJson(response.data ?? const <String, dynamic>{});
    } on DioException catch (error) {
      throw mapDioError(error);
    }
  }

  Future<UserProfile> me() async {
    try {
      final response = await _dio.get<Map<String, dynamic>>('/api/v1/me');
      final data = response.data ?? const <String, dynamic>{};
      final userJson = data['user'] is Map ? Map<String, dynamic>.from(data['user'] as Map) : data;
      return UserProfile.fromJson(userJson);
    } on DioException catch (error) {
      throw mapDioError(error);
    }
  }
}