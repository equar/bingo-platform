import 'package:bingo_app/src/core/config/app_config.dart';
import 'package:bingo_app/src/core/storage/session_storage.dart';
import 'package:bingo_app/src/features/auth/presentation/auth_gate.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:bingo_app/l10n/app_localizations.dart';

class BingoApp extends StatelessWidget {
  const BingoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const ProviderScope(child: _BingoAppView());
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
        colorSchemeSeed: const Color(0xFF0E6C4A),
        scaffoldBackgroundColor: const Color(0xFFF7F4EE),
      ),
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      home: appConfig.apiBaseUrl.isEmpty
          ? _MissingConfigView(apiBaseUrlLabel: appConfig.apiBaseUrlLabel)
          : const AuthGate(),
    );
  }
}

class _MissingConfigView extends StatelessWidget {
  const _MissingConfigView({required this.apiBaseUrlLabel});

  final String apiBaseUrlLabel;

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
                Text(localizations.missingApiBaseUrlTitle,
                    style: Theme.of(context).textTheme.headlineSmall),
                const SizedBox(height: 12),
                Text(localizations.missingApiBaseUrlBody(apiBaseUrlLabel)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}