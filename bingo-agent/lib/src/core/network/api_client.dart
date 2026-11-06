import 'package:bingo_agent/src/core/config/app_config.dart';
import 'package:bingo_agent/src/core/network/api_error.dart';
import 'package:bingo_agent/src/core/storage/session_storage.dart';
import 'package:bingo_agent/src/features/auth/data/auth_session_controller.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final Provider<Dio> dioProvider = Provider<Dio>((ref) {
  final config = ref.watch(appConfigProvider);
  final storage = ref.watch(sessionStorageProvider);
  final dio = Dio(
    BaseOptions(
      baseUrl: config.apiBaseUri?.toString() ?? '',
      connectTimeout: const Duration(seconds: 15),
      receiveTimeout: const Duration(seconds: 20),
      sendTimeout: const Duration(seconds: 20),
      headers: const {'Accept': 'application/json'},
    ),
  );
  String? expiringToken;

  dio.interceptors.add(
    InterceptorsWrapper(
      onRequest: (options, handler) async {
        final session = await storage.readSession();
        final token = session['token'];
        if (token != null && token.isNotEmpty) {
          options.headers['Authorization'] = 'Bearer $token';
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
        if (error.response?.statusCode == 401 && sentToken != null) {
          final currentToken = (await storage.readSession())['token'];
          if (sentToken == currentToken && expiringToken != sentToken) {
            expiringToken = sentToken;
            try {
              await ref
                  .read(authSessionControllerProvider.notifier)
                  .clearSession(reason: 'SESSION_EXPIRED');
            } finally {
              if (expiringToken == sentToken) expiringToken = null;
            }
          }
        }
        handler.next(error);
      },
    ),
  );

  return dio;
});

ApiError mapDioError(DioException error) => mapDioApiError(error);
