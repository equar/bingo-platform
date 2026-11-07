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
  String? expiringToken;

  dio.interceptors.add(
    InterceptorsWrapper(
      onRequest: (options, handler) async {
        final sessionState = ref.read(authSessionControllerProvider);
        final session = sessionState.session;
        if (session != null && session.isExpired) {
          await ref
              .read(authSessionControllerProvider.notifier)
              .clearSession(reason: 'SESSION_EXPIRED');
        } else if (session?.accessToken != null) {
          options.headers['Authorization'] = 'Bearer ${session!.accessToken}';
        }
        handler.next(options);
      },
      onError: (error, handler) async {
        String? sentToken;
        for (final entry in error.requestOptions.headers.entries) {
          if (entry.key.toLowerCase() != 'authorization') continue;
          final authorization = entry.value?.toString();
          if (authorization != null && authorization.startsWith('Bearer ')) {
            sentToken = authorization.substring('Bearer '.length);
          }
          break;
        }
        final currentToken = ref
            .read(authSessionControllerProvider)
            .session
            ?.accessToken;
        final suspended = mapDioApiError(error).code == 'ACCOUNT_SUSPENDED';
        if ((error.response?.statusCode == 401 || suspended) &&
            sentToken != null &&
            sentToken == currentToken &&
            expiringToken != sentToken) {
          expiringToken = sentToken;
          try {
            await ref
                .read(authSessionControllerProvider.notifier)
                .clearSession(
                  reason: suspended ? 'ACCOUNT_SUSPENDED' : 'SESSION_EXPIRED',
                );
          } finally {
            if (expiringToken == sentToken) expiringToken = null;
          }
        }
        handler.next(error);
      },
    ),
  );

  return dio;
});

ApiError mapDioError(DioException error) => mapDioApiError(error);
