import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/features/support/presentation/support_screen.dart';
import 'package:flutter/material.dart';

class SupportShortcut extends StatelessWidget {
  const SupportShortcut({super.key});

  @override
  Widget build(BuildContext context) => IconButton(
    tooltip: AppLocalizations.of(context)!.homeSupport,
    onPressed: () => Navigator.of(
      context,
    ).push<void>(MaterialPageRoute(builder: (_) => const SupportScreen())),
    icon: const Icon(Icons.support_agent_rounded),
  );
}
