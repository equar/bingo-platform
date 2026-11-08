import 'package:bingo_agent/src/core/config/app_config.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('allows local HTTP backend URLs in debug', () {
    const config = AppConfig(
      apiBaseUrl: 'http://10.0.2.2:8080',
      apiBaseUrlLabel: 'API_BASE_URL',
    );

    expect(config.apiBaseUri, Uri.parse('http://10.0.2.2:8080'));
  });

  test('rejects missing, malformed, and unsupported backend URLs', () {
    for (final value in ['', 'localhost:8080', 'ftp://api.example.com']) {
      final config = AppConfig(
        apiBaseUrl: value,
        apiBaseUrlLabel: 'API_BASE_URL',
      );
      expect(config.apiBaseUri, isNull, reason: value);
    }
  });

  test('requires HTTPS and rejects loopback hosts outside debug', () {
    for (final value in [
      'http://api.example.com',
      'https://localhost:8080',
      'https://127.0.0.1:8080',
      'https://[::1]:8080',
      'https://10.0.2.2:8080',
    ]) {
      final config = AppConfig(
        apiBaseUrl: value,
        apiBaseUrlLabel: 'API_BASE_URL',
        isProduction: true,
      );
      expect(config.apiBaseUri, isNull, reason: value);
    }
  });
}
