import 'dart:ui' show Locale;

import 'package:bingo_agent/src/core/network/api_error.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
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

  test(
    'maps login and permission errors without exposing backend messages',
    () {
      final loginError = mapDioApiError(
        responseError(
          status: 401,
          path: '/api/v1/agent/login',
          method: 'POST',
          data: {'message': 'Account user@example.com exists'},
        ),
      );
      final permissionError = mapDioApiError(
        responseError(
          status: 403,
          path: '/api/v1/agent/float',
          method: 'GET',
          data: {'message': 'java.sql.SQLException: internal host'},
        ),
      );

      expect(
        loginError.userMessage(localizations),
        localizations.errorInvalidCredentials,
      );
      expect(
        permissionError.userMessage(localizations),
        localizations.errorPermissionDenied,
      );
      expect(
        permissionError.userMessage(localizations),
        isNot(contains('SQLException')),
      );
    },
  );

  test('localizes safe fallbacks in English and Amharic', () async {
    final amharic = await AppLocalizations.delegate.load(const Locale('am'));
    final loginError = ApiError(
      code: 'AUTH_INVALID_CREDENTIALS',
      statusCode: 401,
      requestPath: '/api/v1/agent/login',
    );
    final unknownError = ApiError(code: 'UNRECOGNIZED_BACKEND_CODE');

    expect(
      loginError.userMessage(localizations),
      localizations.errorInvalidCredentials,
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

  test('maps insufficient Agent float to a ticket-sale recovery message', () {
    final error = ApiError(code: 'AGENT_FLOAT_OVERDRAFT', statusCode: 409);

    expect(
      error.userMessage(localizations),
      localizations.errorInsufficientAgentFloat,
    );
  });

  test(
    'maps malformed responses, safe validation details, and transport failures',
    () {
      final malformed = mapDioApiError(
        responseError(
          status: 500,
          path: '/api/v1/agent/profile',
          method: 'GET',
          data: '<html>stack trace and internal URL</html>',
        ),
      );
      final validation = mapDioApiError(
        responseError(
          status: 400,
          path: '/api/v1/agent/top-ups',
          method: 'POST',
          data: {
            'code': 'VALIDATION_ERROR',
            'details': [
              {'field': 'amount', 'message': 'SQL constraint failed'},
            ],
          },
        ),
      );
      final network = mapDioApiError(
        DioException(
          requestOptions: RequestOptions(
            path: '/api/v1/agent/profile',
            method: 'GET',
          ),
          type: DioExceptionType.connectionError,
        ),
      );

      expect(malformed.userMessage(localizations), localizations.errorGeneric);
      expect(
        malformed.userMessage(localizations),
        isNot(contains('stack trace')),
      );
      expect(
        fieldErrorMessage(validation, 'amount', localizations),
        localizations.errorInvalidAmount,
      );
      expect(
        validation.details.single.message,
        'Check this field and try again.',
      );
      expect(
        network.userMessage(localizations),
        localizations.errorNoConnection,
      );
    },
  );

  test(
    'marks financial timeout uncertain and suppresses normal cancellation',
    () {
      final failure = mapDioApiError(
        DioException(
          requestOptions: RequestOptions(
            path: '/api/v1/agent/wallet/top-up',
            method: 'POST',
          ),
          type: DioExceptionType.sendTimeout,
        ),
      );
      final cancelled = mapDioApiError(
        DioException(
          requestOptions: RequestOptions(
            path: '/api/v1/agent/wallet/top-up',
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
