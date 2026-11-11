import 'package:bingo_app/src/core/theme/app_theme.dart';
import 'package:bingo_app/src/core/widgets/responsive_content.dart';
import 'package:bingo_app/src/core/config/app_config.dart';
import 'package:bingo_app/src/core/localization/app_locale_controller.dart';
import 'package:bingo_app/src/core/storage/session_storage.dart';
import 'package:bingo_app/src/features/auth/presentation/auth_gate.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:bingo_app/l10n/app_localizations.dart';

class BingoApp extends StatelessWidget {
  const BingoApp({super.key, this.config});

  final AppConfig? config;

  @override
  Widget build(BuildContext context) {
    return ProviderScope(
      overrides: config == null
          ? const []
          : [appConfigProvider.overrideWithValue(config!)],
      child: const _BingoAppView(),
    );
  }
}

class _BingoAppView extends ConsumerWidget {
  const _BingoAppView();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final appConfig = ref.watch(appConfigProvider);
    final locale = ref.watch(appLocaleControllerProvider);
    ref.watch(sessionStorageProvider);

    return MaterialApp(
      debugShowCheckedModeBanner: false,
      locale: locale,
      onGenerateTitle: (context) => AppLocalizations.of(context)!.appTitle,
      theme: buildAppTheme(),
      darkTheme: buildAppTheme(brightness: Brightness.dark),
      themeMode: ThemeMode.system,
      builder: (context, child) => ColoredBox(
        color: Theme.of(context).scaffoldBackgroundColor,
        child: SafeArea(
          top: false,
          bottom: false,
          child: ResponsiveContent(maxWidth: 1200, child: child!),
        ),
      ),
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      home: !appConfig.isValid
          ? _MissingConfigView(problem: appConfig.problem!)
          : const AuthGate(),
    );
  }
}

class _MissingConfigView extends StatelessWidget {
  const _MissingConfigView({required this.problem});

  final ApiBaseUrlProblem problem;

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    return Scaffold(
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 520),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  problem == ApiBaseUrlProblem.missing
                      ? localizations.missingApiBaseUrlTitle
                      : localizations.invalidApiBaseUrlTitle,
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const SizedBox(height: 12),
                Text(
                  problem == ApiBaseUrlProblem.missing
                      ? localizations.missingApiBaseUrlBody
                      : localizations.invalidApiBaseUrlBody,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
