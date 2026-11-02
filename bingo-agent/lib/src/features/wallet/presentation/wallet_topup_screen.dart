import 'package:bingo_agent/src/core/network/api_error.dart';
import 'package:bingo_agent/src/core/network/idempotency.dart';
import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class WalletTopUpScreen extends ConsumerStatefulWidget {
  const WalletTopUpScreen({super.key});

  @override
  ConsumerState<WalletTopUpScreen> createState() => _WalletTopUpScreenState();
}

class _WalletTopUpScreenState extends ConsumerState<WalletTopUpScreen> {
  final _phoneController = TextEditingController();
  final _amountController = TextEditingController();
  final _pinController = TextEditingController();
  String? _result;
  String? _error;
  bool _busy = false;
  String? _idempotencyKey;
  Map<String, dynamic>? _attemptBody;
  bool _outcomeUnknown = false;
  ApiError? _apiError;

  @override
  void dispose() {
    _phoneController.dispose();
    _amountController.dispose();
    _pinController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final l10n = AppLocalizations.of(context)!;
    if (_attemptBody == null) {
      final amount = double.tryParse(_amountController.text.trim());
      if (_phoneController.text.trim().isEmpty ||
          amount == null ||
          amount <= 0 ||
          _pinController.text.trim().isEmpty) {
        setState(() => _error = l10n.requiredField);
        return;
      }
      _attemptBody = {
        'playerPhoneNumber': _phoneController.text.trim(),
        'amount': amount,
        'currency': 'ETB',
        'pin': _pinController.text.trim(),
        'note': l10n.playerWalletTopUp,
      };
      _idempotencyKey = IdempotencyKeyStore.newKey();
    }
    if (_idempotencyKey == null || _busy) {
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
      _apiError = null;
    });
    try {
      final response = await ref
          .read(agentRepositoryProvider)
          .transferFloat(_attemptBody!, _idempotencyKey!);
      if (mounted) {
        setState(() {
          _result = response['availableBalance']?.toString();
          _attemptBody = null;
          _idempotencyKey = null;
          _outcomeUnknown = false;
        });
      }
    } catch (error) {
      if (mounted) {
        final parsed = normalizeApiError(error);
        setState(() {
          _apiError = parsed;
          _error = parsed.cancelled ? null : parsed.userMessage(l10n);
          _outcomeUnknown = parsed.outcomeUnknown;
          if (!parsed.outcomeUnknown) {
            _attemptBody = null;
            _idempotencyKey = null;
          }
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final phoneError = fieldErrorMessage(_apiError, 'playerPhoneNumber', l10n);
    final amountError = fieldErrorMessage(_apiError, 'amount', l10n);
    final pinError = fieldErrorMessage(_apiError, 'pin', l10n);
    final hasInlineError =
        phoneError != null || amountError != null || pinError != null;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(
          l10n.playerWalletTopUpTitle,
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _phoneController,
          readOnly: _outcomeUnknown,
          decoration: InputDecoration(
            labelText: l10n.playerPhone,
            errorText: phoneError,
          ),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _amountController,
          readOnly: _outcomeUnknown,
          decoration: InputDecoration(
            labelText: l10n.topUpAmount,
            errorText: amountError,
          ),
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _pinController,
          readOnly: _outcomeUnknown,
          decoration: InputDecoration(
            labelText: l10n.pinLabel,
            errorText: pinError,
          ),
          obscureText: true,
        ),
        const SizedBox(height: 12),
        FilledButton(
          onPressed: _busy ? null : _submit,
          child: _busy
              ? const CircularProgressIndicator.adaptive()
              : Text(_outcomeUnknown ? l10n.retry : l10n.topUp),
        ),
        if (_error != null && !hasInlineError) ...[
          const SizedBox(height: 8),
          Text(
            _error!,
            style: TextStyle(color: Theme.of(context).colorScheme.error),
          ),
        ],
        if (_result != null) ...[
          const SizedBox(height: 16),
          Text(l10n.serverBalance(_result!)),
        ],
      ],
    );
  }
}
