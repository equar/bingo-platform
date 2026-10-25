import 'package:bingo_app/src/features/lobby/domain/lobby_models.dart';
import 'package:dio/dio.dart';

class LobbyApi {
  LobbyApi(this._dio);

  final Dio _dio;

  Future<List<BingoRoom>> getRooms() async {
    final response = await _dio.get<List<dynamic>>('/api/v1/rooms');
    return (response.data ?? const [])
        .whereType<Map>()
        .map((item) => BingoRoom.fromJson(Map<String, dynamic>.from(item)))
        .toList();
  }

  Future<BingoRoom> getRoom(int roomId) async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/rooms/$roomId');
    return BingoRoom.fromJson(response.data ?? const <String, dynamic>{});
  }

  Future<List<GameSession>> getRoomSessions(int roomId) async {
    final response = await _dio.get<List<dynamic>>('/api/v1/rooms/$roomId/sessions');
    return (response.data ?? const [])
        .whereType<Map>()
        .map((item) => GameSession.fromJson(Map<String, dynamic>.from(item)))
        .toList();
  }

  Future<GameSession> getSession(int sessionId) async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/sessions/$sessionId');
    return GameSession.fromJson(response.data ?? const <String, dynamic>{});
  }
}
