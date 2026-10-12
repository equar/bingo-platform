import 'package:bingo_agent/src/core/models/page_response.dart';
import 'package:bingo_agent/src/core/network/api_client.dart';
import 'package:bingo_agent/src/features/support/data/support_models.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final supportRepositoryProvider = Provider<SupportRepository>((ref) => SupportRepository(ref.watch(dioProvider)));

class SupportRepository {
  SupportRepository(this._dio);

  final Dio _dio;

  Future<PageResponse<SupportTicketSummary>> myTickets() async {
    final response = await _dio.get<Map<String, dynamic>>('/api/v1/support/tickets');
    return PageResponse.fromJson(response.data ?? {}, SupportTicketSummary.fromJson);
  }

  Future<SupportTicketDetail> ticketDetail(int id) async =>
      SupportTicketDetail.fromJson((await _dio.get<Map<String, dynamic>>('/api/v1/support/tickets/$id')).data ?? {});

  Future<SupportTicketDetail> createTicket(SupportTicketCreateRequest request) async =>
      SupportTicketDetail.fromJson((await _dio.post<Map<String, dynamic>>('/api/v1/support/tickets', data: request.toJson())).data ?? {});

  Future<SupportTicketDetail> reply(int id, String body) async =>
      SupportTicketDetail.fromJson((await _dio.post<Map<String, dynamic>>('/api/v1/support/tickets/$id/messages', data: {'body': body})).data ?? {});
}