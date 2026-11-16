import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

final appLocaleControllerProvider =
    StateNotifierProvider<AppLocaleController, Locale>(
      (ref) => AppLocaleController(),
    );

class AppLocaleController extends StateNotifier<Locale> {
  AppLocaleController({SharedPreferences? preferences})
    : _preferences = preferences,
      super(const Locale('en')) {
    _restoreFuture = _restore();
  }

  static const _preferenceKey = 'player.locale';
  final SharedPreferences? _preferences;
  late final Future<void> _restoreFuture;

  Future<void> get ready => _restoreFuture;

  Future<void> _restore() async {
    try {
      final preferences = _preferences ?? await SharedPreferences.getInstance();
      final savedLanguage = preferences.getString(_preferenceKey);
      if (savedLanguage != null &&
          (savedLanguage == 'en' || savedLanguage == 'am')) {
        state = Locale(savedLanguage);
      }
    } catch (error, stackTrace) {
      FlutterError.reportError(
        FlutterErrorDetails(
          exception: error,
          stack: stackTrace,
          library: 'player language preferences',
        ),
      );
    }
  }

  Future<void> setLocale(Locale locale) async {
    final languageCode = locale.languageCode;
    if (languageCode != 'en' && languageCode != 'am') {
      throw ArgumentError.value(locale, 'locale', 'Unsupported language');
    }

    await _restoreFuture;
    final preferences = _preferences ?? await SharedPreferences.getInstance();
    final saved = await preferences.setString(_preferenceKey, languageCode);
    if (!saved) {
      throw StateError('The language preference could not be saved.');
    }
    state = Locale(languageCode);
  }
}
