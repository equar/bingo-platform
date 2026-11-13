import 'package:flutter/services.dart';

class PlayerNotifications {
  PlayerNotifications._();

  static const _channel = MethodChannel('bingo_player/notifications');

  static Future<void> playSound({required bool win}) async {
    try {
      await _channel.invokeMethod<void>('playSound', {'win': win});
    } on PlatformException {
      await _playSystemFallback(win);
    } on MissingPluginException {
      await _playSystemFallback(win);
    }
  }

  static Future<void> _playSystemFallback(bool win) async {
    try {
      await SystemSound.play(
        win ? SystemSoundType.alert : SystemSoundType.click,
      );
    } catch (_) {
      // Browser and desktop audio may be unavailable until a user gesture.
    }
  }

  static Future<void> stopSound() async {
    try {
      await _channel.invokeMethod<void>('stopSound');
    } on PlatformException {
      // Stopping is best-effort on platforms without a native tone player.
    } on MissingPluginException {
      // Unsupported platforms have no ongoing native tone to stop.
    }
  }

  static Future<bool> requestPermission() async {
    try {
      return await _channel.invokeMethod<bool>('requestPermission') ?? false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  static Future<void> show({
    required String title,
    required String body,
    required int id,
  }) async {
    try {
      await _channel.invokeMethod<void>('show', {
        'title': title,
        'body': body,
        'id': id,
      });
    } on PlatformException {
      // Notification permission may be denied or unavailable on this device.
    } on MissingPluginException {
      // Keep the live game usable on unsupported platforms.
    }
  }
}
