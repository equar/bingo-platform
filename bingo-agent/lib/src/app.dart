import 'package:bingo_agent/src/core/config/app_config.dart';
import 'package:bingo_agent/src/core/network/network_status_controller.dart';
import 'package:bingo_agent/src/core/storage/session_storage.dart';
import 'package:bingo_agent/src/features/auth/presentation/auth_gate.dart';
import 'package:bingo_agent/src/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
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
      onGenerateTitle: (context) => AppLocalizations.of(context).appTitle,
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
      home: config.apiBaseUrl.isEmpty ? const _MissingConfigView() : const AuthGate(),
    );
  }
}

class _MissingConfigView extends StatelessWidget {
  const _MissingConfigView();

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text('Missing API_BASE_URL. Pass it with --dart-define.'),
        ),
      ),
    );
  }
}