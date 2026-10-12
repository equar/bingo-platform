import 'package:bingo_agent/src/core/network/idempotency.dart';
import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
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

  @override
  void dispose() {
    _phoneController.dispose();
    _amountController.dispose();
    _pinController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final response = await ref.read(agentRepositoryProvider).transferFloat({
      'playerPhoneNumber': _phoneController.text.trim(),
      'amount': num.parse(_amountController.text),
      'currency': 'ETB',
      'pin': _pinController.text.trim(),
      'note': 'Player wallet top-up',
    }, IdempotencyKeyStore.newKey());
    setState(() => _result = response['availableBalance']?.toString());
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('Player wallet top-up', style: Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height: 12),
        TextField(controller: _phoneController, decoration: const InputDecoration(labelText: 'Player phone')),
        const SizedBox(height: 12),
        TextField(controller: _amountController, decoration: const InputDecoration(labelText: 'Amount'), keyboardType: TextInputType.number),
        const SizedBox(height: 12),
        TextField(controller: _pinController, decoration: const InputDecoration(labelText: 'PIN'), obscureText: true),
        const SizedBox(height: 12),
        FilledButton(onPressed: _submit, child: const Text('Top up')),
        if (_result != null) ...[const SizedBox(height: 16), Text('Server balance: $_result')],
      ],
    );
  }
}