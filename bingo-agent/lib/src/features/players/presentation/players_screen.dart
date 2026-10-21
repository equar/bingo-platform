import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
import 'package:bingo_agent/src/features/auth/data/auth_models.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class PlayersScreen extends ConsumerStatefulWidget {
  const PlayersScreen({super.key});

  @override
  ConsumerState<PlayersScreen> createState() => _PlayersScreenState();
}

class _PlayersScreenState extends ConsumerState<PlayersScreen> {
  final _phoneController = TextEditingController();
  final _nameController = TextEditingController();
  bool _busy = false;
  String? _error;
  UserProfile? _player;

  @override
  void dispose() {
    _phoneController.dispose();
    _nameController.dispose();
    super.dispose();
  }

  Future<void> _lookup() async {
    final requestFailed = AppLocalizations.of(context)!.requestFailed;
    setState(() => _busy = true);
    try {
      _player = await ref
          .read(agentRepositoryProvider)
          .lookupPlayer(_phoneController.text.trim());
    } catch (_) {
      _player = null;
      _error = requestFailed;
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _register() async {
    final requestFailed = AppLocalizations.of(context)!.requestFailed;
    setState(() => _busy = true);
    try {
      _player = await ref.read(agentRepositoryProvider).registerPlayer({
        'phoneNumber': _phoneController.text.trim(),
        'displayName': _nameController.text.trim(),
      });
      _error = null;
    } catch (_) {
      _error = requestFailed;
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
        Text(l10n.playerLookupRegistration,
            style: Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height: 12),
        TextField(
            controller: _phoneController,
            decoration: InputDecoration(labelText: l10n.phoneLabel)),
        const SizedBox(height: 12),
        TextField(
            controller: _nameController,
            decoration: InputDecoration(labelText: l10n.displayName)),
        const SizedBox(height: 12),
        Wrap(spacing: 12, children: [
          FilledButton(
              onPressed: _busy ? null : _lookup, child: Text(l10n.lookup)),
          OutlinedButton(
              onPressed: _busy ? null : _register, child: Text(l10n.register)),
        ]),
        if (_error != null) ...[
          const SizedBox(height: 8),
          Text(_error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error)),
        ],
        const SizedBox(height: 16),
        if (_player != null)
          Card(
              child: ListTile(
                  title: Text(_player!.displayName),
                  subtitle: Text(_player!.phoneNumber))),
      ],
    );
  }
}
