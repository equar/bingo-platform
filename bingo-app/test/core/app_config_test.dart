import 'package:bingo_app/src/core/config/app_config.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('ticket purchase stays disabled unless explicitly enabled', () {
    const safeDefault = AppConfig(
      apiBaseUrl: 'http://10.0.2.2:8082',
      apiBaseUrlLabel: 'API_BASE_URL',
    );
    const enabledForAnApprovedRuntime = AppConfig(
      apiBaseUrl: 'http://10.0.2.2:8082',
      apiBaseUrlLabel: 'API_BASE_URL',
      playerTicketPurchasesEnabled: true,
    );

    expect(safeDefault.playerTicketPurchasesEnabled, isFalse);
    expect(enabledForAnApprovedRuntime.playerTicketPurchasesEnabled, isTrue);
  });
}
