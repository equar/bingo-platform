import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final appConfigProvider = Provider<AppConfig>((ref) => AppConfig.fromEnvironment());

class AppConfig {
  const AppConfig({
    required this.apiBaseUrl,
    required this.apiBaseUrlLabel,
    this.isProduction = false,
  });

  AppConfig.fromEnvironment()
      : apiBaseUrl = const String.fromEnvironment('API_BASE_URL'),
        apiBaseUrlLabel = 'API_BASE_URL',
        isProduction = !kDebugMode;

  final String apiBaseUrl;
  final String apiBaseUrlLabel;
  final bool isProduction;

  Uri? get apiBaseUri {
    final value = apiBaseUrl.trim();
    if (value.isEmpty) return null;

    final uri = Uri.tryParse(value);
    if (uri == null || !uri.hasAuthority || uri.host.isEmpty) return null;
    if (uri.scheme != 'http' && uri.scheme != 'https') return null;
    if (isProduction && uri.scheme != 'https') return null;
    if (isProduction && _isLoopback(uri.host)) return null;
    return uri;
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
