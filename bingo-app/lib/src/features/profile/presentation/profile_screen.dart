import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/core/widgets/section_card.dart';
import 'package:bingo_app/src/features/auth/data/auth_session_controller.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final localizations = AppLocalizations.of(context)!;
    final session = ref.watch(authSessionControllerProvider).session;
    if (session == null) {
      return Scaffold(body: Center(child: Text(localizations.errorGeneric)));
    }

    return Scaffold(
      appBar: AppBar(title: Text(localizations.profileTitle)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          SectionCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(session.user.displayName, style: Theme.of(context).textTheme.headlineSmall),
                const SizedBox(height: 8),
                Text(session.user.phoneNumber),
                const SizedBox(height: 12),
                Text('${localizations.profileMemberSince}: ${session.user.createdAt.toLocal()}'),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 8,
                  children: session.user.roles.map((role) => Chip(label: Text(role))).toList(),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: () => ref.read(authSessionControllerProvider.notifier).clearSession(),
            child: Text(localizations.logout),
          ),
        ],
      ),
    );
  }
}
