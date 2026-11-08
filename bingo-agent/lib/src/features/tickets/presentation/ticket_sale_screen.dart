import 'package:bingo_agent/src/core/network/api_error.dart';
import 'package:bingo_agent/src/core/network/idempotency.dart';
import 'package:bingo_agent/src/core/validation/ethiopian_phone.dart';
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

  int? get _sessionId => int.tryParse(_sessionController.text.trim());
  bool get _validSession => (_sessionId ?? 0) > 0;
  bool get _validPhone => isValidEthiopianPhone(_phoneController.text);
  bool get _validPin =>
      RegExp(r'^\d{4,8}$').hasMatch(_pinController.text.trim());

  void _clearStaleState() {
    if (_apiError != null || _error != null || _ticketCode != null) {
      setState(() {
        _apiError = null;
        _error = null;
        _ticketCode = null;
      });
    }
  }

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
      final sessionId = _sessionId;
      if (sessionId == null || sessionId <= 0 || !_validPhone || !_validPin) {
        setState(() {
          _apiError = null;
          _error = sessionId == null || sessionId <= 0
              ? l10n.validSessionId
              : !_validPhone
              ? l10n.errorInvalidPhone
              : l10n.agentPinLength;
        });
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
    final sessionError =
        fieldErrorMessage(_apiError, 'sessionId', l10n) ??
        (_error == l10n.validSessionId ? _error : null);
    final phoneError =
        fieldErrorMessage(_apiError, 'playerPhoneNumber', l10n) ??
        (_error == l10n.errorInvalidPhone ? _error : null);
    final pinError =
        fieldErrorMessage(_apiError, 'pin', l10n) ??
        (_error == l10n.agentPinLength ? _error : null);
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
          enabled: !_busy && !_outcomeUnknown,
          textInputAction: TextInputAction.next,
          onSubmitted: (_) => FocusScope.of(context).nextFocus(),
          onChanged: (_) => _clearStaleState(),
          decoration: InputDecoration(
            labelText: l10n.sessionId,
            errorText: sessionError,
          ),
          keyboardType: TextInputType.number,
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
            _sessionController,
            _phoneController,
            _pinController,
          ]),
          builder: (context, _) {
            final canSubmit = _validSession && _validPhone && _validPin;
            final hint = !_validSession
                ? l10n.validSessionId
                : !_validPhone
                ? l10n.errorInvalidPhone
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
                      : Text(_outcomeUnknown ? l10n.retry : l10n.sellTicket),
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
