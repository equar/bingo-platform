import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final appConfigProvider = Provider<AppConfig>((ref) {
  return AppConfig.fromEnvironment();
});

class AppConfig {
  const AppConfig({
    required String apiBaseUrl,
    required this.apiBaseUrlLabel,
    this.isProduction = false,
  }) : _apiBaseUrl = apiBaseUrl;

  AppConfig.fromEnvironment()
    : this(
        apiBaseUrl: const String.fromEnvironment('API_BASE_URL'),
        apiBaseUrlLabel: 'API_BASE_URL',
        isProduction: !kDebugMode,
      );

  final String _apiBaseUrl;
  final String apiBaseUrlLabel;
  final bool isProduction;

  String get apiBaseUrl => _apiBaseUrl.trim();

  ApiBaseUrlProblem? get problem {
    final baseUrl = apiBaseUrl;
    if (baseUrl.isEmpty) {
      return ApiBaseUrlProblem.missing;
    }

    final uri = Uri.tryParse(baseUrl);
    if (uri == null || !uri.hasAuthority || uri.host.isEmpty) {
      return ApiBaseUrlProblem.invalid;
    }

    if (uri.scheme != 'http' && uri.scheme != 'https') {
      return ApiBaseUrlProblem.unsupportedScheme;
    }

    if (isProduction && _isLoopback(uri.host)) {
      return ApiBaseUrlProblem.productionLoopback;
    }
    if (isProduction && uri.scheme != 'https') {
      return ApiBaseUrlProblem.productionRequiresHttps;
    }
    return null;
  }

  bool get isValid => problem == null;

  Uri? get apiBaseUri {
    if (!isValid) {
      return null;
    }
    return Uri.parse(apiBaseUrl);
  }

  Uri? get liveGameWebSocketUri {
    final baseUri = apiBaseUri;
    if (baseUri == null) {
      return null;
    }

    final websocketScheme = baseUri.scheme == 'https' ? 'wss' : 'ws';
    return baseUri.replace(
      scheme: websocketScheme,
      path: '/ws',
      queryParameters: null,
    );
  }

  static bool _isLoopback(String host) {
    final normalizedHost = host
        .toLowerCase()
        .replaceAll(RegExp(r'^\[|\]$'), '')
        .replaceFirst(RegExp(r'\.$'), '');
    return normalizedHost == 'localhost' ||
        normalizedHost.endsWith('.localhost') ||
        normalizedHost == '127.0.0.1' ||
        normalizedHost.startsWith('127.') ||
        normalizedHost == '::1' ||
        normalizedHost == '0.0.0.0' ||
        normalizedHost == '10.0.2.2';
  }
}

enum ApiBaseUrlProblem {
  missing,
  invalid,
  unsupportedScheme,
  productionLoopback,
  productionRequiresHttps,
}
