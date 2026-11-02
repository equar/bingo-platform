import 'package:bingo_agent/src/core/network/api_error.dart';
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
  String? _idempotencyKey;
  Map<String, dynamic>? _attemptBody;
  bool _outcomeUnknown = false;
  ApiError? _apiError;

  @override
  void dispose() {
    _sessionController.dispose();
    _phoneController.dispose();
    _pinController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final l10n = AppLocalizations.of(context)!;
    if (_attemptBody == null) {
      final sessionId = int.tryParse(_sessionController.text.trim());
      if (sessionId == null ||
          _phoneController.text.trim().isEmpty ||
          _pinController.text.trim().isEmpty) {
        setState(() => _error = l10n.requiredField);
        return;
      }
      _attemptBody = {
        'sessionId': sessionId,
        'playerPhoneNumber': _phoneController.text.trim(),
        'pin': _pinController.text.trim(),
      };
      _idempotencyKey = IdempotencyKeyStore.newKey();
    }
    if (_idempotencyKey == null || _busy) return;
    setState(() {
      _busy = true;
      _error = null;
      _ticketCode = null;
      _apiError = null;
    });
    try {
      final response = await ref
          .read(agentRepositoryProvider)
          .sellTicket(_attemptBody!, _idempotencyKey!);
      if (mounted) {
        setState(() {
          _ticketCode = response['ticket']?['publicTicketCode']?.toString();
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
    final sessionError = fieldErrorMessage(_apiError, 'sessionId', l10n);
    final phoneError = fieldErrorMessage(_apiError, 'playerPhoneNumber', l10n);
    final pinError = fieldErrorMessage(_apiError, 'pin', l10n);
    final hasInlineError =
        sessionError != null || phoneError != null || pinError != null;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(
          l10n.ticketSaleTitle,
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _sessionController,
          readOnly: _outcomeUnknown,
          decoration: InputDecoration(
            labelText: l10n.sessionId,
            errorText: sessionError,
          ),
          keyboardType: TextInputType.number,
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
              : Text(_outcomeUnknown ? l10n.retry : l10n.sellTicket),
        ),
        if (_error != null && !hasInlineError) ...[
          const SizedBox(height: 8),
          Text(
            _error!,
            style: TextStyle(color: Theme.of(context).colorScheme.error),
          ),
        ],
        if (_ticketCode != null) ...[
          const SizedBox(height: 16),
          Card(
            child: ListTile(
              title: Text(l10n.publicTicketCode),
              subtitle: Text(_ticketCode!),
            ),
          ),
        ],
      ],
    );
  }
}
