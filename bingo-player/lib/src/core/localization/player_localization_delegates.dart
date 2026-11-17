import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

const playerLocalizationDelegates = <LocalizationsDelegate<dynamic>>[
  AppLocalizations.delegate,
  _TigrinyaFallbackDelegate<MaterialLocalizations>(
    GlobalMaterialLocalizations.delegate,
  ),
  _TigrinyaFallbackDelegate<CupertinoLocalizations>(
    GlobalCupertinoLocalizations.delegate,
  ),
  GlobalWidgetsLocalizations.delegate,
];

class _TigrinyaFallbackDelegate<T> extends LocalizationsDelegate<T> {
  const _TigrinyaFallbackDelegate(this.delegate);

  final LocalizationsDelegate<T> delegate;

  @override
  bool isSupported(Locale locale) =>
      locale.languageCode == 'ti' || delegate.isSupported(locale);

  @override
  Future<T> load(Locale locale) =>
      delegate.load(locale.languageCode == 'ti' ? const Locale('en') : locale);

  @override
  bool shouldReload(_TigrinyaFallbackDelegate<T> old) => false;
}
