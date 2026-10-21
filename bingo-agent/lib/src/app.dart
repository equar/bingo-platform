import 'package:bingo_agent/src/core/config/app_config.dart';
import 'package:bingo_agent/src/core/network/network_status_controller.dart';
import 'package:bingo_agent/src/core/storage/session_storage.dart';
import 'package:bingo_agent/src/features/auth/presentation/auth_gate.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class BingoAgentApp extends StatelessWidget {
  const BingoAgentApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const ProviderScope(child: _BingoAgentView());
  }
}

class _BingoAgentView extends ConsumerWidget {
  const _BingoAgentView();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final config = ref.watch(appConfigProvider);
    ref.watch(sessionStorageProvider);
    ref.watch(networkStatusProvider);

    return MaterialApp(
      debugShowCheckedModeBanner: false,
      onGenerateTitle: (context) => AppLocalizations.of(context)!.appTitle,
      theme: ThemeData(
        useMaterial3: true,
        colorSchemeSeed: const Color(0xFF0E6C4A),
        scaffoldBackgroundColor: const Color(0xFFF7F4EE),
      ),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: config.apiBaseUrl.isEmpty
          ? _MissingConfigView(label: config.apiBaseUrlLabel)
          : const AuthGate(),
    );
  }
}

class _MissingConfigView extends StatelessWidget {
  const _MissingConfigView({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(l10n.missingApiBaseUrlTitle,
                  style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 8),
              Text(l10n.missingApiBaseUrlBody(label)),
            ],
          ),
        ),
      ),
    );
  }
}
