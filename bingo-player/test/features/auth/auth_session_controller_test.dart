import 'package:bingo_player/src/core/storage/session_storage.dart';
import 'package:bingo_player/src/features/auth/data/auth_session_controller.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('shows a recoverable sign-in state when secure storage cannot be read', () async {
    final originalOnError = FlutterError.onError;
    addTearDown(() => FlutterError.onError = originalOnError);
    FlutterError.onError = (_) {};
    final controller = AuthSessionController(
      _FailingReadSessionStorage(),
      restoreOnInit: false,
    );

    await controller.restore();

    expect(controller.state.isLoading, isFalse);
    expect(controller.state.isAuthenticated, isFalse);
    expect(controller.state.errorMessage, 'SESSION_STORAGE_UNAVAILABLE');
    controller.dispose();
  });
}

class _FailingReadSessionStorage extends SessionStorage {
  @override
  Future<Map<String, String?>> readSession() {
    throw StateError('Secure storage is unavailable');
  }
}
