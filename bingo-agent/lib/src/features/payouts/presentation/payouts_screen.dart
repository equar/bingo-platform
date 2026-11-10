import 'package:bingo_agent/src/core/network/api_error.dart';
import 'package:bingo_agent/src/core/network/idempotency.dart';
import 'package:bingo_agent/src/core/widgets/responsive_form_content.dart';
import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
import 'package:bingo_agent/src/features/agents/data/agent_models.dart';
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
  PayoutLookup? _payout;
  String? _error;
  bool _busy = false;
  String? _idempotencyKey;
  Map<String, dynamic>? _attemptBody;
  bool _outcomeUnknown = false;
  bool _completed = false;
  ApiError? _apiError;
  bool get _validCode => _codeController.text.trim().isNotEmpty;
  bool get _validPin =>
      RegExp(r'^\d{4,8}$').hasMatch(_pinController.text.trim());

  void _clearCodeState() {
    if (_lookup != null || _apiError != null || _error != null || _completed) {
      setState(() {
        _lookup = null;
        _payout = null;
        _apiError = null;
        _error = null;
        _completed = false;
      });
    }
  }

  void _clearPinErrors() {
    if (_apiError != null || _error != null) {
      setState(() {
        _apiError = null;
        _error = null;
      });
    }
  }

  @override
  void dispose() {
    _codeController.dispose();
    _pinController.dispose();
    super.dispose();
  }

  Future<void> _lookupCode() async {
    if (_busy || _outcomeUnknown) return;
    final l10n = AppLocalizations.of(context)!;
    if (!_validCode) {
      setState(() => _error = l10n.requiredField);
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
      _apiError = null;
      _lookup = null;
      _payout = null;
    });
    try {
      final payout = await ref
          .read(agentRepositoryProvider)
          .lookupPayout(_codeController.text.trim());
      if (mounted) {
        setState(() {
          _payout = payout;
          _lookup = '${payout.prizeAmount} ${payout.currency}';
        });
      }
    } catch (error) {
      if (mounted) {
        final parsed = normalizeApiError(error);
        setState(() {
          _apiError = parsed;
          _error = parsed.cancelled ? null : parsed.userMessage(l10n);
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _verify() async {
    if (_busy || _completed) return;
    final l10n = AppLocalizations.of(context)!;
    final payout = _payout;
    if (payout == null ||
        payout.payoutCode != _codeController.text.trim()) {
      setState(() {
        _error = l10n.payoutLookupFirst;
        _apiError = null;
      });
      return;
    }
    if (payout.status.toUpperCase() != 'PENDING' ||
        payout.settlementPath != 'AGENT_CASH') {
      setState(() {
        _error = l10n.payoutUnavailable;
        _apiError = null;
      });
      return;
    }
    if (!_validCode || !_validPin) {
      setState(() {
        _error = !_validCode ? l10n.requiredField : l10n.agentPinLength;
        _apiError = null;
      });
      return;
    }
    if (_attemptBody == null) {
      _attemptBody = {
        'payoutCode': _codeController.text.trim(),
        'pin': _pinController.text.trim(),
        'clearingReference': 'COUNTER-${DateTime.now().millisecondsSinceEpoch}',
      };
      _idempotencyKey = IdempotencyKeyStore.newKey();
    }
    if (_idempotencyKey == null || _busy) return;
    setState(() {
      _busy = true;
      _error = null;
      _apiError = null;
    });
    try {
      await ref
          .read(agentRepositoryProvider)
          .verifyPayout(_attemptBody!, _idempotencyKey!);
      if (mounted) {
        setState(() {
          _lookup = l10n.completed;
          _attemptBody = null;
          _idempotencyKey = null;
          _outcomeUnknown = false;
          _completed = true;
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
    final codeError =
        fieldErrorMessage(_apiError, 'payoutCode', l10n) ??
        (_error == l10n.requiredField ? _error : null);
    final pinError =
        fieldErrorMessage(_apiError, 'pin', l10n) ??
        (_error == l10n.agentPinLength ? _error : null);
    final hasInlineError = codeError != null || pinError != null;
    return ResponsiveFormContent(
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(
            l10n.approvedAgentCashPayout,
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _codeController,
            enabled: !_busy && !_outcomeUnknown,
            textInputAction: TextInputAction.next,
            onSubmitted: (_) => FocusScope.of(context).nextFocus(),
            onChanged: (_) => _clearCodeState(),
            decoration: InputDecoration(
              labelText: l10n.payoutCode,
              errorText: codeError,
            ),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _pinController,
            enabled: !_busy && !_outcomeUnknown,
            textInputAction: TextInputAction.done,
            onSubmitted: (_) => _confirmVerify(),
            onChanged: (_) => _clearPinErrors(),
            decoration: InputDecoration(
              labelText: l10n.pinLabel,
              errorText: pinError,
            ),
            obscureText: true,
          ),
          const SizedBox(height: 12),
          ListenableBuilder(
            listenable: Listenable.merge([_codeController, _pinController]),
            builder: (context, _) {
              final canLookup = _validCode && !_outcomeUnknown;
              final canVerify =
                  _validCode &&
                  _validPin &&
                  _payout?.payoutCode == _codeController.text.trim() &&
                  _payout?.status.toUpperCase() == 'PENDING' &&
                  _payout?.settlementPath == 'AGENT_CASH' &&
                  !_completed;
              final hint = !_validCode
                  ? l10n.requiredField
                  : !_validPin
                  ? l10n.agentPinLength
                  : _payout?.payoutCode != _codeController.text.trim()
                  ? l10n.payoutLookupFirst
                  : null;
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Wrap(
                    spacing: 12,
                    children: [
                      FilledButton(
                        onPressed: _busy || !canLookup ? null : _lookupCode,
                        child: Text(l10n.lookup),
                      ),
                      OutlinedButton(
                        onPressed: _busy || !canVerify ? null : _confirmVerify,
                        child: Text(
                          _outcomeUnknown ? l10n.retry : l10n.verifyAndDisburse,
                        ),
                      ),
                    ],
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
          if (_lookup != null && _payout != null) ...[
            const SizedBox(height: 16),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _lookup!,
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    const SizedBox(height: 8),
                    Text('${l10n.payoutCode}: ${_payout!.payoutCode}'),
                    Text('${l10n.sessionId}: ${_payout!.sessionId}'),
                    Text('${l10n.ticketId}: ${_payout!.ticketId}'),
                    Text(
                      '${l10n.transactionStatus}: ${_payout!.status}',
                    ),
                    if (_payout!.status.toUpperCase() != 'PENDING') ...[
                      const SizedBox(height: 8),
                      Text(
                        l10n.payoutUnavailable,
                        style: TextStyle(color: Theme.of(context).colorScheme.error),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Future<void> _confirmVerify() async {
    final payout = _payout;
    if (_busy ||
        _completed ||
        payout == null ||
        payout.status.toUpperCase() != 'PENDING' ||
        payout.settlementPath != 'AGENT_CASH') {
      return;
    }
    final l10n = AppLocalizations.of(context)!;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(l10n.verifyAndDisburse),
        content: Text(
          '${payout.prizeAmount} ${payout.currency}\n'
          '${l10n.payoutCode}: ${payout.payoutCode}\n'
          '${l10n.sessionId}: ${payout.sessionId} · '
          '${l10n.ticketId}: ${payout.ticketId}',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: Text(l10n.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: Text(l10n.verifyAndDisburse),
          ),
        ],
      ),
    );
    if (confirmed == true && mounted) await _verify();
  }
}
