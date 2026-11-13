import 'dart:convert';

import 'package:bingo_player/src/core/storage/session_storage.dart';
import 'package:bingo_player/src/features/auth/data/auth_models.dart';
import 'package:flutter/foundation.dart';
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
  AuthSessionController(this._storage, {bool restoreOnInit = true})
    : super(const AuthSessionState(isLoading: true)) {
    if (restoreOnInit) restore();
  }

  final SessionStorage _storage;

  Future<void> restore() async {
    Map<String, String?> stored;
    try {
      stored = await _storage.readSession();
    } catch (error, stackTrace) {
      _reportStorageError(error, stackTrace, 'reading the saved session');
      _setStorageUnavailable();
      return;
    }

    try {
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

      final user = UserProfile.fromJson(
        Map<String, dynamic>.from(jsonDecode(userJson) as Map),
      );
      final session = AuthSession(
        accessToken: token,
        expiresAt: DateTime.parse(expiresAt),
        user: user,
      );
      if (session.isExpired) {
        if (!await _clearStoredSession('clearing an expired session')) {
          return;
        }
        if (mounted) {
          state = const AuthSessionState(
            isLoading: false,
            errorMessage: 'SESSION_EXPIRED',
          );
        }
        return;
      }
      state = AuthSessionState(isLoading: false, session: session);
    } catch (error, stackTrace) {
      FlutterError.reportError(
        FlutterErrorDetails(
          exception: error,
          stack: stackTrace,
          library: 'bingo_player',
          context: ErrorDescription('restoring the saved session'),
        ),
      );
      if (!await _clearStoredSession('clearing an invalid saved session')) {
        return;
      }
      if (mounted) {
        state = const AuthSessionState(isLoading: false);
      }
    }
  }

  Future<void> setSession(AuthResponse response) async {
    final expiresAt = DateTime.now().toUtc().add(Duration(seconds: response.expiresInSeconds));
    final session = AuthSession(accessToken: response.accessToken, expiresAt: expiresAt, user: response.user);
    try {
      await _storage.saveSession(
        accessToken: session.accessToken,
        expiresAtIso: session.expiresAt.toIso8601String(),
        userJson: session.user.toPersistedJson(),
      );
    } catch (error, stackTrace) {
      _reportStorageError(error, stackTrace, 'saving the authenticated session');
      rethrow;
    }
    state = AuthSessionState(isLoading: false, session: session);
  }

  Future<void> clearSession({String? reason}) async {
    if (!await _clearStoredSession('clearing the authenticated session')) {
      return;
    }
    state = AuthSessionState(isLoading: false, errorMessage: reason);
  }

  Future<bool> _clearStoredSession(String operation) async {
    try {
      await _storage.clear();
      return true;
    } catch (error, stackTrace) {
      _reportStorageError(error, stackTrace, operation);
      _setStorageUnavailable();
      return false;
    }
  }

  void _reportStorageError(Object error, StackTrace stackTrace, String operation) {
    FlutterError.reportError(
      FlutterErrorDetails(
        exception: error,
        stack: stackTrace,
        library: 'bingo_player',
        context: ErrorDescription(operation),
      ),
    );
  }

  void _setStorageUnavailable() {
    if (mounted) {
      state = const AuthSessionState(
        isLoading: false,
        errorMessage: 'SESSION_STORAGE_UNAVAILABLE',
      );
    }
  }
}
