import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final sessionStorageProvider = Provider<SessionStorage>((ref) {
  return const SessionStorage();
});

class SessionStorage {
  const SessionStorage();

  static const _secureStorage = FlutterSecureStorage();
  static const _tokenKey = 'auth.token';
  static const _expiresAtKey = 'auth.expiresAt';
  static const _userJsonKey = 'auth.userJson';

  Future<void> saveSession({
    required String accessToken,
    required String expiresAtIso,
    required String userJson,
  }) async {
    await _secureStorage.write(key: _tokenKey, value: accessToken);
    await _secureStorage.write(key: _expiresAtKey, value: expiresAtIso);
    await _secureStorage.write(key: _userJsonKey, value: userJson);
  }

  Future<Map<String, String?>> readSession() async {
    return {
      'token': await _secureStorage.read(key: _tokenKey),
      'expiresAt': await _secureStorage.read(key: _expiresAtKey),
      'userJson': await _secureStorage.read(key: _userJsonKey),
    };
  }

  Future<void> clear() async {
    await _secureStorage.delete(key: _tokenKey);
    await _secureStorage.delete(key: _expiresAtKey);
    await _secureStorage.delete(key: _userJsonKey);
  }
}