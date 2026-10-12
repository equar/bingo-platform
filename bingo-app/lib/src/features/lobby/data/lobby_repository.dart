import 'package:bingo_app/src/core/network/api_provider.dart';
import 'package:bingo_app/src/features/lobby/data/lobby_api.dart';
import 'package:bingo_app/src/features/lobby/domain/lobby_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final lobbyRepositoryProvider = Provider<LobbyRepository>((ref) {
  return LobbyRepository(ref.watch(lobbyApiProvider));
});

class LobbyRepository {
  LobbyRepository(this._api);

  final LobbyApi _api;

  Future<List<BingoRoom>> getRooms() => _api.getRooms();

  Future<BingoRoom> getRoom(int roomId) => _api.getRoom(roomId);

  Future<GameSession> getSession(int sessionId) => _api.getSession(sessionId);
}