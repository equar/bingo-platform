import 'package:bingo_agent/src/features/auth/data/auth_session_controller.dart';
import 'package:bingo_agent/src/features/auth/presentation/auth_screen.dart';
import 'package:bingo_agent/src/features/home/presentation/home_shell.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class AuthGate extends ConsumerWidget {
  const AuthGate({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(authSessionControllerProvider);

    if (state.loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    if (state.isAuthenticated) {
      return const HomeShell();
    }

    return const AuthScreen();
  }
}