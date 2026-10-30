import 'package:bingo_agent/src/features/auth/data/auth_models.dart';
import 'package:bingo_agent/src/features/auth/data/auth_repository.dart';
import 'package:bingo_agent/src/features/auth/data/auth_session_controller.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class AuthScreen extends ConsumerStatefulWidget {
  const AuthScreen({super.key, this.sessionExpired = false});

  final bool sessionExpired;

  @override
  ConsumerState<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends ConsumerState<AuthScreen> {
  final _formKey = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _phone = TextEditingController();
  final _pin = TextEditingController();
  final _city = TextEditingController();
  final _territory = TextEditingController();
  final _documentType = TextEditingController();
  final _documentNumber = TextEditingController();
  bool _busy = false;
  bool _applicationMode = false;
  bool _applicationSubmitted = false;
  String? _error;

  @override
  void dispose() {
    _name.dispose();
    _phone.dispose();
    _pin.dispose();
    _city.dispose();
    _territory.dispose();
    _documentType.dispose();
    _documentNumber.dispose();
    super.dispose();
  }

  String? _required(String? value, AppLocalizations l10n) =>
      (value == null || value.trim().isEmpty) ? l10n.requiredField : null;

  Future<void> _submitLogin() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(authSessionControllerProvider.notifier).signIn(
        AgentLoginRequest(
          phoneNumber: _phone.text.trim(),
          pin: _pin.text.trim(),
        ),
      );
    } catch (_) {
      if (mounted) setState(() => _error = AppLocalizations.of(context)!.signInFailed);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _submitApplication() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(authRepositoryProvider).registerApplication(
        AgentApplicationRequest(
          displayName: _name.text.trim(),
          phoneNumber: _phone.text.trim(),
          pin: _pin.text.trim(),
          city: _city.text.trim(),
          territory: _territory.text.trim(),
          idDocumentType: _documentType.text.trim(),
          idDocumentNumber: _documentNumber.text.trim(),
        ),
      );
      if (mounted) setState(() => _applicationSubmitted = true);
    } catch (error) {
      if (mounted) {
        final message = error is Exception ? error.toString() : AppLocalizations.of(context)!.requestFailed;
        setState(() => _error = message);
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _showLogin() {
    setState(() {
      _applicationMode = false;
      _applicationSubmitted = false;
      _error = null;
      _pin.clear();
    });
  }

  Widget _field({
    required TextEditingController controller,
    required String label,
    required AppLocalizations l10n,
    bool obscure = false,
    TextInputType? keyboardType,
    String? Function(String?)? validator,
  }) => TextFormField(
    controller: controller,
    decoration: InputDecoration(labelText: label),
    obscureText: obscure,
    keyboardType: keyboardType,
    validator: validator ?? (value) => _required(value, l10n),
  );

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final isApplication = _applicationMode && !_applicationSubmitted;
    return Scaffold(
      key: ValueKey(
        isApplication ? 'agent-application-screen' : 'agent-login-screen',
      ),
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 480),
            child: Card(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(l10n.appTitle, style: Theme.of(context).textTheme.headlineMedium),
                    const SizedBox(height: 8),
                    Text(isApplication ? l10n.agentApplicationTitle : l10n.loginTitle),
                    if (widget.sessionExpired && !isApplication) ...[
                      const SizedBox(height: 12),
                      Text(l10n.sessionExpired),
                    ],
                    if (_applicationSubmitted) ...[
                      const SizedBox(height: 20),
                      Text(l10n.agentApplicationSubmitted, key: const ValueKey('agent-application-submitted')),
                      const SizedBox(height: 8),
                      Text(l10n.agentApplicationReviewNotice),
                      const SizedBox(height: 20),
                      OutlinedButton(onPressed: _showLogin, child: Text(l10n.agentBackToSignIn)),
                    ] else ...[
                      const SizedBox(height: 20),
                      Form(
                        key: _formKey,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            if (isApplication) ...[
                              _field(controller: _name, label: l10n.agentDisplayName, l10n: l10n),
                              const SizedBox(height: 12),
                            ],
                            _field(controller: _phone, label: l10n.phoneLabel, l10n: l10n),
                            if (isApplication) ...[
                              const SizedBox(height: 12),
                              _field(controller: _city, label: l10n.agentCity, l10n: l10n),
                              const SizedBox(height: 12),
                              _field(controller: _territory, label: l10n.agentTerritory, l10n: l10n),
                              const SizedBox(height: 12),
                              _field(controller: _documentType, label: l10n.agentIdDocumentType, l10n: l10n),
                              const SizedBox(height: 12),
                              _field(controller: _documentNumber, label: l10n.agentIdDocumentNumber, l10n: l10n),
                            ],
                            const SizedBox(height: 12),
                            _field(
                              controller: _pin,
                              label: l10n.pinLabel,
                              l10n: l10n,
                              obscure: true,
                              keyboardType: TextInputType.number,
                              validator: (value) => value == null || !RegExp(r'^\d{4,8}$').hasMatch(value.trim())
                                  ? l10n.agentPinLength
                                  : null,
                            ),
                          ],
                        ),
                      ),
                      if (_error != null) ...[
                        const SizedBox(height: 12),
                        Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                      ],
                      const SizedBox(height: 20),
                      FilledButton(
                        onPressed: _busy ? null : (isApplication ? _submitApplication : _submitLogin),
                        child: _busy
                            ? const CircularProgressIndicator.adaptive()
                            : Text(isApplication ? l10n.agentApplicationSubmit : l10n.signIn),
                      ),
                      TextButton(
                        onPressed: _busy
                            ? null
                            : () => setState(() {
                                  _applicationMode = !_applicationMode;
                                  _error = null;
                                  _formKey.currentState?.reset();
                                }),
                        child: Text(isApplication ? l10n.agentBackToSignIn : l10n.agentApplyLink),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
