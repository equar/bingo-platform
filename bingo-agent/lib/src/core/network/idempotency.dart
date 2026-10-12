import 'package:uuid/uuid.dart';

class IdempotencyKeyStore {
  IdempotencyKeyStore._();

  static final _uuid = Uuid();
  static String newKey() => _uuid.v4();
}