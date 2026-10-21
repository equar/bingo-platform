import 'package:bingo_agent/src/core/network/idempotency.dart';
import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class PayoutsScreen extends ConsumerStatefulWidget {
  const PayoutsScreen({super.key});

  @override
  ConsumerState<PayoutsScreen> createState() => _PayoutsScreenState();
}

class _PayoutsScreenState extends ConsumerState<PayoutsScreen> {
  final _codeController = TextEditingController();
  final _pinController = TextEditingController();
  String? _lookup;
  String? _error;
  bool _busy = false;

  @override
  void dispose() {
    _codeController.dispose();
    _pinController.dispose();
    super.dispose();
  }

  Future<void> _lookupCode() async {
    final l10n = AppLocalizations.of(context)!;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final payout = await ref
          .read(agentRepositoryProvider)
          .lookupPayout(_codeController.text.trim());
      if (mounted) {
        setState(() => _lookup = '${payout.prizeAmount} ${payout.currency}');
      }
    } catch (_) {
      if (mounted) setState(() => _error = l10n.requestFailed);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _verify() async {
    final l10n = AppLocalizations.of(context)!;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(agentRepositoryProvider).verifyPayout({
        'payoutCode': _codeController.text.trim(),
        'pin': _pinController.text.trim(),
        'clearingReference': 'COUNTER-${DateTime.now().millisecondsSinceEpoch}',
      }, IdempotencyKeyStore.newKey());
      if (mounted) setState(() => _lookup = l10n.completed);
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
        Text(l10n.approvedAgentCashPayout,
            style: Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height: 12),
        TextField(
            controller: _codeController,
            decoration: InputDecoration(labelText: l10n.payoutCode)),
        const SizedBox(height: 12),
        TextField(
            controller: _pinController,
            decoration: InputDecoration(labelText: l10n.pinLabel),
            obscureText: true),
        const SizedBox(height: 12),
        Wrap(spacing: 12, children: [
          FilledButton(
              onPressed: _busy ? null : _lookupCode, child: Text(l10n.lookup)),
          OutlinedButton(
              onPressed: _busy ? null : _verify,
              child: Text(l10n.verifyAndDisburse)),
        ]),
        if (_error != null) ...[
          const SizedBox(height: 8),
          Text(_error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error))
        ],
        if (_lookup != null) ...[const SizedBox(height: 16), Text(_lookup!)],
      ],
    );
  }
}
