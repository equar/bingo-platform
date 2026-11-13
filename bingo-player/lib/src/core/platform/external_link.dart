import 'package:flutter/services.dart';

class ExternalLink {
  static const _channel = MethodChannel('bingo_player/external_links');

  static Future<void> open(String url) async {
    try {
      await _channel.invokeMethod<void>('open', url);
    } on PlatformException {
      // The caller keeps the URL visible so it remains available to copy.
    } on MissingPluginException {
      // Platform-specific link launching is unavailable on this platform.
    }
  }
}
