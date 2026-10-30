import 'package:bingo_app/src/core/config/app_config.dart';
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
    ref.watch(sessionStorageProvider);

    return MaterialApp(
      debugShowCheckedModeBanner: false,
      onGenerateTitle: (context) => AppLocalizations.of(context)!.appTitle,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF1688FF),
          primary: const Color(0xFF1688FF),
          secondary: const Color(0xFFFF7900),
          onSurface: const Color(0xFF081D46),
        ),
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(0xFFFAFBFD),
          foregroundColor: Color(0xFF081D46),
          surfaceTintColor: Colors.transparent,
          centerTitle: false,
        ),
        filledButtonTheme: FilledButtonThemeData(
          style: FilledButton.styleFrom(
            backgroundColor: const Color(0xFFFF7900),
            foregroundColor: Colors.white,
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
            ),
          ),
        ),
        scaffoldBackgroundColor: const Color(0xFFFAFBFD),
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
