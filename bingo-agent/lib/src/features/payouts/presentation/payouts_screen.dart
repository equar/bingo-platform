import 'package:bingo_agent/src/core/network/api_error.dart';
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
  String? _idempotencyKey;
  Map<String, dynamic>? _attemptBody;
  bool _outcomeUnknown = false;
  ApiError? _apiError;

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
      _apiError = null;
    });
    try {
      final payout = await ref
          .read(agentRepositoryProvider)
          .lookupPayout(_codeController.text.trim());
      if (mounted) {
        setState(() => _lookup = '${payout.prizeAmount} ${payout.currency}');
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
    final l10n = AppLocalizations.of(context)!;
    if (_codeController.text.trim().isEmpty ||
        _pinController.text.trim().isEmpty) {
      setState(() => _error = l10n.requiredField);
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
    final codeError = fieldErrorMessage(_apiError, 'payoutCode', l10n);
    final pinError = fieldErrorMessage(_apiError, 'pin', l10n);
    final hasInlineError = codeError != null || pinError != null;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(
          l10n.approvedAgentCashPayout,
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _codeController,
          readOnly: _outcomeUnknown,
          decoration: InputDecoration(
            labelText: l10n.payoutCode,
            errorText: codeError,
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
        Wrap(
          spacing: 12,
          children: [
            FilledButton(
              onPressed: _busy || _outcomeUnknown ? null : _lookupCode,
              child: Text(l10n.lookup),
            ),
            OutlinedButton(
              onPressed: _busy ? null : _verify,
              child: Text(
                _outcomeUnknown ? l10n.retry : l10n.verifyAndDisburse,
              ),
            ),
          ],
        ),
        if (_error != null && !hasInlineError) ...[
          const SizedBox(height: 8),
          Text(
            _error!,
            style: TextStyle(color: Theme.of(context).colorScheme.error),
          ),
        ],
        if (_lookup != null) ...[const SizedBox(height: 16), Text(_lookup!)],
      ],
    );
  }
}
