import 'dart:convert';
import 'dart:io';

import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('English and Amharic ARB resources contain the same translation keys',
      () async {
    final english = jsonDecode(await File('lib/l10n/app_en.arb').readAsString())
        as Map<String, dynamic>;
    final amharic = jsonDecode(await File('lib/l10n/app_am.arb').readAsString())
        as Map<String, dynamic>;
    final englishKeys =
        english.keys.where((key) => !key.startsWith('@')).toSet();
    final amharicKeys =
        amharic.keys.where((key) => !key.startsWith('@')).toSet();

    expect(amharicKeys, equals(englishKeys));
    for (final key in englishKeys) {
      expect(english[key], isA<String>(),
          reason: 'English key "$key" must have a translation');
      expect(amharic[key], isA<String>(),
          reason: 'Amharic key "$key" must have a translation');
      expect((amharic[key] as String).trim(), isNotEmpty,
          reason: 'Amharic key "$key" must not be blank');
    }
  });

  testWidgets('long Amharic labels render without layout overflow',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(260, 220));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      MaterialApp(
        locale: const Locale('am'),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: Padding(
            padding: const EdgeInsets.all(16),
            child: Builder(
              builder: (context) {
                final l10n = AppLocalizations.of(context)!;
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(l10n.requestFloatTopUp),
                    Text(l10n.financialHistoryTitle)
                  ],
                );
              },
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}
