import 'package:bingo_app/src/core/network/api_client.dart';
import 'package:bingo_app/src/features/community/domain/community_models.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final communityRepositoryProvider = Provider<CommunityRepository>(
  (ref) => CommunityRepository(ref.watch(dioProvider)),
);

class CommunityRepository {
  CommunityRepository(this._dio);
  final Dio _dio;
  Future<T> _guard<T>(Future<T> Function() action) async {
    try {
      return await action();
    } on DioException catch (e) {
      throw mapDioError(e);
    }
  }

  Future<List<PrivateGame>> privateGames() => _guard(() async {
    final r = await _dio.get<List<dynamic>>('/api/v1/private-games');
    return (r.data ?? [])
        .map((v) => PrivateGame.fromJson(Map<String, dynamic>.from(v as Map)))
        .toList();
  });
  Future<Map<String, dynamic>> create(
    int templateId,
    String name,
    DateTime start,
    String key,
  ) => _guard(() async {
    final r = await _dio.post<Map<String, dynamic>>(
      '/api/v1/private-games',
      data: {
        'templateRoomId': templateId,
        'name': name,
        'scheduledStartAt': start.toUtc().toIso8601String(),
      },
      options: Options(headers: {'Idempotency-Key': key}),
    );
    return r.data!;
  });
  Future<PrivateGame> join(String code, String key) => _guard(() async {
    final r = await _dio.post<Map<String, dynamic>>(
      '/api/v1/private-games/join',
      data: {'code': code},
      options: Options(headers: {'Idempotency-Key': key}),
    );
    return PrivateGame.fromJson(r.data!);
  });
  Future<Map<String, dynamic>> details(int room) => _guard(
    () async => (await _dio.get<Map<String, dynamic>>(
      '/api/v1/private-games/$room',
    )).data!,
  );
  Future<Map<String, dynamic>> manage(int room, String action) => _guard(
    () async => (await _dio.post<Map<String, dynamic>>(
      '/api/v1/private-games/$room/$action',
    )).data!,
  );
  Future<DailyProgress> daily() => _guard(
    () async => DailyProgress.fromJson(
      (await _dio.get<Map<String, dynamic>>('/api/v1/challenges/daily')).data!,
    ),
  );
  Future<void> claim(String code, String day, String key) => _guard(() async {
    await _dio.post<dynamic>(
      '/api/v1/challenges/$code/claim',
      data: {'businessDate': day},
      options: Options(headers: {'Idempotency-Key': key}),
    );
  });
  Future<Ranking> leaderboard(String period, int page) => _guard(
    () async => Ranking.fromJson(
      (await _dio.get<Map<String, dynamic>>(
        '/api/v1/leaderboard',
        queryParameters: {'period': period, 'page': page, 'size': 25},
      )).data!,
    ),
  );
  Future<void> saveProfile(String? alias, bool visible) => _guard(() async {
    await _dio.put<dynamic>(
      '/api/v1/leaderboard/profile',
      data: {'alias': alias, 'visible': visible},
    );
  });
}
