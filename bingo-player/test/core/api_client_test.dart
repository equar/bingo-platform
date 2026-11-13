import 'package:bingo_player/src/core/network/api_client.dart';
import 'package:bingo_player/src/core/network/api_error.dart';
import 'package:bingo_player/l10n/app_localizations.dart';
import 'dart:ui' show Locale;
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'purchase API errors map stable codes and never expose server message',
    () async {
      final request = RequestOptions(
        method: 'POST',
        path: '/api/v1/sessions/9/tickets',
      );
      final error = DioException(
        requestOptions: request,
        response: Response<Map<String, dynamic>>(
          requestOptions: request,
          statusCode: 409,
          data: const {
            'code': 'INSUFFICIENT_BALANCE',
            'message': 'java.sql.SQLException: internal database host',
          },
        ),
      );

      final mapped = mapDioError(error);
      final localizations = await AppLocalizations.delegate.load(
        const Locale('en'),
      );

      expect(mapped, isA<ApiError>());
      expect(mapped.code, 'INSUFFICIENT_BALANCE');
      expect(mapped.message, 'Something went wrong. Please try again.');
      expect(
        userFacingApiError(mapped, localizations),
        'You don’t have enough balance to complete this transaction.',
      );
      expect(
        userFacingApiError(mapped, localizations),
        isNot(contains('SQLException')),
      );
      expect(mapped.statusCode, 409);
    },
  );
}
