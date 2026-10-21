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

  @override
  void dispose() {
    _phoneController.dispose();
    _amountController.dispose();
    _pinController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final l10n = AppLocalizations.of(context)!;
    final amount = double.tryParse(_amountController.text.trim());
    if (_phoneController.text.trim().isEmpty ||
        amount == null ||
        amount <= 0 ||
        _pinController.text.trim().isEmpty) {
      setState(() => _error = l10n.requiredField);
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final response = await ref.read(agentRepositoryProvider).transferFloat({
        'playerPhoneNumber': _phoneController.text.trim(),
        'amount': amount,
        'currency': 'ETB',
        'pin': _pinController.text.trim(),
        'note': l10n.playerWalletTopUp,
      }, IdempotencyKeyStore.newKey());
      if (mounted) {
        setState(() => _result = response['availableBalance']?.toString());
      }
    } catch (_) {
      if (mounted) setState(() => _error = l10n.requestFailed);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(l10n.playerWalletTopUpTitle,
            style: Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height: 12),
        TextField(
            controller: _phoneController,
            decoration: InputDecoration(labelText: l10n.playerPhone)),
        const SizedBox(height: 12),
        TextField(
            controller: _amountController,
            decoration: InputDecoration(labelText: l10n.topUpAmount),
            keyboardType: const TextInputType.numberWithOptions(decimal: true)),
        const SizedBox(height: 12),
        TextField(
            controller: _pinController,
            decoration: InputDecoration(labelText: l10n.pinLabel),
            obscureText: true),
        const SizedBox(height: 12),
        FilledButton(
            onPressed: _busy ? null : _submit,
            child: _busy
                ? const CircularProgressIndicator.adaptive()
                : Text(l10n.topUp)),
        if (_error != null) ...[
          const SizedBox(height: 8),
          Text(_error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error))
        ],
        if (_result != null) ...[
          const SizedBox(height: 16),
          Text(l10n.serverBalance(_result!))
        ],
      ],
    );
  }
}
