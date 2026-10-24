import 'package:bingo_app/src/features/auth/data/auth_models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('profile parsing tolerates omitted optional metadata safely', () {
    final profile = UserProfile.fromJson({
      'id': 178,
      'displayName': 'QA player',
      'phoneNumber': '251930300001',
    });

    expect(profile.id, 178);
    expect(profile.roles, isEmpty);
    expect(profile.status, isEmpty);
    expect(profile.createdAt.millisecondsSinceEpoch, 0);
    expect(profile.updatedAt.millisecondsSinceEpoch, 0);
    expect(profile.toPersistedJson(), isNot(contains('password')));
    expect(profile.toPersistedJson(), isNot(contains('token')));
  });
}
