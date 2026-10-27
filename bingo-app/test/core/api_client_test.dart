import 'package:bingo_app/src/core/network/api_client.dart';
import 'package:bingo_app/src/core/network/api_error.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('purchase API errors preserve server validation code and message', () {
    final request = RequestOptions(path: '/api/v1/sessions/9/tickets');
    final error = DioException(
      requestOptions: request,
      response: Response<Map<String, dynamic>>(
        requestOptions: request,
        statusCode: 409,
        data: const {
          'code': 'INSUFFICIENT_BALANCE',
          'message': 'Wallet balance cannot go below zero',
        },
      ),
    );

    final mapped = mapDioError(error);

    expect(mapped, isA<ApiError>());
    expect(mapped.code, 'INSUFFICIENT_BALANCE');
    expect(mapped.message, 'Wallet balance cannot go below zero');
    expect(mapped.statusCode, 409);
  });
}
