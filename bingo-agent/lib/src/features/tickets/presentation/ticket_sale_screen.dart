import 'package:bingo_agent/src/core/network/idempotency.dart';
import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class TicketSaleScreen extends ConsumerStatefulWidget {
  const TicketSaleScreen({super.key});

  @override
  ConsumerState<TicketSaleScreen> createState() => _TicketSaleScreenState();
}

class _TicketSaleScreenState extends ConsumerState<TicketSaleScreen> {
  final _sessionController = TextEditingController();
  final _phoneController = TextEditingController();
  final _pinController = TextEditingController();
  String? _ticketCode;

  @override
  void dispose() {
    _sessionController.dispose();
    _phoneController.dispose();
    _pinController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final response = await ref.read(agentRepositoryProvider).sellTicket({
      'sessionId': int.parse(_sessionController.text),
      'playerPhoneNumber': _phoneController.text.trim(),
      'pin': _pinController.text.trim(),
    }, IdempotencyKeyStore.newKey());
    setState(() => _ticketCode = response['ticket']?['publicTicketCode']?.toString());
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('Ticket sale with readable code', style: Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height: 12),
        TextField(controller: _sessionController, decoration: const InputDecoration(labelText: 'Session ID'), keyboardType: TextInputType.number),
        const SizedBox(height: 12),
        TextField(controller: _phoneController, decoration: const InputDecoration(labelText: 'Player phone')),
        const SizedBox(height: 12),
        TextField(controller: _pinController, decoration: const InputDecoration(labelText: 'PIN'), obscureText: true),
        const SizedBox(height: 12),
        FilledButton(onPressed: _submit, child: const Text('Sell ticket')),
        if (_ticketCode != null) ...[
          const SizedBox(height: 16),
          Card(child: ListTile(title: const Text('Public ticket code'), subtitle: Text(_ticketCode!))),
        ],
      ],
    );
  }
}