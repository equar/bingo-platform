import 'package:bingo_app/src/core/network/api_error.dart';
import 'package:bingo_app/src/core/config/app_config.dart';
import 'package:bingo_app/src/features/auth/data/auth_session_controller.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final dioProvider = Provider<Dio>((ref) {
  final appConfig = ref.watch(appConfigProvider);
  final dio = Dio(
    BaseOptions(
      baseUrl: appConfig.apiBaseUrl,
      connectTimeout: const Duration(seconds: 20),
      receiveTimeout: const Duration(seconds: 20),
      headers: const {'Content-Type': 'application/json'},
    ),
  );

  dio.interceptors.add(
    InterceptorsWrapper(
      onRequest: (options, handler) async {
        final sessionState = ref.read(authSessionControllerProvider);
        final session = sessionState.session;
        if (session != null && session.isExpired) {
          await ref.read(authSessionControllerProvider.notifier).clearSession(reason: 'SESSION_EXPIRED');
        } else if (session?.accessToken != null) {
          options.headers['Authorization'] = 'Bearer ${session!.accessToken}';
        }
        handler.next(options);
      },
      onError: (error, handler) async {
        if (error.response?.statusCode == 401) {
          await ref.read(authSessionControllerProvider.notifier).clearSession(reason: 'SESSION_EXPIRED');
        }
        handler.next(error);
      },
    ),
  );

  return dio;
});

ApiError mapDioError(DioException error) {
  final response = error.response;
  final data = response?.data;
  if (data is Map<String, dynamic>) {
    final details = data['details'];
    return ApiError(
      code: data['code']?.toString() ?? 'HTTP_${response?.statusCode ?? 0}',
      message: data['message']?.toString() ?? error.message ?? 'Request failed',
      statusCode: response?.statusCode,
      details: details is List
          ? details
              .whereType<Map<String, dynamic>>()
              .map((item) => ApiValidationError(
                    field: item['field']?.toString() ?? '',
                    message: item['message']?.toString() ?? 'Invalid value',
                  ))
              .toList()
          : const [],
    );
  }
  return ApiError(
    code: 'HTTP_${response?.statusCode ?? 0}',
    message: error.message ?? 'Request failed',
    statusCode: response?.statusCode,
  );
}