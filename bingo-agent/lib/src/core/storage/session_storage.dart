import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

final sessionStorageProvider = Provider<SessionStorage>((ref) => const SessionStorage());

class SessionStorage {
  const SessionStorage();

  static const FlutterSecureStorage _storage = FlutterSecureStorage();
  static const String _tokenKey = 'agent.auth.token';
  static const String _sessionJsonKey = 'agent.auth.session';

  Future<void> saveSession({required String accessToken, required String sessionJson}) async {
    await _storage.write(key: _tokenKey, value: accessToken);
    await _storage.write(key: _sessionJsonKey, value: sessionJson);
  }

  Future<Map<String, String?>> readSession() async {
    return {
      'token': await _storage.read(key: _tokenKey),
      'sessionJson': await _storage.read(key: _sessionJsonKey),
    };
  }

  Future<void> clear() async {
    await _storage.delete(key: _tokenKey);
    await _storage.delete(key: _sessionJsonKey);
  }

  Map<String, dynamic>? decodeSessionJson(String? jsonValue) {
    if (jsonValue == null || jsonValue.isEmpty) {
      return null;
    }
    final decoded = jsonDecode(jsonValue);
    return decoded is Map<String, dynamic> ? decoded : null;
  }
}