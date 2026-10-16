import 'dart:convert';

import 'package:bingo_app/src/core/storage/session_storage.dart';
import 'package:bingo_app/src/features/auth/data/auth_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class AuthSessionState {
  const AuthSessionState({required this.isLoading, this.session, this.errorMessage});

  final bool isLoading;
  final AuthSession? session;
  final String? errorMessage;

  bool get isAuthenticated => session != null && !session!.isExpired;
}

final authSessionControllerProvider =
    StateNotifierProvider<AuthSessionController, AuthSessionState>((ref) {
  return AuthSessionController(ref.read(sessionStorageProvider));
});

class AuthSessionController extends StateNotifier<AuthSessionState> {
  AuthSessionController(this._storage) : super(const AuthSessionState(isLoading: true)) {
    restore();
  }

  final SessionStorage _storage;

  Future<void> restore() async {
    final stored = await _storage.readSession();
    if (!mounted) {
      return;
    }
    final token = stored['token'];
    final expiresAt = stored['expiresAt'];
    final userJson = stored['userJson'];
    if (token == null || expiresAt == null || userJson == null) {
      state = const AuthSessionState(isLoading: false);
      return;
    }

    final user = UserProfile.fromJson(Map<String, dynamic>.from(jsonDecode(userJson) as Map));
    state = AuthSessionState(
      isLoading: false,
      session: AuthSession(accessToken: token, expiresAt: DateTime.parse(expiresAt), user: user),
    );
  }

  Future<void> setSession(AuthResponse response) async {
    final expiresAt = DateTime.now().toUtc().add(Duration(seconds: response.expiresInSeconds));
    final session = AuthSession(accessToken: response.accessToken, expiresAt: expiresAt, user: response.user);
    await _storage.saveSession(
      accessToken: session.accessToken,
      expiresAtIso: session.expiresAt.toIso8601String(),
      userJson: session.user.toPersistedJson(),
    );
    state = AuthSessionState(isLoading: false, session: session);
  }

  Future<void> clearSession({String? reason}) async {
    await _storage.clear();
    state = AuthSessionState(isLoading: false, errorMessage: reason);
  }
}