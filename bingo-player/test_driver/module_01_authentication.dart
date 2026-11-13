import 'dart:io';

import 'package:integration_test/integration_test_driver_extended.dart';

Future<void> main() async {
  await integrationDriver(
    onScreenshot:
        (String name, List<int> bytes, [Map<String, Object?>? args]) async {
          final safeName = name.replaceAll(RegExp(r'[^A-Za-z0-9_.-]'), '_');
          final directory = Directory('../docs/qa/evidence');
          await directory.create(recursive: true);
          await File(
            '${directory.path}/$safeName.png',
          ).writeAsBytes(bytes, flush: true);
          return true;
        },
  );
}
