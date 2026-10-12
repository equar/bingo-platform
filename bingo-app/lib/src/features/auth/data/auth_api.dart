import 'package:bingo_app/src/features/auth/data/auth_models.dart';
import 'package:dio/dio.dart';

class AuthApi {
  AuthApi(this._dio);

  final Dio _dio;

  Future<AuthResponse> login(LoginRequest request) async {
    final response = await _dio.post<Map<String, dynamic>>('/api/v1/auth/login', data: request.toJson());
    return AuthResponse.fromJson(response.data ?? const <String, dynamic>{});
  }

  Future<AuthResponse> register(RegisterRequest request) async {
    final response = await _dio.post<Map<String, dynamic>>('/api/v1/auth/register', data: request.toJson());
    return AuthResponse.fromJson(response.data ?? const <String, dynamic>{});
  }

  Future<UserProfile> me() async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/me');
    final data = response.data ?? const <String, dynamic>{};
    final userJson = data['user'] is Map ? Map<String, dynamic>.from(data['user'] as Map) : data;
    return UserProfile.fromJson(userJson);
  }
}