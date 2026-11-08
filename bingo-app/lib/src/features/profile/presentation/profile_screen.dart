import 'package:bingo_app/src/features/support/presentation/support_shortcut.dart';
import 'package:bingo_app/src/core/widgets/responsive_content.dart';
import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/core/widgets/section_card.dart';
import 'package:bingo_app/src/core/localization/language_selector.dart';
import 'package:bingo_app/src/features/auth/data/auth_repository.dart';
import 'package:bingo_app/src/features/auth/data/auth_session_controller.dart';
import 'package:bingo_app/src/features/auth/data/auth_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class ProfileScreen extends ConsumerStatefulWidget {
  const ProfileScreen({super.key, this.onGames, this.onWallet, this.onSupport});

  final VoidCallback? onGames;
  final VoidCallback? onWallet;
  final VoidCallback? onSupport;

  @override
  ConsumerState<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends ConsumerState<ProfileScreen> {
  UserProfile? _serverProfile;
  bool _isRefreshing = true;
  bool _hasRefreshError = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _refreshProfile();
    });
  }

  Future<void> _refreshProfile() async {
    setState(() {
      _isRefreshing = true;
      _hasRefreshError = false;
    });
    try {
      final profile = await ref.read(authRepositoryProvider).me();
      if (!mounted) return;
      setState(() {
        _serverProfile = profile;
        _isRefreshing = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _isRefreshing = false;
        _hasRefreshError = true;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final session = ref.watch(authSessionControllerProvider).session;
    if (session == null) {
      return Scaffold(
        body: ResponsiveContent(
          child: Center(child: Text(localizations.errorGeneric)),
        ),
      );
    }
    final profile = _serverProfile ?? session.user;

    return Scaffold(
      appBar: AppBar(
        title: Text(localizations.profileTitle),
        actions: [
          const SupportShortcut(),
          IconButton(
            key: const ValueKey('profile-refresh-button'),
            tooltip: localizations.profileRefresh,
            onPressed: _isRefreshing ? null : _refreshProfile,
            icon: _isRefreshing
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.refresh),
          ),
        ],
      ),
      body: ResponsiveContent(
        child: RefreshIndicator(
          onRefresh: _refreshProfile,
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(16),
            children: [
              if (_hasRefreshError)
                SectionCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(localizations.profileLoadError),
                      TextButton(
                        key: const ValueKey('profile-retry-button'),
                        onPressed: _refreshProfile,
                        child: Text(localizations.retry),
                      ),
                    ],
                  ),
                ),
              if (_isRefreshing && _serverProfile == null)
                const LinearProgressIndicator(
                  key: ValueKey('profile-loading-indicator'),
                ),
              SectionCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const CircleAvatar(
                      radius: 28,
                      backgroundColor: Color(0xFFEBF4FF),
                      child: Icon(
                        Icons.person_rounded,
                        size: 32,
                        color: Color(0xFF1264C8),
                      ),
                    ),
                    const SizedBox(height: 16),
                    Text(
                      profile.displayName,
                      style: Theme.of(context).textTheme.headlineSmall,
                    ),
                    const SizedBox(height: 8),
                    Text(profile.phoneNumber),
                    const SizedBox(height: 12),
                    Text(
                      '${localizations.profileMemberSince}: ${profile.createdAt.toLocal()}',
                    ),
                    const SizedBox(height: 12),
                    Wrap(
                      spacing: 8,
                      children: profile.roles
                          .map((role) => Chip(label: Text(role)))
                          .toList(),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              SectionCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      localizations.profileLanguagePreference,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 4),
                    Text(localizations.profileLanguagePreferenceDescription),
                    const SizedBox(height: 8),
                    const Align(
                      alignment: AlignmentDirectional.centerEnd,
                      child: LanguageSelector(),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              if (widget.onGames != null ||
                  widget.onWallet != null ||
                  widget.onSupport != null) ...[
                SectionCard(
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  child: Column(
                    children: [
                      if (widget.onGames != null)
                        _shortcut(
                          Icons.grid_view_rounded,
                          localizations.homeGames,
                          widget.onGames!,
                        ),
                      if (widget.onWallet != null)
                        _shortcut(
                          Icons.account_balance_wallet_outlined,
                          localizations.homeWallet,
                          widget.onWallet!,
                        ),
                      if (widget.onSupport != null)
                        _shortcut(
                          Icons.support_agent_rounded,
                          localizations.homeSupport,
                          widget.onSupport!,
                        ),
                    ],
                  ),
                ),
                const SizedBox(height: 24),
              ],
              OutlinedButton.icon(
                icon: const Icon(Icons.logout_rounded),
                onPressed: () => ref
                    .read(authSessionControllerProvider.notifier)
                    .clearSession(),
                label: Text(localizations.logout),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _shortcut(IconData icon, String label, VoidCallback onTap) => ListTile(
    leading: Icon(icon, color: Theme.of(context).colorScheme.primary),
    title: Text(label),
    trailing: const Icon(Icons.chevron_right_rounded),
    onTap: onTap,
  );
}
