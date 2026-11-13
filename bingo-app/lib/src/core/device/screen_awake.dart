import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:wakelock_plus/wakelock_plus.dart';

abstract interface class ScreenAwake {
  Future<void> setEnabled(bool enabled);
}

class WakelockScreenAwake implements ScreenAwake {
  const WakelockScreenAwake();

  @override
  Future<void> setEnabled(bool enabled) => WakelockPlus.toggle(enable: enabled);
}

final screenAwakeProvider = Provider<ScreenAwake>(
  (ref) => const WakelockScreenAwake(),
);

class ScreenAwakeController {
  ScreenAwakeController(this._screenAwake);

  final ScreenAwake _screenAwake;
  bool _enabled = false;

  Future<void> setActiveGame(bool active) async {
    if (_enabled == active) return;
    await _screenAwake.setEnabled(active);
    _enabled = active;
  }

  Future<void> dispose() => setActiveGame(false);
}
