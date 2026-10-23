import 'package:bingo_agent/src/features/auth/data/auth_models.dart';
import 'package:bingo_agent/src/features/auth/data/auth_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class AuthSessionState {
  const AuthSessionState({required this.loading, this.session, this.errorCode});

  final bool loading;
  final AuthResponse? session;
  final String? errorCode;

  bool get isAuthenticated => session != null;
}

final authSessionControllerProvider =
    StateNotifierProvider<AuthSessionController, AuthSessionState>((ref) {
      return AuthSessionController(ref.watch(authRepositoryProvider));
    });

class AuthSessionController extends StateNotifier<AuthSessionState> {
  AuthSessionController(this._repository)
    : super(const AuthSessionState(loading: true)) {
    _restore();
  }

  final AuthRepository _repository;

  Future<void> _restore() async {
    final session = await _repository.restore();
    if (session != null && session.isExpired) {
      await _repository.clear();
      state = const AuthSessionState(
        loading: false,
        errorCode: 'SESSION_EXPIRED',
      );
      return;
    }
    state = AuthSessionState(loading: false, session: session);
  }

  Future<void> signIn(AgentLoginRequest request) async {
    final response = await _repository.login(request);
    await _repository.persist(response);
    state = AuthSessionState(loading: false, session: response);
  }

  Future<void> signOut() async {
    await _repository.clear();
    state = const AuthSessionState(loading: false, session: null);
  }
}
