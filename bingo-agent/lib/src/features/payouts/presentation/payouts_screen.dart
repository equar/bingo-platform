import 'package:bingo_agent/src/core/network/idempotency.dart';
import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
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

  @override
  void dispose() {
    _codeController.dispose();
    _pinController.dispose();
    super.dispose();
  }

  Future<void> _lookupCode() async {
    final payout = await ref.read(agentRepositoryProvider).lookupPayout(_codeController.text.trim());
    setState(() => _lookup = '${payout.prizeAmount} ${payout.currency}');
  }

  Future<void> _verify() async {
    await ref.read(agentRepositoryProvider).verifyPayout({
      'payoutCode': _codeController.text.trim(),
      'pin': _pinController.text.trim(),
      'clearingReference': 'COUNTER-${DateTime.now().millisecondsSinceEpoch}',
    }, IdempotencyKeyStore.newKey());
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('Approved AGENT_CASH payout', style: Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height: 12),
        TextField(controller: _codeController, decoration: const InputDecoration(labelText: 'Payout code')),
        const SizedBox(height: 12),
        TextField(controller: _pinController, decoration: const InputDecoration(labelText: 'PIN'), obscureText: true),
        const SizedBox(height: 12),
        Wrap(spacing: 12, children: [FilledButton(onPressed: _lookupCode, child: const Text('Lookup')), OutlinedButton(onPressed: _verify, child: const Text('Verify and disburse'))]),
        if (_lookup != null) ...[const SizedBox(height: 16), Text(_lookup!)],
      ],
    );
  }
}