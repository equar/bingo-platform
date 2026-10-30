import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/core/network/api_error.dart';
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
  final TextEditingController _pinController = TextEditingController();
  bool _registerMode = false;
  bool _busy = false;
  String? _errorMessage;
  bool _pinVisible = false;

  @override
  void dispose() {
    _displayNameController.dispose();
    _phoneController.dispose();
    _pinController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy) return;
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
      final sessionController = ref.read(
        authSessionControllerProvider.notifier,
      );
      final requestPhone = _phoneController.text.trim();
      final pin = _pinController.text;
      final authResponse = _registerMode
          ? await repository.register(
              RegisterRequest(
                displayName: _displayNameController.text.trim(),
                phoneNumber: requestPhone,
                password: pin,
              ),
            )
          : await repository.login(
              LoginRequest(phoneNumber: requestPhone, password: pin),
            );
      await sessionController.setSession(authResponse);
    } catch (error) {
      if (!mounted) return;
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
    final theme = Theme.of(context);
    const navy = Color(0xFF09234A);
    const blue = Color(0xFF1685FF);
    const orange = Color(0xFFFF790A);
    final border = OutlineInputBorder(
      borderRadius: BorderRadius.circular(16),
      borderSide: const BorderSide(color: Color(0xFFDCE8F8)),
    );
    return Theme(
      data: theme.copyWith(
        colorScheme: theme.colorScheme.copyWith(
          primary: blue,
          secondary: orange,
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: const Color(0xFFF4F8FF),
          border: border,
          enabledBorder: border,
          focusedBorder: border.copyWith(
            borderSide: const BorderSide(color: blue, width: 2),
          ),
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 18,
            vertical: 18,
          ),
        ),
      ),
      child: Scaffold(
        backgroundColor: const Color(0xFFFAFBFD),
        body: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(20, 12, 20, 28),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 480),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    ClipRRect(
                      borderRadius: BorderRadius.circular(28),
                      child: AspectRatio(
                        aspectRatio: 1.27,
                        child: Image.asset(
                          'assets/images/bingo-ethiopia-hero.png',
                          fit: BoxFit.contain,
                          alignment: Alignment.center,
                          excludeFromSemantics: true,
                        ),
                      ),
                    ),
                    const SizedBox(height: 22),
                    Text(
                      _registerMode
                          ? localizations.registerTitle
                          : localizations.loginTitle,
                      textAlign: TextAlign.center,
                      style: theme.textTheme.headlineMedium?.copyWith(
                        color: navy,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      _registerMode
                          ? localizations.registerSubtitle
                          : localizations.loginSubtitle,
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: const Color(0xFF59677D),
                      ),
                    ),
                    if (widget.sessionExpired) ...[
                      const SizedBox(height: 12),
                      Container(
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: const Color(0xFFFFF3E3),
                          borderRadius: BorderRadius.circular(16),
                        ),
                        child: Text(
                          localizations.sessionExpired,
                          style: const TextStyle(color: navy),
                        ),
                      ),
                    ],
                    const SizedBox(height: 16),
                    DecoratedBox(
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(26),
                        border: Border.all(color: const Color(0xFFE4ECF7)),
                        boxShadow: const [
                          BoxShadow(
                            color: Color(0x0809234A),
                            blurRadius: 24,
                            offset: Offset(0, 8),
                          ),
                        ],
                      ),
                      child: Padding(
                        padding: const EdgeInsets.all(20),
                        child: Form(
                          key: _formKey,
                          child: Column(
                            children: [
                              if (_registerMode) ...[
                                TextFormField(
                                  key: const ValueKey(
                                    'auth-display-name-field',
                                  ),
                                  controller: _displayNameController,
                                  textInputAction: TextInputAction.next,
                                  maxLength: 120,
                                  enabled: !_busy,
                                  autofillHints: const [AutofillHints.nickname],
                                  decoration: InputDecoration(
                                    labelText: localizations.displayNameLabel,
                                    prefixIcon: const Icon(
                                      Icons.person_outline_rounded,
                                    ),
                                  ),
                                  validator: (value) =>
                                      value == null || value.trim().isEmpty
                                      ? localizations.displayNameRequired
                                      : null,
                                ),
                                const SizedBox(height: 12),
                              ],
                              TextFormField(
                                key: const ValueKey('auth-phone-field'),
                                controller: _phoneController,
                                textInputAction: TextInputAction.next,
                                keyboardType: TextInputType.phone,
                                maxLength: 30,
                                enabled: !_busy,
                                autofillHints: const [
                                  AutofillHints.telephoneNumber,
                                ],
                                decoration: InputDecoration(
                                  labelText: localizations.phoneLabel,
                                  counterText: '',
                                  prefixIcon: const Icon(Icons.phone_outlined),
                                ),
                                validator: (value) {
                                  final text = value?.trim() ?? '';
                                  if (text.isEmpty) {
                                    return localizations.phoneRequired;
                                  }
                                  if (!_isValidEthiopianPhone(text)) {
                                    return localizations.invalidPhone;
                                  }
                                  return null;
                                },
                              ),
                              const SizedBox(height: 12),
                              TextFormField(
                                key: const ValueKey('auth-password-field'),
                                controller: _pinController,
                                obscureText: !_pinVisible,
                                enabled: !_busy,
                                enableSuggestions: false,
                                autocorrect: false,
                                textInputAction: TextInputAction.done,
                                onFieldSubmitted: (_) {
                                  if (!_busy) _submit();
                                },
                                keyboardType: TextInputType.number,
                                decoration: InputDecoration(
                                  labelText: localizations.passwordLabel,
                                  prefixIcon: const Icon(
                                    Icons.lock_outline_rounded,
                                  ),
                                  suffixIcon: IconButton(
                                    key: const ValueKey('auth-pin-visibility'),
                                    onPressed: _busy
                                        ? null
                                        : () => setState(
                                            () => _pinVisible = !_pinVisible,
                                          ),
                                    tooltip: _pinVisible
                                        ? localizations.hidePin
                                        : localizations.showPin,
                                    icon: Icon(
                                      _pinVisible
                                          ? Icons.visibility_off_outlined
                                          : Icons.visibility_outlined,
                                    ),
                                  ),
                                ),
                                validator: (value) {
                                  final text = value ?? '';
                                  if (text.isEmpty) {
                                    return localizations.passwordRequired;
                                  }
                                  if (!RegExp(r'^[0-9]{6}$').hasMatch(text)) {
                                    return localizations.passwordTooShort;
                                  }
                                  return null;
                                },
                              ),
                              if (_errorMessage != null) ...[
                                const SizedBox(height: 12),
                                Text(
                                  _errorMessage!,
                                  style: TextStyle(
                                    color: Theme.of(context).colorScheme.error,
                                  ),
                                ),
                              ],
                              const SizedBox(height: 16),
                              FilledButton(
                                key: const ValueKey('auth-submit-button'),
                                style: FilledButton.styleFrom(
                                  backgroundColor: orange,
                                  foregroundColor: Colors.white,
                                  minimumSize: const Size(double.infinity, 56),
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(17),
                                  ),
                                  textStyle: theme.textTheme.labelLarge
                                      ?.copyWith(
                                        fontSize: 18,
                                        fontWeight: FontWeight.w800,
                                      ),
                                ),
                                onPressed: _busy ? null : _submit,
                                child: _busy
                                    ? const SizedBox(
                                        width: 18,
                                        height: 18,
                                        child: CircularProgressIndicator(
                                          strokeWidth: 2,
                                        ),
                                      )
                                    : Text(
                                        _registerMode
                                            ? localizations.createAccount
                                            : localizations.signIn,
                                      ),
                              ),
                              const SizedBox(height: 8),
                              TextButton(
                                key: const ValueKey('auth-toggle-button'),
                                onPressed: _busy
                                    ? null
                                    : () => setState(() {
                                        _registerMode = !_registerMode;
                                        _errorMessage = null;
                                        _pinVisible = false;
                                      }),
                                child: Text(
                                  _registerMode
                                      ? localizations.switchToLogin
                                      : localizations.switchToRegister,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
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

bool _isValidEthiopianPhone(String value) {
  var compact = value.replaceAll(RegExp(r'[\s\-().]'), '');
  if (!RegExp(r'^\+?[0-9]+$').hasMatch(compact)) return false;
  if (compact.startsWith('+')) compact = compact.substring(1);

  String localNumber;
  if (compact.length == 12 && compact.startsWith('251')) {
    localNumber = compact.substring(3);
  } else if (compact.length == 10 && compact.startsWith('0')) {
    localNumber = compact.substring(1);
  } else if (compact.length == 9) {
    localNumber = compact;
  } else {
    return false;
  }
  return localNumber.startsWith('9') || localNumber.startsWith('7');
}
