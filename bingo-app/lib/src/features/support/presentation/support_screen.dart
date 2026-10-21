import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/core/network/api_provider.dart';
import 'package:bingo_app/src/features/support/domain/support_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final playerSupportTicketsProvider =
    FutureProvider.autoDispose<SupportTicketPage>((ref) {
  return ref.watch(supportApiProvider).getMyTickets(page: 0, size: 20);
});

const _categories = [
  'ACCOUNT',
  'WALLET',
  'GAME',
  'AGENT',
  'TECHNICAL',
  'BILLING',
  'OTHER'
];
const _priorities = ['LOW', 'NORMAL', 'HIGH', 'URGENT'];

class SupportScreen extends ConsumerWidget {
  const SupportScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final localizations = AppLocalizations.of(context)!;
    final tickets = ref.watch(playerSupportTicketsProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(localizations.supportTitle),
        actions: [
          IconButton(
            key: const ValueKey('support-refresh-button'),
            tooltip: localizations.refresh,
            onPressed: () => ref.invalidate(playerSupportTicketsProvider),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(localizations.supportIntro,
              style: Theme.of(context).textTheme.bodyLarge),
          const SizedBox(height: 16),
          FilledButton.icon(
            key: const ValueKey('support-create-button'),
            onPressed: () async {
              final createdTicket =
                  await Navigator.of(context).push<SupportTicketSummary>(
                MaterialPageRoute(
                    builder: (_) => const SupportTicketFormScreen()),
              );
              if (createdTicket != null && context.mounted) {
                ref.invalidate(playerSupportTicketsProvider);
                ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text(localizations.supportSubmitted)));
              }
            },
            icon: const Icon(Icons.add),
            label: Text(localizations.supportCreateTicket),
          ),
          const SizedBox(height: 20),
          tickets.when(
            loading: () => const Center(
                child: Padding(
                    padding: EdgeInsets.all(24),
                    child: CircularProgressIndicator())),
            error: (error, _) => Column(
              children: [
                Text(error.toString(), textAlign: TextAlign.center),
                const SizedBox(height: 8),
                OutlinedButton(
                  onPressed: () => ref.invalidate(playerSupportTicketsProvider),
                  child: Text(localizations.retry),
                ),
              ],
            ),
            data: (page) => page.content.isEmpty
                ? Padding(
                    padding: const EdgeInsets.symmetric(vertical: 28),
                    child: Center(child: Text(localizations.supportEmpty)),
                  )
                : Column(
                    children: page.content
                        .map((ticket) => Card(
                              child: ListTile(
                                key: ValueKey('support-ticket-${ticket.id}'),
                                title: Text(ticket.subject),
                                subtitle: Text(
                                    '${ticket.ticketNumber} · ${ticket.category} · ${ticket.createdAt.toLocal()}'),
                                trailing: Column(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  crossAxisAlignment: CrossAxisAlignment.end,
                                  children: [
                                    Text(ticket.status),
                                    Text(ticket.priority,
                                        style: Theme.of(context)
                                            .textTheme
                                            .labelSmall),
                                  ],
                                ),
                              ),
                            ))
                        .toList(),
                  ),
          ),
        ],
      ),
    );
  }
}

class SupportTicketFormScreen extends ConsumerStatefulWidget {
  const SupportTicketFormScreen({super.key});

  @override
  ConsumerState<SupportTicketFormScreen> createState() =>
      _SupportTicketFormScreenState();
}

class _SupportTicketFormScreenState
    extends ConsumerState<SupportTicketFormScreen> {
  final _formKey = GlobalKey<FormState>();
  final _subjectController = TextEditingController();
  final _descriptionController = TextEditingController();
  String _category = 'OTHER';
  String _priority = 'NORMAL';
  String? _error;
  bool _submitting = false;

  @override
  void dispose() {
    _subjectController.dispose();
    _descriptionController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _submitting = true;
      _error = null;
    });

    try {
      final ticket = await ref.read(supportApiProvider).createTicket(
            SupportTicketCreateRequest(
              subject: _subjectController.text.trim(),
              description: _descriptionController.text.trim(),
              category: _category,
              priority: _priority,
            ),
          );
      if (mounted) Navigator.of(context).pop(ticket);
    } catch (error) {
      if (mounted) {
        setState(() {
          _submitting = false;
          _error = error.toString();
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: AppBar(title: Text(localizations.supportCreateTicket)),
      body: SafeArea(
        child: Form(
          key: _formKey,
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              TextFormField(
                key: const ValueKey('support-subject-field'),
                controller: _subjectController,
                textCapitalization: TextCapitalization.sentences,
                decoration:
                    InputDecoration(labelText: localizations.supportSubject),
                validator: (value) => value == null || value.trim().isEmpty
                    ? localizations.requiredField
                    : null,
              ),
              const SizedBox(height: 16),
              TextFormField(
                key: const ValueKey('support-description-field'),
                controller: _descriptionController,
                minLines: 4,
                maxLines: 7,
                textCapitalization: TextCapitalization.sentences,
                decoration: InputDecoration(
                    labelText: localizations.supportDescription),
                validator: (value) => value == null || value.trim().isEmpty
                    ? localizations.requiredField
                    : null,
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<String>(
                key: const ValueKey('support-category-field'),
                initialValue: _category,
                decoration:
                    InputDecoration(labelText: localizations.supportCategory),
                items: _categories
                    .map((value) =>
                        DropdownMenuItem(value: value, child: Text(value)))
                    .toList(),
                onChanged: _submitting
                    ? null
                    : (value) => setState(() => _category = value ?? _category),
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<String>(
                key: const ValueKey('support-priority-field'),
                initialValue: _priority,
                decoration:
                    InputDecoration(labelText: localizations.supportPriority),
                items: _priorities
                    .map((value) =>
                        DropdownMenuItem(value: value, child: Text(value)))
                    .toList(),
                onChanged: _submitting
                    ? null
                    : (value) => setState(() => _priority = value ?? _priority),
              ),
              if (_error != null) ...[
                const SizedBox(height: 16),
                Text(_error!,
                    style:
                        TextStyle(color: Theme.of(context).colorScheme.error)),
              ],
              const SizedBox(height: 24),
              FilledButton(
                key: const ValueKey('support-submit-button'),
                onPressed: _submitting ? null : _submit,
                child: _submitting
                    ? const SizedBox.square(
                        dimension: 20,
                        child: CircularProgressIndicator(strokeWidth: 2))
                    : Text(localizations.supportSubmit),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
