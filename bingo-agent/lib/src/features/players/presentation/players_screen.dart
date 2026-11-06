import 'package:bingo_agent/src/core/network/api_error.dart';
import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
import 'package:bingo_agent/src/features/auth/data/auth_models.dart';
import 'package:bingo_agent/src/core/validation/ethiopian_phone.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class PlayersScreen extends ConsumerStatefulWidget {
  const PlayersScreen({super.key});

  @override
  ConsumerState<PlayersScreen> createState() => _PlayersScreenState();
}

class _PlayersScreenState extends ConsumerState<PlayersScreen> {
  final _phoneController = TextEditingController();
  final _nameController = TextEditingController();
  bool _busy = false;
  String? _error;
  ApiError? _apiError;
  UserProfile? _player;
  String? _phoneInputError;
  String? _nameInputError;

  @override
  void dispose() {
    _phoneController.dispose();
    _nameController.dispose();
    super.dispose();
  }

  Future<void> _lookup() async {
    final l10n = AppLocalizations.of(context)!;
    if (_busy) return;
    if (!isValidEthiopianPhone(_phoneController.text)) {
      setState(() {
        _phoneInputError = l10n.errorInvalidPhone;
        _nameInputError = null;
        _error = null;
        _apiError = null;
      });
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
      _apiError = null;
      _phoneInputError = null;
      _nameInputError = null;
    });
    try {
      _player = await ref
          .read(agentRepositoryProvider)
          .lookupPlayer(_phoneController.text.trim());
    } catch (error) {
      _player = null;
      final parsed = normalizeApiError(error);
      _apiError = parsed;
      _error = parsed.cancelled ? null : parsed.userMessage(l10n);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _register() async {
    final l10n = AppLocalizations.of(context)!;
    if (_busy) return;
    final phoneIsValid = isValidEthiopianPhone(_phoneController.text);
    final nameIsValid = _nameController.text.trim().isNotEmpty;
    if (!phoneIsValid || !nameIsValid) {
      setState(() {
        _phoneInputError = phoneIsValid ? null : l10n.errorInvalidPhone;
        _nameInputError = nameIsValid ? null : l10n.requiredField;
        _error = null;
        _apiError = null;
      });
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
      _apiError = null;
      _phoneInputError = null;
      _nameInputError = null;
    });
    try {
      _player = await ref.read(agentRepositoryProvider).registerPlayer({
        'phoneNumber': _phoneController.text.trim(),
        'displayName': _nameController.text.trim(),
      });
      _error = null;
    } catch (error) {
      final parsed = normalizeApiError(error);
      _apiError = parsed;
      _error = parsed.cancelled ? null : parsed.userMessage(l10n);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final phoneError =
        _phoneInputError ?? fieldErrorMessage(_apiError, 'phoneNumber', l10n);
    final nameError =
        _nameInputError ?? fieldErrorMessage(_apiError, 'displayName', l10n);
    final hasInlineError = phoneError != null || nameError != null;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(
          l10n.playerLookupRegistration,
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _phoneController,
          enabled: !_busy,
          keyboardType: TextInputType.phone,
          textInputAction: TextInputAction.next,
          onSubmitted: (_) => FocusScope.of(context).nextFocus(),
          onChanged: (_) {
            if (_phoneInputError != null ||
                _apiError != null ||
                _error != null ||
                _player != null) {
              setState(() {
                _phoneInputError = null;
                _apiError = null;
                _error = null;
                _player = null;
              });
            }
          },
          decoration: InputDecoration(
            labelText: l10n.phoneLabel,
            errorText: phoneError,
          ),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _nameController,
          enabled: !_busy,
          textInputAction: TextInputAction.done,
          onSubmitted: (_) => _register(),
          onChanged: (_) {
            if (_nameInputError != null ||
                _apiError != null ||
                _error != null) {
              setState(() {
                _nameInputError = null;
                _apiError = null;
                _error = null;
              });
            }
          },
          decoration: InputDecoration(
            labelText: l10n.displayName,
            errorText: nameError,
          ),
        ),
        const SizedBox(height: 12),
        ListenableBuilder(
          listenable: Listenable.merge([_phoneController, _nameController]),
          builder: (context, _) {
            final phoneIsValid = isValidEthiopianPhone(_phoneController.text);
            final nameIsValid = _nameController.text.trim().isNotEmpty;
            final hint = !phoneIsValid
                ? l10n.errorInvalidPhone
                : !nameIsValid
                ? l10n.requiredField
                : null;
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Wrap(
                  spacing: 12,
                  children: [
                    FilledButton(
                      onPressed: _busy || !phoneIsValid ? null : _lookup,
                      child: Text(l10n.lookup),
                    ),
                    OutlinedButton(
                      onPressed: _busy || !phoneIsValid || !nameIsValid
                          ? null
                          : _register,
                      child: Text(l10n.register),
                    ),
                  ],
                ),
                if (hint != null &&
                    hint != _phoneInputError &&
                    hint != _nameInputError) ...[
                  const SizedBox(height: 8),
                  Text(hint, style: Theme.of(context).textTheme.bodySmall),
                ],
              ],
            );
          },
        ),
        if (_error != null && !hasInlineError) ...[
          const SizedBox(height: 8),
          Text(
            _error!,
            style: TextStyle(color: Theme.of(context).colorScheme.error),
          ),
        ],
        const SizedBox(height: 16),
        if (_player != null)
          Card(
            child: ListTile(
              title: Text(_player!.displayName),
              subtitle: Text(_player!.phoneNumber),
            ),
          ),
      ],
    );
  }
}
