import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/core/network/api_error.dart';
import 'package:bingo_app/src/core/widgets/section_card.dart';
import 'package:bingo_app/src/features/auth/data/auth_models.dart';
import 'package:bingo_app/src/features/auth/data/auth_repository.dart';
import 'package:bingo_app/src/features/auth/data/auth_session_controller.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class AuthScreen extends ConsumerStatefulWidget {
  const AuthScreen({super.key, required this.sessionExpired});

  final bool sessionExpired;

  @override
  ConsumerState<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends ConsumerState<AuthScreen> {
  final GlobalKey<FormState> _formKey = GlobalKey<FormState>();
  final TextEditingController _displayNameController = TextEditingController();
  final TextEditingController _phoneController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  bool _registerMode = false;
  bool _busy = false;
  String? _errorMessage;

  @override
  void dispose() {
    _displayNameController.dispose();
    _phoneController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final localizations = AppLocalizations.of(context)!;
    if (!_formKey.currentState!.validate()) {
      return;
    }

    setState(() {
      _busy = true;
      _errorMessage = null;
    });

    try {
      final repository = ref.read(authRepositoryProvider);
      final sessionController = ref.read(authSessionControllerProvider.notifier);
      final requestPhone = _phoneController.text.trim();
      final password = _passwordController.text;
      final authResponse = _registerMode
          ? await repository.register(RegisterRequest(
              displayName: _displayNameController.text.trim(),
              phoneNumber: requestPhone,
              password: password,
            ))
          : await repository.login(LoginRequest(phoneNumber: requestPhone, password: password));
      await sessionController.setSession(authResponse);
    } catch (error) {
      if (error is ApiError) {
        setState(() => _errorMessage = '${error.code}: ${error.message}');
      } else {
        setState(() => _errorMessage = localizations.errorGeneric);
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(localizations.appTitle, style: Theme.of(context).textTheme.displaySmall),
                  const SizedBox(height: 8),
                  Text(_registerMode ? localizations.registerSubtitle : localizations.loginSubtitle),
                  if (widget.sessionExpired) ...[
                    const SizedBox(height: 12),
                    SectionCard(child: Text(localizations.sessionExpired)),
                  ],
                  const SizedBox(height: 16),
                  SectionCard(
                    child: Form(
                      key: _formKey,
                      child: Column(
                        children: [
                          if (_registerMode) ...[
                            TextFormField(
                              controller: _displayNameController,
                              textInputAction: TextInputAction.next,
                              decoration: InputDecoration(labelText: localizations.displayNameLabel),
                              validator: (value) => value == null || value.trim().isEmpty
                                  ? localizations.displayNameRequired
                                  : null,
                            ),
                            const SizedBox(height: 12),
                          ],
                          TextFormField(
                            controller: _phoneController,
                            textInputAction: TextInputAction.next,
                            keyboardType: TextInputType.phone,
                            decoration: InputDecoration(labelText: localizations.phoneLabel),
                            validator: (value) {
                              final text = value?.trim() ?? '';
                              if (text.isEmpty) return localizations.phoneRequired;
                              if (!RegExp(r'^[0-9+\s-]{6,}$').hasMatch(text)) return localizations.invalidPhone;
                              return null;
                            },
                          ),
                          const SizedBox(height: 12),
                          TextFormField(
                            controller: _passwordController,
                            obscureText: true,
                            decoration: InputDecoration(labelText: localizations.passwordLabel),
                            validator: (value) {
                              final text = value ?? '';
                              if (text.isEmpty) return localizations.passwordRequired;
                              if (text.length < 8) return localizations.passwordTooShort;
                              return null;
                            },
                          ),
                          if (_errorMessage != null) ...[
                            const SizedBox(height: 12),
                            Text(_errorMessage!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                          ],
                          const SizedBox(height: 16),
                          FilledButton(
                            onPressed: _busy ? null : _submit,
                            child: _busy
                                ? const SizedBox(
                                    width: 18,
                                    height: 18,
                                    child: CircularProgressIndicator(strokeWidth: 2),
                                  )
                                : Text(_registerMode ? localizations.createAccount : localizations.signIn),
                          ),
                          const SizedBox(height: 8),
                          TextButton(
                            onPressed: _busy
                                ? null
                                : () => setState(() {
                                      _registerMode = !_registerMode;
                                      _errorMessage = null;
                                    }),
                            child: Text(_registerMode ? localizations.switchToLogin : localizations.switchToRegister),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
