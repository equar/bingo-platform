import 'package:flutter_riverpod/flutter_riverpod.dart';

final appConfigProvider = Provider<AppConfig>((ref) => const AppConfig.fromEnvironment());

class AppConfig {
  const AppConfig({required this.apiBaseUrl, required this.apiBaseUrlLabel});

  const AppConfig.fromEnvironment()
      : apiBaseUrl = String.fromEnvironment('API_BASE_URL'),
        apiBaseUrlLabel = 'API_BASE_URL';

  final String apiBaseUrl;
  final String apiBaseUrlLabel;

  Uri? get apiBaseUri => apiBaseUrl.isEmpty ? null : Uri.tryParse(apiBaseUrl);
}