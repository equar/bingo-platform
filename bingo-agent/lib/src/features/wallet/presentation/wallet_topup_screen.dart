import 'package:bingo_agent/src/core/network/api_error.dart';
import 'package:bingo_agent/src/core/network/idempotency.dart';
import 'package:bingo_agent/src/core/validation/ethiopian_phone.dart';
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

  bool get _validPhone => isValidEthiopianPhone(_phoneController.text);
  bool get _validAmount {
    final amount = double.tryParse(_amountController.text.trim());
    return amount != null && amount.isFinite && amount > 0;
  }

  bool get _validPin =>
      RegExp(r'^\d{4,8}$').hasMatch(_pinController.text.trim());

  void _clearStaleState() {
    if (_apiError != null || _error != null || _result != null) {
      setState(() {
        _apiError = null;
        _error = null;
        _result = null;
      });
    }
  }

  @override
  void dispose() {
    _phoneController.dispose();
    _amountController.dispose();
    _pinController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final l10n = AppLocalizations.of(context)!;
    final isNewAttempt = _attemptBody == null;
    if (_attemptBody == null) {
      final amount = double.tryParse(_amountController.text.trim());
      if (!_validPhone ||
          amount == null ||
          !amount.isFinite ||
          amount <= 0 ||
          !_validPin) {
        setState(() {
          _error = !_validPhone
              ? l10n.errorInvalidPhone
              : amount == null || !amount.isFinite || amount <= 0
              ? l10n.positiveAmountRequired
              : l10n.agentPinLength;
          _apiError = null;
        });
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
    if (isNewAttempt) {
      final amount = double.parse(_attemptBody!['amount'].toString());
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: Text(l10n.playerWalletTopUpTitle),
          content: Text(
            '${l10n.playerPhone}: ${_attemptBody!['playerPhoneNumber']}\n'
            '${l10n.topUpAmount}: ${amount.toStringAsFixed(2)} ETB',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(false),
              child: Text(l10n.cancel),
            ),
            FilledButton(
              onPressed: () => Navigator.of(dialogContext).pop(true),
              child: Text(l10n.confirm),
            ),
          ],
        ),
      );
      if (!mounted || confirmed != true) {
        _attemptBody = null;
        _idempotencyKey = null;
        return;
      }
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
        _pinController.clear();
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
    final phoneError =
        fieldErrorMessage(_apiError, 'playerPhoneNumber', l10n) ??
        (_error == l10n.errorInvalidPhone ? _error : null);
    final amountError =
        fieldErrorMessage(_apiError, 'amount', l10n) ??
        (_error == l10n.positiveAmountRequired ? _error : null);
    final pinError =
        fieldErrorMessage(_apiError, 'pin', l10n) ??
        (_error == l10n.agentPinLength ? _error : null);
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
          enabled: !_busy && !_outcomeUnknown,
          keyboardType: TextInputType.phone,
          textInputAction: TextInputAction.next,
          onSubmitted: (_) => FocusScope.of(context).nextFocus(),
          onChanged: (_) => _clearStaleState(),
          decoration: InputDecoration(
            labelText: l10n.playerPhone,
            errorText: phoneError,
          ),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _amountController,
          enabled: !_busy && !_outcomeUnknown,
          textInputAction: TextInputAction.next,
          onSubmitted: (_) => FocusScope.of(context).nextFocus(),
          onChanged: (_) => _clearStaleState(),
          decoration: InputDecoration(
            labelText: l10n.topUpAmount,
            errorText: amountError,
          ),
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _pinController,
          enabled: !_busy && !_outcomeUnknown,
          textInputAction: TextInputAction.done,
          onSubmitted: (_) => _submit(),
          onChanged: (_) => _clearStaleState(),
          decoration: InputDecoration(
            labelText: l10n.pinLabel,
            errorText: pinError,
          ),
          obscureText: true,
        ),
        const SizedBox(height: 12),
        ListenableBuilder(
          listenable: Listenable.merge([
            _phoneController,
            _amountController,
            _pinController,
          ]),
          builder: (context, _) {
            final canSubmit = _validPhone && _validAmount && _validPin;
            final hint = !_validPhone
                ? l10n.errorInvalidPhone
                : !_validAmount
                ? l10n.positiveAmountRequired
                : !_validPin
                ? l10n.agentPinLength
                : null;
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                FilledButton(
                  onPressed: _busy || !canSubmit ? null : _submit,
                  child: _busy
                      ? const CircularProgressIndicator.adaptive()
                      : Text(_outcomeUnknown ? l10n.retry : l10n.topUp),
                ),
                if (hint != null && hint != _error) ...[
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
        if (_result != null) ...[
          const SizedBox(height: 16),
          Text(l10n.serverBalance(_result!)),
        ],
      ],
    );
  }
}
