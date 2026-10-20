import 'package:bingo_app/src/core/models/page_result.dart';
import 'package:bingo_app/src/features/support/domain/support_models.dart';
import 'package:dio/dio.dart';

class SupportApi {
  SupportApi(this._dio);

  final Dio _dio;

  Future<PageResult<SupportTicketSummary>> getMyTickets(
      {required int page, required int size}) async {
    final response = await _dio.get<Map<String, dynamic>>(
      '/api/v1/support/tickets',
      queryParameters: {'page': page, 'size': size},
    );
    return PageResult.fromJson(response.data ?? const <String, dynamic>{},
        SupportTicketSummary.fromJson);
  }

  Future<SupportTicketSummary> createTicket(
      SupportTicketCreateRequest request) async {
    final response = await _dio.post<Map<String, dynamic>>(
        '/api/v1/support/tickets',
        data: request.toJson());
    return SupportTicketSummary.fromJson(
        response.data ?? const <String, dynamic>{});
  }
}
