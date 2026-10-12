import 'package:flutter_riverpod/flutter_riverpod.dart';

final appConfigProvider = Provider<AppConfig>((ref) {
  return const AppConfig.fromEnvironment();
});

class AppConfig {
  const AppConfig({required this.apiBaseUrl, required this.apiBaseUrlLabel});

  const AppConfig.fromEnvironment()
      : apiBaseUrl = String.fromEnvironment('API_BASE_URL'),
        apiBaseUrlLabel = 'API_BASE_URL';

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