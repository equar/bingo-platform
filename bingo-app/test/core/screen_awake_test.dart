import 'package:bingo_app/src/core/device/screen_awake.dart';
import 'package:flutter_test/flutter_test.dart';

class _FakeScreenAwake implements ScreenAwake {
  final calls = <bool>[];

  @override
  Future<void> setEnabled(bool enabled) async {
    calls.add(enabled);
  }
}

void main() {
  test('keeps the screen awake only while a game is active', () async {
    final screenAwake = _FakeScreenAwake();
    final controller = ScreenAwakeController(screenAwake);

    await controller.setActiveGame(false);
    await controller.setActiveGame(true);
    await controller.setActiveGame(true);
    await controller.setActiveGame(false);
    await controller.dispose();

    expect(screenAwake.calls, [true, false]);
  });
}
