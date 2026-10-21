import 'package:bingo_agent/src/core/network/idempotency.dart';
import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
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
  String? _error;
  bool _busy = false;

  @override
  void dispose() {
    _sessionController.dispose();
    _phoneController.dispose();
    _pinController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final l10n = AppLocalizations.of(context)!;
    final sessionId = int.tryParse(_sessionController.text.trim());
    if (sessionId == null ||
        _phoneController.text.trim().isEmpty ||
        _pinController.text.trim().isEmpty) {
      setState(() => _error = l10n.requiredField);
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final response = await ref.read(agentRepositoryProvider).sellTicket({
        'sessionId': sessionId,
        'playerPhoneNumber': _phoneController.text.trim(),
        'pin': _pinController.text.trim(),
      }, IdempotencyKeyStore.newKey());
      if (mounted) {
        setState(() =>
            _ticketCode = response['ticket']?['publicTicketCode']?.toString());
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
        Text(l10n.ticketSaleTitle,
            style: Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height: 12),
        TextField(
            controller: _sessionController,
            decoration: InputDecoration(labelText: l10n.sessionId),
            keyboardType: TextInputType.number),
        const SizedBox(height: 12),
        TextField(
            controller: _phoneController,
            decoration: InputDecoration(labelText: l10n.playerPhone)),
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
                : Text(l10n.sellTicket)),
        if (_error != null) ...[
          const SizedBox(height: 8),
          Text(_error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error))
        ],
        if (_ticketCode != null) ...[
          const SizedBox(height: 16),
          Card(
              child: ListTile(
                  title: Text(l10n.publicTicketCode),
                  subtitle: Text(_ticketCode!))),
        ],
      ],
    );
  }
}
