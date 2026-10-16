import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final appConfigProvider = Provider<AppConfig>((ref) {
  return AppConfig.fromEnvironment();
});

class AppConfig {
  const AppConfig({required this.apiBaseUrl, required this.apiBaseUrlLabel});

  AppConfig.fromEnvironment()
      : apiBaseUrl = _resolveApiBaseUrl(),
        apiBaseUrlLabel = 'API_BASE_URL';

  static String _resolveApiBaseUrl() {
    const configuredApiBaseUrl = String.fromEnvironment('API_BASE_URL');
    if (configuredApiBaseUrl.isNotEmpty) {
      return configuredApiBaseUrl;
    }

    if (kIsWeb) {
      return 'http://localhost:8080';
    }

    switch (defaultTargetPlatform) {
      case TargetPlatform.android:
        return 'http://10.0.2.2:8080';
      case TargetPlatform.iOS:
      case TargetPlatform.macOS:
      case TargetPlatform.windows:
      case TargetPlatform.linux:
        return 'http://localhost:8080';
      case TargetPlatform.fuchsia:
        return '';
    }
  }

  final String apiBaseUrl;
  final String apiBaseUrlLabel;

  Uri? get apiBaseUri {
    if (apiBaseUrl.isEmpty) {
      return null;
    }
    return Uri.tryParse(apiBaseUrl);
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
}