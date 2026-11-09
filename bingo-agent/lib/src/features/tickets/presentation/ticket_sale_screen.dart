import 'package:bingo_agent/src/core/network/api_error.dart';
import 'package:bingo_agent/src/core/network/idempotency.dart';
import 'package:bingo_agent/src/core/validation/ethiopian_phone.dart';
import 'package:bingo_agent/src/core/widgets/responsive_form_content.dart';
import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
import 'package:bingo_agent/src/features/tickets/data/ticket_sale_session.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final ticketSaleSessionsProvider =
    FutureProvider.autoDispose<List<TicketSaleSession>>(
      (ref) => ref.watch(agentRepositoryProvider).ticketSaleSessions(),
    );

class TicketSaleScreen extends ConsumerStatefulWidget {
  const TicketSaleScreen({super.key});

  @override
  ConsumerState<TicketSaleScreen> createState() => _TicketSaleScreenState();
}

class _TicketSaleScreenState extends ConsumerState<TicketSaleScreen> {
  final _phoneController = TextEditingController();
  final _pinController = TextEditingController();
  int? _selectedSessionId;
  String? _ticketCode;
  String? _error;
  bool _busy = false;
  String? _idempotencyKey;
  Map<String, dynamic>? _attemptBody;
  bool _outcomeUnknown = false;
  ApiError? _apiError;

  int? get _sessionId => _selectedSessionId;
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
    _phoneController.dispose();
    _pinController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final l10n = AppLocalizations.of(context)!;
    final isNewAttempt = _attemptBody == null;
    if (_attemptBody == null) {
      final sessionId = _sessionId;
      if (sessionId == null || sessionId <= 0 || !_validPhone || !_validPin) {
        setState(() {
          _apiError = null;
          _error = sessionId == null || sessionId <= 0
              ? l10n.ticketSaleSelectSession
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
    if (isNewAttempt) {
      final selectedSession = ref
          .read(ticketSaleSessionsProvider)
          .value
          ?.where((session) => session.id == _sessionId)
          .firstOrNull;
      if (selectedSession == null) {
        setState(() => _error = l10n.ticketSaleSessionsLoadFailed);
        _attemptBody = null;
        _idempotencyKey = null;
        return;
      }
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: Text(l10n.ticketSaleConfirmTitle),
          content: Text(
            '${selectedSession.roomName} · ${selectedSession.sessionName}\n'
            '${l10n.ticketSaleSessionId(selectedSession.id)}\n'
            '${l10n.playerPhone}: ${_phoneController.text.trim()}\n'
            '${l10n.ticketSaleConfirmation}\n\n'
            '${selectedSession.ticketPrice} ${selectedSession.currency}',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(false),
              child: Text(l10n.cancel),
            ),
            FilledButton(
              onPressed: () => Navigator.of(dialogContext).pop(true),
              child: Text(l10n.confirm),
            ),
          ],
        ),
      );
      if (!mounted || confirmed != true) {
        _attemptBody = null;
        _idempotencyKey = null;
        return;
      }
    }
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
        ref.invalidate(ticketSaleSessionsProvider);
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
        if (!parsed.outcomeUnknown) ref.invalidate(ticketSaleSessionsProvider);
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
        (_error == l10n.ticketSaleSelectSession ? _error : null);
    final phoneError =
        fieldErrorMessage(_apiError, 'playerPhoneNumber', l10n) ??
        (_error == l10n.errorInvalidPhone ? _error : null);
    final pinError =
        fieldErrorMessage(_apiError, 'pin', l10n) ??
        (_error == l10n.agentPinLength ? _error : null);
    final hasInlineError =
        sessionError != null || phoneError != null || pinError != null;
    final sessionsAsync = ref.watch(ticketSaleSessionsProvider);
    final sessions = sessionsAsync.valueOrNull ?? const <TicketSaleSession>[];
    final selectedSession = sessions
        .where((session) => session.id == _selectedSessionId)
        .firstOrNull;
    return ResponsiveFormContent(
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(
            l10n.ticketSaleTitle,
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 12),
          if (sessionsAsync.isLoading)
            const LinearProgressIndicator()
          else if (sessionsAsync.hasError)
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(l10n.ticketSaleSessionsLoadFailed),
              trailing: IconButton(
                tooltip: l10n.ticketSaleRefreshSessions,
                onPressed: () => ref.invalidate(ticketSaleSessionsProvider),
                icon: const Icon(Icons.refresh),
              ),
            )
          else if (sessions.isEmpty)
            Card(
              child: ListTile(
                leading: const Icon(Icons.event_busy_outlined),
                title: Text(l10n.ticketSaleNoSessions),
                trailing: IconButton(
                  tooltip: l10n.ticketSaleRefreshSessions,
                  onPressed: () => ref.invalidate(ticketSaleSessionsProvider),
                  icon: const Icon(Icons.refresh),
                ),
              ),
            )
          else
            DropdownButtonFormField<int>(
              initialValue: selectedSession?.id,
              isExpanded: true,
              decoration: InputDecoration(
                labelText: l10n.ticketSaleSelectSession,
                errorText: sessionError,
                suffixIcon: IconButton(
                  tooltip: l10n.ticketSaleRefreshSessions,
                  onPressed: _busy || _outcomeUnknown
                      ? null
                      : () => ref.invalidate(ticketSaleSessionsProvider),
                  icon: const Icon(Icons.refresh),
                ),
              ),
              items: [
                for (final session in sessions)
                  DropdownMenuItem(
                    value: session.id,
                    child: Text(
                      '${session.roomName} · ${session.sessionName} · '
                      '${session.ticketPrice} ${session.currency}',
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
              ],
              onChanged: _busy || _outcomeUnknown
                  ? null
                  : (value) => setState(() {
                      _selectedSessionId = value;
                      _apiError = null;
                      _error = null;
                      _ticketCode = null;
                    }),
            ),
          if (selectedSession != null) ...[
            const SizedBox(height: 8),
            Text(
              l10n.ticketSaleAvailableSeats(selectedSession.seatsLeft),
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ],
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
              _phoneController,
              _pinController,
            ]),
            builder: (context, _) {
              final canSubmit =
                  selectedSession != null && _validPhone && _validPin;
              final hint = selectedSession == null
                  ? l10n.ticketSaleSelectSession
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
      ),
    );
  }
}
