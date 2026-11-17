import 'package:bingo_player/src/core/network/api_provider.dart';
import 'package:bingo_player/src/features/auth/data/auth_api.dart';
import 'package:bingo_player/src/features/auth/data/auth_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository(ref.watch(authApiProvider));
});

class AuthRepository {
  AuthRepository(this._api);

  final AuthApi _api;

  Future<AuthResponse> login(LoginRequest request) => _api.login(request);

  Future<AuthResponse> replacePlayerSession(LoginRequest request) =>
      _api.replacePlayerSession(request);

  Future<void> logout() => _api.logout();

  Future<AuthResponse> register(RegisterRequest request) =>
      _api.register(request);

  Future<UserProfile> me() => _api.me();

  Future<void> changePin(ChangePlayerPinRequest request) =>
      _api.changePin(request);

  Future<UserProfile> updatePreferences({
    String? preferredLanguage,
    String? preferredCalendar,
  }) => _api.updatePreferences(
    preferredLanguage: preferredLanguage,
    preferredCalendar: preferredCalendar,
  );
}
