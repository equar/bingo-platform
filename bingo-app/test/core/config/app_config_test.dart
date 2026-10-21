import 'package:bingo_app/src/core/config/app_config.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('requires an explicitly supplied API base URL', () {
    const config = AppConfig(apiBaseUrl: '', apiBaseUrlLabel: 'API_BASE_URL');

    expect(config.problem, ApiBaseUrlProblem.missing);
    expect(config.apiBaseUri, isNull);
    expect(config.liveGameWebSocketUri, isNull);
  });

  test('rejects relative, malformed, and unsupported API URLs', () {
    for (final value in [
      'localhost:8080',
      'https://',
      'ftp://api.example.com',
    ]) {
      final config = AppConfig(
        apiBaseUrl: value,
        apiBaseUrlLabel: 'API_BASE_URL',
      );
      expect(config.isValid, isFalse, reason: value);
      expect(config.apiBaseUri, isNull, reason: value);
    }
  });

  test('permits explicitly configured local HTTP endpoints in development', () {
    const config = AppConfig(
      apiBaseUrl: 'http://10.0.2.2:8080',
      apiBaseUrlLabel: 'API_BASE_URL',
    );

    expect(config.isValid, isTrue);
    expect(config.liveGameWebSocketUri, Uri.parse('ws://10.0.2.2:8080/ws'));
  });

  test('requires HTTPS and rejects loopback hosts in production', () {
    for (final value in [
      'http://api.example.com',
      'https://localhost:8080',
      'https://127.0.0.1:8080',
      'https://[::1]:8080',
      'https://localhost.:8080',
      'https://10.0.2.2:8080',
    ]) {
      final config = AppConfig(
        apiBaseUrl: value,
        apiBaseUrlLabel: 'API_BASE_URL',
        isProduction: true,
      );
      expect(config.isValid, isFalse, reason: value);
    }
  });

  test('derives a secure WebSocket URI for production HTTPS', () {
    const config = AppConfig(
      apiBaseUrl: 'https://api.example.com:8443/api',
      apiBaseUrlLabel: 'API_BASE_URL',
      isProduction: true,
    );

    expect(config.isValid, isTrue);
    expect(
      config.liveGameWebSocketUri,
      Uri.parse('wss://api.example.com:8443/ws'),
    );
  });
}
