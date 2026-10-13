import 'package:bingo_app/src/core/network/api_provider.dart';
import 'package:bingo_app/src/core/network/api_client.dart';
import 'package:bingo_app/src/features/auth/data/auth_api.dart';
import 'package:bingo_app/src/features/auth/data/auth_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository(ref.watch(authApiProvider));
});

class AuthRepository {
  AuthRepository(this._api);

  final AuthApi _api;

  Future<AuthResponse> login(LoginRequest request) => _api.login(request);

  Future<AuthResponse> register(RegisterRequest request) => _api.register(request);

  Future<UserProfile> me() => _api.me();
}