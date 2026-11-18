import 'package:flutter/services.dart';

class ReferralInvite {
  ReferralInvite._();

  static const _channel = MethodChannel('bingo_player/external_links');
  static const _siteUrl = 'https://bingo.bakaroo.com/download/';

  static String link(String referralCode) {
    return Uri.parse(
      _siteUrl,
    ).replace(queryParameters: {'ref': referralCode.toUpperCase()}).toString();
  }

  static Future<bool> share(String message, String subject) async {
    try {
      await _channel.invokeMethod<void>('share', {
        'text': message,
        'subject': subject,
      });
      return true;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  static Future<String?> pendingCode() async {
    try {
      final code = await _channel.invokeMethod<String>(
        'getInstallReferralCode',
      );
      if (code != null && RegExp(r'^[A-Za-z0-9]{6}$').hasMatch(code)) {
        return code.toUpperCase();
      }
    } on PlatformException {
      // Referral attribution is best-effort; manual code entry remains available.
    } on MissingPluginException {
      // Non-Android platforms do not provide Play Store install attribution.
    }
    return null;
  }
}
