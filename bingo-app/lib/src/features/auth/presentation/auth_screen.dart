import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/core/network/api_error.dart';
import 'package:bingo_app/src/core/widgets/section_card.dart';
import 'package:bingo_app/src/features/auth/data/auth_models.dart';
import 'package:bingo_app/src/features/auth/data/auth_repository.dart';
import 'package:bingo_app/src/features/auth/data/auth_session_controller.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class AuthScreen extends ConsumerStatefulWidget {
  const AuthScreen({
    super.key,
    required this.sessionExpired,
    this.accountSuspended = false,
  });

  final bool sessionExpired;
  final bool accountSuspended;

  @override
  ConsumerState<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends ConsumerState<AuthScreen> {
  final GlobalKey<FormState> _formKey = GlobalKey<FormState>();
  final TextEditingController _displayNameController = TextEditingController();
  final TextEditingController _phoneController = TextEditingController();
  final TextEditingController _pinController = TextEditingController();
  bool _registerMode = false;
  bool _showPin = false;
  bool _busy = false;
  String? _errorMessage;
  ApiError? _apiError;
  bool _suspensionDialogOpen = false;

  @override
  void initState() {
    super.initState();
    if (widget.accountSuspended) _scheduleSuspensionDialog();
  }

  @override
  void didUpdateWidget(covariant AuthScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.accountSuspended && !oldWidget.accountSuspended) {
      _scheduleSuspensionDialog();
    }
  }

  void _scheduleSuspensionDialog() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _showSuspensionDialog();
    });
  }

  Future<void> _showSuspensionDialog() async {
    if (_suspensionDialogOpen) return;
    _suspensionDialogOpen = true;
    FocusScope.of(context).unfocus();
    try {
      await showDialog<void>(
        context: context,
        barrierDismissible: false,
        builder: (dialogContext) => AlertDialog(
          icon: const Icon(Icons.block_outlined),
          content: Text(
            AppLocalizations.of(dialogContext)!.errorAccountSuspended,
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(),
              child: Text(
                MaterialLocalizations.of(dialogContext).okButtonLabel,
              ),
            ),
          ],
        ),
      );
    } finally {
      _suspensionDialogOpen = false;
    }
  }

  @override
  void dispose() {
    _displayNameController.dispose();
    _phoneController.dispose();
    _pinController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final localizations = AppLocalizations.of(context)!;
    if (_busy) return;
    if (!_formKey.currentState!.validate()) {
      return;
    }

    setState(() {
      _busy = true;
      _errorMessage = null;
      _apiError = null;
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
      final parsed = normalizeApiError(error);
      if (!parsed.cancelled && parsed.code == 'ACCOUNT_SUSPENDED') {
        setState(() => _busy = false);
        await _showSuspensionDialog();
        return;
      }
      setState(() {
        _apiError = parsed;
        _errorMessage = parsed.cancelled
            ? null
            : parsed.userMessage(localizations);
      });
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final displayNameError = fieldErrorMessage(
      _apiError,
      'displayName',
      localizations,
    );
    final phoneError = fieldErrorMessage(
      _apiError,
      'phoneNumber',
      localizations,
    );
    final passwordError =
        fieldErrorMessage(_apiError, 'password', localizations) ??
        fieldErrorMessage(_apiError, 'pin', localizations);
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
            padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Container(
                    width: double.infinity,
                    decoration: BoxDecoration(
                      gradient: const LinearGradient(
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                        colors: [Color(0xFF0B2B5B), Color(0xFF081D46)],
                      ),
                      borderRadius: BorderRadius.circular(24),
                    ),
                    child: Column(
                      children: [
                        ClipRRect(
                          borderRadius: const BorderRadius.vertical(
                            top: Radius.circular(24),
                          ),
                          child: Image.asset(
                            'assets/images/bingo-ethiopia-hero.png',
                            width: double.infinity,
                            fit: BoxFit.fitWidth,
                            excludeFromSemantics: true,
                          ),
                        ),
                        const SizedBox(height: 16),
                        Text(
                          _registerMode
                              ? localizations.registerTitle
                              : localizations.loginTitle,
                          textAlign: TextAlign.center,
                          style: Theme.of(
                            context,
                          ).textTheme.titleLarge?.copyWith(color: Colors.white),
                        ),
                        const SizedBox(height: 4),
                        Padding(
                          padding: const EdgeInsets.fromLTRB(20, 0, 20, 18),
                          child: Text(
                            _registerMode
                                ? localizations.registerSubtitle
                                : localizations.loginSubtitle,
                            textAlign: TextAlign.center,
                            style: Theme.of(context).textTheme.bodyMedium
                                ?.copyWith(color: Colors.white70),
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (widget.sessionExpired) ...[
                    const SizedBox(height: 12),
                    SectionCard(child: Text(localizations.sessionExpired)),
                  ],
                  const SizedBox(height: 16),
                  SectionCard(
                    child: Form(
                      key: _formKey,
                      autovalidateMode: AutovalidateMode.onUserInteraction,
                      child: Column(
                        children: [
                          if (_registerMode) ...[
                            TextFormField(
                              key: const ValueKey('auth-display-name-field'),
                              controller: _displayNameController,
                              enabled: !_busy,
                              textInputAction: TextInputAction.next,
                              maxLength: 120,
                              onChanged: (_) => _clearStaleErrors(),
                              onFieldSubmitted: (_) =>
                                  FocusScope.of(context).nextFocus(),
                              decoration: InputDecoration(
                                prefixIcon: const Icon(
                                  Icons.person_outline_rounded,
                                ),
                                labelText: localizations.displayNameLabel,
                                errorText: displayNameError,
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
                            enabled: !_busy,
                            textInputAction: TextInputAction.next,
                            keyboardType: TextInputType.phone,
                            maxLength: 30,
                            onChanged: (_) => _clearStaleErrors(),
                            onFieldSubmitted: (_) =>
                                FocusScope.of(context).nextFocus(),
                            decoration: InputDecoration(
                              prefixIcon: const Icon(Icons.phone_outlined),
                              labelText: localizations.phoneLabel,
                              errorText: phoneError,
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
                            enabled: !_busy,
                            obscureText: !_showPin,
                            keyboardType: TextInputType.number,
                            textInputAction: TextInputAction.done,
                            onChanged: (_) => _clearStaleErrors(),
                            onFieldSubmitted: (_) => _submit(),
                            decoration: InputDecoration(
                              labelText: localizations.passwordLabel,
                              errorText: passwordError,
                              suffixIcon: IconButton(
                                key: const ValueKey('auth-pin-visibility'),
                                tooltip: _showPin
                                    ? localizations.hidePin
                                    : localizations.showPin,
                                onPressed: _busy
                                    ? null
                                    : () =>
                                          setState(() => _showPin = !_showPin),
                                icon: Icon(
                                  _showPin
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
                          if (_errorMessage != null &&
                              displayNameError == null &&
                              phoneError == null &&
                              passwordError == null) ...[
                            const SizedBox(height: 12),
                            Text(
                              _errorMessage!,
                              style: TextStyle(
                                color: Theme.of(context).colorScheme.error,
                              ),
                            ),
                          ],
                          const SizedBox(height: 16),
                          SizedBox(
                            width: double.infinity,
                            child: FilledButton(
                              key: const ValueKey('auth-submit-button'),
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
                          ),
                          const SizedBox(height: 8),
                          TextButton(
                            key: const ValueKey('auth-toggle-button'),
                            onPressed: _busy
                                ? null
                                : () => setState(() {
                                    _registerMode = !_registerMode;
                                    _errorMessage = null;
                                    _apiError = null;
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
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  void _clearStaleErrors() {
    if (_apiError != null || _errorMessage != null) {
      setState(() {
        _apiError = null;
        _errorMessage = null;
      });
    }
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
