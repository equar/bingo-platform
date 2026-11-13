import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/features/auth/data/auth_session_controller.dart';
import 'package:bingo_player/src/features/auth/presentation/auth_screen.dart';
import 'package:bingo_player/src/features/home/presentation/home_shell.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class AuthGate extends ConsumerWidget {
  const AuthGate({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(authSessionControllerProvider);
    final localizations = AppLocalizations.of(context)!;

    if (state.isLoading) {
      return Scaffold(body: Center(child: Text(localizations.loading)));
    }

    if (state.isAuthenticated) {
      return const HomeShell();
    }

    return AuthScreen(
      sessionExpired: state.errorMessage == 'SESSION_EXPIRED',
      accountSuspended: state.errorMessage == 'ACCOUNT_SUSPENDED',
      sessionStorageUnavailable:
          state.errorMessage == 'SESSION_STORAGE_UNAVAILABLE',
    );
  }
}
