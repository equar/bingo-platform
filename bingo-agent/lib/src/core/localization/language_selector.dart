import 'package:bingo_agent/src/core/localization/app_locale_controller.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class LanguageSelector extends ConsumerWidget {
  const LanguageSelector({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final localizations = AppLocalizations.of(context)!;
    final locale = ref.watch(appLocaleControllerProvider);

    return Semantics(
      label: localizations.languageSelectorLabel,
      child: DropdownButton<Locale>(
        key: const ValueKey('agent-language-selector'),
        value: locale,
        isDense: true,
        underline: const SizedBox.shrink(),
        items: [
          DropdownMenuItem(
            value: const Locale('en'),
            child: Text(localizations.languageEnglish),
          ),
          DropdownMenuItem(
            value: const Locale('am'),
            child: Text(localizations.languageAmharic),
          ),
        ],
        onChanged: (nextLocale) async {
          if (nextLocale == null) return;
          try {
            await ref
                .read(appLocaleControllerProvider.notifier)
                .setLocale(nextLocale);
          } catch (error, stackTrace) {
            FlutterError.reportError(
              FlutterErrorDetails(
                exception: error,
                stack: stackTrace,
                library: 'agent language preferences',
              ),
            );
            if (context.mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(localizations.languagePreferenceSaveFailed),
                ),
              );
            }
          }
        },
      ),
    );
  }
}
