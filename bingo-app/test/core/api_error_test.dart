import 'dart:ui' show Locale;

import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/core/network/api_error.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  late AppLocalizations localizations;

  setUpAll(() async {
    localizations = await AppLocalizations.delegate.load(const Locale('en'));
  });

  DioException responseError({
    required int status,
    required String path,
    required String method,
    Object? data,
  }) {
    final request = RequestOptions(path: path, method: method);
    return DioException(
      requestOptions: request,
      response: Response(
        requestOptions: request,
        statusCode: status,
        data: data,
      ),
    );
  }

  test('maps session, permission, and login statuses to safe messages', () {
    final loginError = mapDioApiError(
      responseError(
        status: 401,
        path: '/api/v1/player/login',
        method: 'POST',
        data: {'message': 'Account user@example.com exists'},
      ),
    );
    final sessionError = mapDioApiError(
      responseError(status: 401, path: '/api/v1/player/profile', method: 'GET'),
    );
    final permissionError = mapDioApiError(
      responseError(
        status: 403,
        path: '/api/v1/admin/users',
        method: 'GET',
        data: {'message': 'SQL permissions detail'},
      ),
    );

    expect(
      loginError.userMessage(localizations),
      localizations.errorInvalidCredentials,
    );
    expect(
      sessionError.userMessage(localizations),
      localizations.sessionExpired,
    );
    expect(
      permissionError.userMessage(localizations),
      localizations.errorPermissionDenied,
    );
    expect(permissionError.userMessage(localizations), isNot(contains('SQL')));
  });

  test('localizes safe login and unknown-code fallbacks in Amharic', () async {
    final amharic = await AppLocalizations.delegate.load(const Locale('am'));
    final loginError = mapDioApiError(
      responseError(
        status: 401,
        path: '/api/v1/player/login',
        method: 'POST',
        data: {'code': 'AUTH_INVALID_CREDENTIALS'},
      ),
    );
    final unknownError = mapDioApiError(
      responseError(
        status: 599,
        path: '/api/v1/player/profile',
        method: 'GET',
        data: {'code': 'UNRECOGNIZED_BACKEND_CODE'},
      ),
    );

    expect(loginError.userMessage(amharic), amharic.errorInvalidCredentials);
    expect(unknownError.userMessage(amharic), amharic.errorGeneric);
  });

  test('maps PHONE_REQUIRED to a safe inline phone-field message', () {
    final error = ApiError(code: 'PHONE_REQUIRED', statusCode: 400);

    expect(error.userMessage(localizations), localizations.errorInvalidPhone);
    expect(
      fieldErrorMessage(error, 'phoneNumber', localizations),
      localizations.errorInvalidPhone,
    );
    expect(fieldErrorMessage(error, 'amount', localizations), isNull);
  });

  test(
    'distinguishes malformed responses and connection failures from server text',
    () {
      final malformed = mapDioApiError(
        responseError(
          status: 500,
          path: '/api/v1/player/profile',
          method: 'GET',
          data: '<html>java.lang.Exception: internal host</html>',
        ),
      );
      final connection = mapDioApiError(
        DioException(
          requestOptions: RequestOptions(
            path: '/api/v1/player/profile',
            method: 'GET',
          ),
          type: DioExceptionType.connectionError,
          message: 'SocketException at internal-host',
        ),
      );

      expect(malformed.userMessage(localizations), localizations.errorGeneric);
      expect(
        malformed.userMessage(localizations),
        isNot(contains('Exception')),
      );
      expect(
        connection.userMessage(localizations),
        localizations.errorNoConnection,
      );
    },
  );

  test('uses safe validation field messages and hides backend detail text', () {
    final error = mapDioApiError(
      responseError(
        status: 400,
        path: '/api/v1/player/register',
        method: 'POST',
        data: {
          'code': 'VALIDATION_ERROR',
          'details': [
            {'field': 'phoneNumber', 'message': 'SQL constraint failed'},
          ],
        },
      ),
    );

    expect(
      fieldErrorMessage(error, 'phoneNumber', localizations),
      localizations.errorInvalidPhone,
    );
    expect(error.details.single.message, 'Check this field and try again.');
  });

  test(
    'marks financial transport failures uncertain and ignores cancellation',
    () {
      final failure = mapDioApiError(
        DioException(
          requestOptions: RequestOptions(
            path: '/api/v1/player/wallet/transfers',
            method: 'POST',
          ),
          type: DioExceptionType.connectionTimeout,
        ),
      );
      final cancelled = mapDioApiError(
        DioException(
          requestOptions: RequestOptions(
            path: '/api/v1/player/wallet/transfers',
            method: 'POST',
          ),
          type: DioExceptionType.cancel,
        ),
      );

      expect(failure.outcomeUnknown, isTrue);
      expect(
        failure.userMessage(localizations),
        localizations.errorTransactionUnknown,
      );
      expect(cancelled.cancelled, isTrue);
      expect(userFacingApiError(cancelled, localizations), isNull);
    },
  );
}
