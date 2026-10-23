import 'package:bingo_agent/src/features/auth/data/auth_models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('agent login sends phone number and PIN only', () {
    final request = AgentLoginRequest(phoneNumber: '0911000001', pin: '123456');

    expect(request.toJson(), {'phoneNumber': '0911000001', 'pin': '123456'});
    expect(request.toJson().containsKey('password'), isFalse);
  });

  test('agent application does not request or transmit a password', () {
    const request = AgentApplicationRequest(
      displayName: 'QA Agent',
      phoneNumber: '0911000001',
      pin: '123456',
      city: 'Addis Ababa',
      territory: 'Bole',
      idDocumentType: 'NID',
      idDocumentNumber: 'QA-NID-001',
    );

    expect(request.toJson().containsKey('password'), isFalse);
    expect(request.toJson()['pin'], '123456');
  });
}
