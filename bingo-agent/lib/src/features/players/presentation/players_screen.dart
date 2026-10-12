import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
import 'package:bingo_agent/src/features/auth/data/auth_models.dart';
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
  UserProfile? _player;

  @override
  void dispose() {
    _phoneController.dispose();
    _nameController.dispose();
    super.dispose();
  }

  Future<void> _lookup() async {
    setState(() => _busy = true);
    try {
      _player = await ref.read(agentRepositoryProvider).lookupPlayer(_phoneController.text.trim());
    } catch (_) {
      _player = null;
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _register() async {
    setState(() => _busy = true);
    try {
      _player = await ref.read(agentRepositoryProvider).registerPlayer({
        'phoneNumber': _phoneController.text.trim(),
        'displayName': _nameController.text.trim(),
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('Fast player lookup and registration', style: Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height: 12),
        TextField(controller: _phoneController, decoration: const InputDecoration(labelText: 'Phone number')),
        const SizedBox(height: 12),
        TextField(controller: _nameController, decoration: const InputDecoration(labelText: 'Display name')),
        const SizedBox(height: 12),
        Wrap(spacing: 12, children: [
          FilledButton(onPressed: _busy ? null : _lookup, child: const Text('Lookup')),
          OutlinedButton(onPressed: _busy ? null : _register, child: const Text('Register')),
        ]),
        const SizedBox(height: 16),
        if (_player != null) Card(child: ListTile(title: Text(_player!.displayName), subtitle: Text(_player!.phoneNumber))),
      ],
    );
  }
}