import 'package:bingo_player/src/core/models/page_result.dart';
import 'package:bingo_player/src/features/player_content/domain/player_content_models.dart';
import 'package:dio/dio.dart';

class PlayerContentApi {
  PlayerContentApi(this._dio);
  final Dio _dio;

  Future<PlayerAppContent> getContent() async {
    final response = await _dio.get<Map<String, dynamic>>(
      '/api/v1/player-app/content',
    );
    return PlayerAppContent.fromJson(response.data ?? const {});
  }

  Future<List<PlayerAnnouncement>> getMessages() async {
    final response = await _dio.get<List<dynamic>>(
      '/api/v1/player-app/messages',
    );
    return (response.data ?? const [])
        .whereType<Map>()
        .map(
          (json) =>
              PlayerAnnouncement.fromJson(Map<String, dynamic>.from(json)),
        )
        .toList();
  }

  Future<void> markMessageRead(int id) =>
      _dio.post<void>('/api/v1/player-app/messages/$id/read').then((_) {});

  Future<PageResult<PlayerGameHistoryItem>> getGameHistory({
    required int page,
    required int size,
  }) async {
    final response = await _dio.get<Map<String, dynamic>>(
      '/api/v1/me/game-history',
      queryParameters: {'page': page, 'size': size},
    );
    return PageResult.fromJson(
      response.data ?? const {},
      PlayerGameHistoryItem.fromJson,
    );
  }
}
