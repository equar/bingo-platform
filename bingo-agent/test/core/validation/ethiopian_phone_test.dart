import 'package:bingo_agent/src/core/validation/ethiopian_phone.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('accepts Ethiopian mobile numbers in supported formats', () {
    for (final phone in [
      '0911000001',
      '911000001',
      '+251911000001',
      '251911000001',
      '07-110-000-01',
    ]) {
      expect(isValidEthiopianPhone(phone), isTrue, reason: phone);
    }
  });

  test('rejects malformed or non-mobile phone numbers', () {
    for (final phone in ['', '091100000', '0211000001', '0911000000x']) {
      expect(isValidEthiopianPhone(phone), isFalse, reason: phone);
    }
  });
}
