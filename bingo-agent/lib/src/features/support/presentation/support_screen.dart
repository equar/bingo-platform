import 'package:bingo_agent/src/core/network/api_error.dart';
import 'package:bingo_agent/src/features/auth/data/auth_session_controller.dart';
import 'package:bingo_agent/src/features/support/data/support_models.dart';
import 'package:bingo_agent/src/features/support/data/support_repository.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

final supportTicketsProvider = FutureProvider.autoDispose(
  (ref) => ref.watch(supportRepositoryProvider).myTickets(),
);

class SupportScreen extends ConsumerStatefulWidget {
  const SupportScreen({super.key});

  @override
  ConsumerState<SupportScreen> createState() => _SupportScreenState();
}

class _SupportScreenState extends ConsumerState<SupportScreen> {
  bool _creating = false;

  Future<void> _createTicket(BuildContext context, WidgetRef ref) async {
    if (_creating) return;
    setState(() => _creating = true);
    final l10n = AppLocalizations.of(context)!;
    try {
      final request = await showModalBottomSheet<SupportTicketCreateRequest>(
        context: context,
        isScrollControlled: true,
        useSafeArea: true,
        builder: (_) => const _CreateSupportTicketSheet(),
      );
      if (request == null || !context.mounted) return;
      await ref.read(supportRepositoryProvider).createTicket(request);
      ref.invalidate(supportTicketsProvider);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(l10n.supportTicketCreated)),
        );
      }
    } catch (error) {
      if (context.mounted) {
        final message = userFacingApiError(error, l10n) ?? l10n.requestFailed;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(message)),
        );
      }
    } finally {
      if (mounted) setState(() => _creating = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final asyncValue = ref.watch(supportTicketsProvider);
    return Scaffold(
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _creating ? null : () => _createTicket(context, ref),
        icon: _creating
            ? const SizedBox.square(
                dimension: 18,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : const Icon(Icons.add_comment_outlined),
        label: Text(
          _creating ? l10n.loading : l10n.supportCreateTicket,
        ),
      ),
      body: asyncValue.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stack) => _SupportLoadError(
          message: userFacingApiError(error, l10n) ?? l10n.requestFailed,
          onRetry: () => ref.invalidate(supportTicketsProvider),
        ),
        data: (page) => RefreshIndicator(
          onRefresh: () async {
            ref.invalidate(supportTicketsProvider);
            try {
              await ref.read(supportTicketsProvider.future);
            } catch (_) {
              // The provider error state includes a retry action.
            }
          },
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
            children: [
              Text(
                l10n.supportTickets,
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              const SizedBox(height: 12),
              if (page.content.isEmpty)
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      children: [
                        Icon(
                          Icons.support_agent_outlined,
                          size: 40,
                          color: Theme.of(context).colorScheme.primary,
                        ),
                        const SizedBox(height: 12),
                        Text(
                          l10n.noSupportTickets,
                          textAlign: TextAlign.center,
                        ),
                      ],
                    ),
                  ),
                )
              else
                ...page.content.map(
                  (ticket) => Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: Card(
                      child: ListTile(
                        leading: const CircleAvatar(
                          child: Icon(Icons.support_agent_outlined),
                        ),
                        title: Text(
                          ticket.subject,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                        subtitle: Padding(
                          padding: const EdgeInsets.only(top: 6),
                          child: Wrap(
                            spacing: 8,
                            runSpacing: 6,
                            crossAxisAlignment: WrapCrossAlignment.center,
                            children: [
                              Text(ticket.ticketNumber),
                              _StatusChip(status: ticket.status),
                              Text(
                                DateFormat.yMMMd(
                                  Localizations.localeOf(context).toString(),
                                ).format(ticket.updatedAt.toLocal()),
                              ),
                            ],
                          ),
                        ),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () => Navigator.of(context).push<void>(
                          MaterialPageRoute(
                            builder: (_) => SupportTicketDetailScreen(
                              ticketId: ticket.id,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class SupportTicketDetailScreen extends ConsumerStatefulWidget {
  const SupportTicketDetailScreen({super.key, required this.ticketId});

  final int ticketId;

  @override
  ConsumerState<SupportTicketDetailScreen> createState() =>
      _SupportTicketDetailScreenState();
}

final supportTicketDetailProvider =
    FutureProvider.autoDispose.family<SupportTicketDetail, int>(
      (ref, id) => ref.watch(supportRepositoryProvider).ticketDetail(id),
    );

class _SupportTicketDetailScreenState
    extends ConsumerState<SupportTicketDetailScreen> {
  final _replyController = TextEditingController();
  bool _sending = false;

  @override
  void dispose() {
    _replyController.dispose();
    super.dispose();
  }

  Future<void> _sendReply() async {
    final body = _replyController.text.trim();
    if (body.isEmpty || _sending) return;
    setState(() => _sending = true);
    final l10n = AppLocalizations.of(context)!;
    try {
      await ref.read(supportRepositoryProvider).reply(widget.ticketId, body);
      if (!mounted) return;
      _replyController.clear();
      ref.invalidate(supportTicketDetailProvider(widget.ticketId));
      ref.invalidate(supportTicketsProvider);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.supportTicketReplySent)),
      );
    } catch (error) {
      if (mounted) {
        final message = userFacingApiError(error, l10n) ?? l10n.requestFailed;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(message)),
        );
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final ticketAsync = ref.watch(supportTicketDetailProvider(widget.ticketId));
    return Scaffold(
      appBar: AppBar(title: Text(l10n.supportTicketDetails)),
      body: ticketAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stack) => _SupportLoadError(
          message: userFacingApiError(error, l10n) ?? l10n.requestFailed,
          onRetry: () =>
              ref.invalidate(supportTicketDetailProvider(widget.ticketId)),
        ),
        data: (ticket) {
          final isClosed = ticket.status == 'CLOSED';
          final currentUserId = ref
              .watch(authSessionControllerProvider)
              .session
              ?.user
              .id;
          return Column(
            children: [
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              ticket.subject,
                              style: Theme.of(context).textTheme.titleLarge,
                            ),
                            const SizedBox(height: 8),
                            Wrap(
                              spacing: 8,
                              runSpacing: 6,
                              crossAxisAlignment: WrapCrossAlignment.center,
                              children: [
                                Text(ticket.ticketNumber),
                                _StatusChip(status: ticket.status),
                              ],
                            ),
                            const Divider(height: 24),
                            Text(ticket.description),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    Text(
                      l10n.supportTicketConversation,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 8),
                    if (ticket.messages.isEmpty)
                      Text(l10n.noSupportTickets)
                    else
                      for (final message in ticket.messages)
                        Builder(
                          builder: (context) {
                            final isMine =
                                message.authorUserId == currentUserId;
                            return Align(
                              alignment: isMine
                                  ? AlignmentDirectional.centerEnd
                                  : AlignmentDirectional.centerStart,
                              child: ConstrainedBox(
                                constraints: const BoxConstraints(
                                  maxWidth: 560,
                                ),
                                child: Card(
                                  child: Padding(
                                    padding: const EdgeInsets.all(14),
                                    child: Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          isMine
                                              ? l10n.supportYou
                                              : l10n.supportTeam,
                                          style: Theme.of(context)
                                              .textTheme
                                              .labelMedium
                                              ?.copyWith(
                                                fontWeight: FontWeight.w700,
                                              ),
                                        ),
                                        const SizedBox(height: 6),
                                        Text(message.body),
                                        const SizedBox(height: 6),
                                        Text(
                                          DateFormat.yMMMd(
                                            Localizations.localeOf(context)
                                                .toString(),
                                          ).add_jm().format(
                                            message.createdAt.toLocal(),
                                          ),
                                          style: Theme.of(context)
                                              .textTheme
                                              .bodySmall,
                                        ),
                                      ],
                                    ),
                                  ),
                                ),
                              ),
                            );
                          },
                        ),
                  ],
                ),
              ),
              if (isClosed)
                SafeArea(
                  top: false,
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Text(l10n.supportTicketClosed),
                  ),
                )
              else
                SafeArea(
                  top: false,
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _replyController,
                            minLines: 1,
                            maxLines: 4,
                            maxLength: 2000,
                            textCapitalization: TextCapitalization.sentences,
                            textInputAction: TextInputAction.send,
                            onSubmitted: (_) => _sendReply(),
                            enabled: !_sending,
                            decoration: InputDecoration(
                              labelText: l10n.supportTicketReply,
                              counterText: '',
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        IconButton.filled(
                          tooltip: l10n.supportTicketSendReply,
                          onPressed: _sending ? null : _sendReply,
                          icon: _sending
                              ? const SizedBox.square(
                                  dimension: 18,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                  ),
                                )
                              : const Icon(Icons.send_rounded),
                        ),
                      ],
                    ),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }
}

class _CreateSupportTicketSheet extends StatefulWidget {
  const _CreateSupportTicketSheet();

  @override
  State<_CreateSupportTicketSheet> createState() =>
      _CreateSupportTicketSheetState();
}

class _CreateSupportTicketSheetState extends State<_CreateSupportTicketSheet> {
  final _formKey = GlobalKey<FormState>();
  final _subject = TextEditingController();
  final _description = TextEditingController();
  String _category = 'OTHER';
  String _priority = 'NORMAL';

  @override
  void dispose() {
    _subject.dispose();
    _description.dispose();
    super.dispose();
  }

  String _categoryLabel(AppLocalizations l10n, String value) => switch (value) {
    'ACCOUNT' => l10n.supportAccount,
    'WALLET' => l10n.supportWallet,
    'GAME' => l10n.supportGame,
    'AGENT' => l10n.supportAgent,
    'TECHNICAL' => l10n.supportTechnical,
    'BILLING' => l10n.supportBilling,
    _ => l10n.supportOther,
  };

  String _priorityLabel(AppLocalizations l10n, String value) => switch (value) {
    'LOW' => l10n.priorityLow,
    'HIGH' => l10n.priorityHigh,
    'URGENT' => l10n.priorityUrgent,
    _ => l10n.priorityNormal,
  };

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Padding(
      padding: EdgeInsets.fromLTRB(
        16,
        8,
        16,
        16 + MediaQuery.viewInsetsOf(context).bottom,
      ),
      child: SingleChildScrollView(
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                l10n.supportCreateTicket,
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _subject,
                maxLength: 120,
                textCapitalization: TextCapitalization.sentences,
                decoration: InputDecoration(labelText: l10n.supportSubject),
                validator: (value) => value == null || value.trim().isEmpty
                    ? l10n.requiredField
                    : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _description,
                minLines: 3,
                maxLines: 6,
                maxLength: 4000,
                textCapitalization: TextCapitalization.sentences,
                decoration: InputDecoration(
                  labelText: l10n.supportDescription,
                  alignLabelWithHint: true,
                ),
                validator: (value) => value == null || value.trim().isEmpty
                    ? l10n.requiredField
                    : null,
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                initialValue: _category,
                isExpanded: true,
                decoration: InputDecoration(labelText: l10n.supportCategory),
                items: [
                  for (final value in [
                    'ACCOUNT',
                    'WALLET',
                    'GAME',
                    'AGENT',
                    'TECHNICAL',
                    'BILLING',
                    'OTHER',
                  ])
                    DropdownMenuItem(
                      value: value,
                      child: Text(_categoryLabel(l10n, value)),
                    ),
                ],
                onChanged: (value) {
                  if (value != null) setState(() => _category = value);
                },
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                initialValue: _priority,
                isExpanded: true,
                decoration: InputDecoration(labelText: l10n.supportPriority),
                items: [
                  for (final value in ['LOW', 'NORMAL', 'HIGH', 'URGENT'])
                    DropdownMenuItem(
                      value: value,
                      child: Text(_priorityLabel(l10n, value)),
                    ),
                ],
                onChanged: (value) {
                  if (value != null) setState(() => _priority = value);
                },
              ),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: () {
                  if (!_formKey.currentState!.validate()) return;
                  Navigator.of(context).pop(
                    SupportTicketCreateRequest(
                      subject: _subject.text.trim(),
                      description: _description.text.trim(),
                      category: _category,
                      priority: _priority,
                    ),
                  );
                },
                child: Text(l10n.supportCreateTicket),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.status});

  final String status;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final (label, color) = switch (status) {
      'IN_PROGRESS' => (l10n.supportStatusInProgress, const Color(0xFF2461A5)),
      'WAITING_FOR_CUSTOMER' =>
        (l10n.supportStatusWaiting, const Color(0xFF8A5B00)),
      'RESOLVED' => (l10n.supportStatusResolved, const Color(0xFF257343)),
      'CLOSED' => (l10n.supportStatusClosed, const Color(0xFF67736D)),
      _ => (l10n.supportStatusOpen, Theme.of(context).colorScheme.primary),
    };
    return Chip(
      visualDensity: VisualDensity.compact,
      label: Text(label),
      side: BorderSide.none,
      backgroundColor: color.withValues(alpha: .1),
      labelStyle: TextStyle(color: color, fontWeight: FontWeight.w700),
    );
  }
}

class _SupportLoadError extends StatelessWidget {
  const _SupportLoadError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 12),
            FilledButton(onPressed: onRetry, child: Text(l10n.retry)),
          ],
        ),
      ),
    );
  }
}
