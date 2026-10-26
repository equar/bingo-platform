import 'package:bingo_app/src/core/models/page_result.dart';
import 'package:bingo_app/src/core/network/api_client.dart' show mapDioError;
import 'package:bingo_app/src/features/game/domain/live_game_models.dart';
import 'package:bingo_app/src/features/game/domain/ticket_models.dart';
import 'package:bingo_app/src/features/lobby/domain/lobby_models.dart';
import 'package:dio/dio.dart';

class GameApi {
  GameApi(this._dio);

  final Dio _dio;

  Future<TicketPurchaseResponse> purchaseTicket(int sessionId, String idempotencyKey) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/v1/sessions/$sessionId/tickets',
        options: Options(headers: {'Idempotency-Key': idempotencyKey}),
      );
      return TicketPurchaseResponse.fromJson(response.data ?? const <String, dynamic>{});
    } on DioException catch (error) {
      throw mapDioError(error);
    }
  }

  Future<GameSession> getSession(int sessionId) async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/sessions/$sessionId');
    return GameSession.fromJson(response.data ?? const <String, dynamic>{});
  }

  Future<PageResult<TicketResponse>> getMyTickets(int sessionId, {required int page, required int size}) async {
    final response = await _dio.get<Map<String, dynamic>>(
      '/api/v1/sessions/$sessionId/my-tickets',
      queryParameters: {'page': page, 'size': size},
    );
    return PageResult.fromJson(response.data ?? const <String, dynamic>{}, TicketResponse.fromJson);
  }

  Future<List<LiveDrawEvent>> getDraws(int sessionId, {int? afterSequence}) async {
    final response = await _dio.get<List<dynamic>>(
      '/api/v1/sessions/$sessionId/draws',
      queryParameters: afterSequence == null ? null : {'afterSequence': afterSequence},
    );
    return (response.data ?? const [])
        .whereType<Map>()
        .map((item) => LiveDrawEvent.fromJson(Map<String, dynamic>.from(item)))
        .toList();
  }

  Future<PageResult<WinClaimRecord>> getMyWinClaims({required int page, required int size}) async {
    final response = await _dio.get<Map<String, dynamic>>(
      '/api/v1/me/win-claims',
      queryParameters: {'page': page, 'size': size},
    );
    return PageResult.fromJson(response.data ?? const <String, dynamic>{}, WinClaimRecord.fromJson);
  }

  Future<WinClaimRecord> submitWinClaim(int ticketId, String claimedPattern, String idempotencyKey) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/api/v1/tickets/$ticketId/win-claims',
      data: {'claimedPattern': claimedPattern},
      options: Options(headers: {'Idempotency-Key': idempotencyKey}),
    );
    return WinClaimRecord.fromJson(response.data ?? const <String, dynamic>{});
  }
}
