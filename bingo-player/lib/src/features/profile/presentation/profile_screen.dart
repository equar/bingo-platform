import 'package:bingo_player/src/core/localization/ethiopian_date_time.dart';
import 'package:bingo_player/src/core/network/api_provider.dart';
import 'package:bingo_player/src/core/platform/external_link.dart';
import 'package:bingo_player/src/core/storage/player_preferences.dart';
import 'package:bingo_player/src/features/support/presentation/support_shortcut.dart';
import 'package:bingo_player/src/core/widgets/responsive_content.dart';
import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/core/widgets/section_card.dart';
import 'package:bingo_player/src/core/localization/language_selector.dart';
import 'package:bingo_player/src/features/auth/data/auth_repository.dart';
import 'package:bingo_player/src/features/auth/data/auth_session_controller.dart';
import 'package:bingo_player/src/features/auth/data/auth_models.dart';
import 'package:bingo_player/src/features/player_content/domain/player_content_models.dart';
import 'package:bingo_player/src/features/wallet/data/wallet_repository.dart';
import 'package:bingo_player/src/features/wallet/domain/wallet_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class ProfileScreen extends ConsumerStatefulWidget {
  const ProfileScreen({
    super.key,
    this.onGames,
    this.onWallet,
    this.onSupport,
    this.onMessages,
    this.onGameHistory,
  });

  final VoidCallback? onGames;
  final VoidCallback? onWallet;
  final VoidCallback? onSupport;
  final VoidCallback? onMessages;
  final VoidCallback? onGameHistory;

  @override
  ConsumerState<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends ConsumerState<ProfileScreen> {
  UserProfile? _serverProfile;
  bool _isRefreshing = true;
  bool _hasRefreshError = false;
  Future<PlayerAppContent>? _contentFuture;
  Future<WalletBalance>? _walletFuture;

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
      _contentFuture = ref.read(playerContentApiProvider).getContent();
      _walletFuture = ref.read(walletRepositoryProvider).getBalance();
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
    final preferences = ref.watch(playerPreferencesProvider);
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
              if (_walletFuture != null)
                FutureBuilder<WalletBalance>(
                  future: _walletFuture,
                  builder: (context, snapshot) {
                    final balance = snapshot.data;
                    if (balance == null) return const SizedBox.shrink();
                    return SectionCard(
                      child: Row(
                        children: [
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  localizations.accountBalance(
                                    profile.phoneNumber,
                                  ),
                                  style: Theme.of(context).textTheme.titleSmall,
                                ),
                                const SizedBox(height: 10),
                                Text(
                                  '${balance.totalBalance.toStringAsFixed(2)} ${balance.currency}',
                                  style: Theme.of(
                                    context,
                                  ).textTheme.headlineMedium,
                                ),
                                Text(localizations.totalBalance),
                              ],
                            ),
                          ),
                          Container(
                            padding: const EdgeInsets.all(12),
                            decoration: BoxDecoration(
                              color: Theme.of(
                                context,
                              ).colorScheme.primaryContainer,
                              borderRadius: BorderRadius.circular(16),
                            ),
                            child: Column(
                              children: [
                                Text(
                                  balance.availableBalance.toStringAsFixed(2),
                                  style: TextStyle(
                                    color: Theme.of(
                                      context,
                                    ).colorScheme.primary,
                                    fontWeight: FontWeight.w800,
                                  ),
                                ),
                                Text(localizations.withdrawableBalance),
                              ],
                            ),
                          ),
                          IconButton(
                            onPressed: _refreshProfile,
                            icon: const Icon(Icons.refresh),
                            tooltip: localizations.profileRefresh,
                          ),
                        ],
                      ),
                    );
                  },
                ),
              if (_contentFuture != null)
                FutureBuilder<PlayerAppContent>(
                  future: _contentFuture,
                  builder: (context, snapshot) {
                    final content = snapshot.data;
                    if (content == null) return const SizedBox.shrink();
                    final isAmharic =
                        Localizations.localeOf(context).languageCode == 'am';
                    final title =
                        isAmharic &&
                            (content.bannerTitleAm?.isNotEmpty ?? false)
                        ? content.bannerTitleAm!
                        : content.bannerTitleEn;
                    final body =
                        isAmharic && (content.bannerBodyAm?.isNotEmpty ?? false)
                        ? content.bannerBodyAm!
                        : content.bannerBodyEn;
                    final phoneNumbers = (content.supportPhoneNumbers ?? '')
                        .split(',')
                        .map((value) => value.trim())
                        .where((value) => value.isNotEmpty)
                        .toList();
                    if ([
                      title,
                      body,
                      content.bannerImageUrl,
                      content.telegramUrl,
                      content.tiktokUrl,
                      content.termsUrl,
                      content.supportPhoneNumbers,
                    ].every((value) => value == null || value.isEmpty)) {
                      return const SizedBox.shrink();
                    }
                    return Column(
                      children: [
                        SectionCard(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              if (content.bannerImageUrl?.isNotEmpty ?? false)
                                ClipRRect(
                                  borderRadius: BorderRadius.circular(16),
                                  child: Image.network(
                                    content.bannerImageUrl!,
                                    width: double.infinity,
                                    height: 150,
                                    fit: BoxFit.cover,
                                    errorBuilder: (_, _, _) =>
                                        const SizedBox.shrink(),
                                  ),
                                ),
                              if (title?.isNotEmpty ?? false)
                                Text(
                                  title!,
                                  style: Theme.of(context).textTheme.titleLarge,
                                ),
                              if (body?.isNotEmpty ?? false)
                                Padding(
                                  padding: const EdgeInsets.only(top: 6),
                                  child: Text(body!),
                                ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 12),
                        SectionCard(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                localizations.contactUs,
                                style: Theme.of(context).textTheme.titleMedium,
                              ),
                              if (content.telegramUrl?.isNotEmpty ?? false)
                                _externalLink(
                                  context,
                                  Icons.send_rounded,
                                  'Telegram',
                                  content.telegramUrl!,
                                ),
                              if (content.tiktokUrl?.isNotEmpty ?? false)
                                _externalLink(
                                  context,
                                  Icons.music_video_rounded,
                                  'TikTok',
                                  content.tiktokUrl!,
                                ),
                              if (content.termsUrl?.isNotEmpty ?? false)
                                _externalLink(
                                  context,
                                  Icons.description_outlined,
                                  localizations.termsAndConditions,
                                  content.termsUrl!,
                                ),
                              if (phoneNumbers.isNotEmpty)
                                ListTile(
                                  leading: const Icon(Icons.phone_outlined),
                                  title: Text(
                                    localizations.phoneLabelWithNumbers(
                                      phoneNumbers.join(', '),
                                    ),
                                  ),
                                ),
                            ],
                          ),
                        ),
                      ],
                    );
                  },
                ),
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
                    CircleAvatar(
                      radius: 28,
                      backgroundColor: Theme.of(
                        context,
                      ).colorScheme.primaryContainer,
                      child: Icon(
                        Icons.person_rounded,
                        size: 32,
                        color: Theme.of(context).colorScheme.onPrimaryContainer,
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
                      '${localizations.profileMemberSince}: ${EthiopianDateTime.date(profile.createdAt, languageCode: Localizations.localeOf(context).languageCode)}',
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
                      localizations.settingsTitle,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 10),
                    Text(
                      localizations.themeTitle,
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                    const SizedBox(height: 8),
                    SegmentedButton<ThemeMode>(
                      segments: [
                        ButtonSegment(
                          value: ThemeMode.light,
                          icon: Icon(Icons.light_mode_outlined),
                          label: Text(localizations.themeLight),
                        ),
                        ButtonSegment(
                          value: ThemeMode.dark,
                          icon: Icon(Icons.dark_mode_outlined),
                          label: Text(localizations.themeDark),
                        ),
                        ButtonSegment(
                          value: ThemeMode.system,
                          icon: Icon(Icons.brightness_auto_outlined),
                          label: Text(localizations.themeSystem),
                        ),
                      ],
                      selected: {preferences.themeMode},
                      onSelectionChanged: (value) =>
                          preferences.setTheme(value.first),
                    ),
                    const SizedBox(height: 8),
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(localizations.soundTitle),
                      subtitle: Text(localizations.soundDescription),
                      value: preferences.soundEnabled,
                      onChanged: preferences.setSound,
                    ),
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(localizations.notificationsTitle),
                      subtitle: Text(localizations.notificationsDescription),
                      value: preferences.notificationsEnabled,
                      onChanged: preferences.setNotifications,
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
                  widget.onSupport != null ||
                  widget.onMessages != null ||
                  widget.onGameHistory != null) ...[
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
                      if (widget.onMessages != null)
                        _shortcut(
                          Icons.mail_outline_rounded,
                          localizations.messagesTitle,
                          widget.onMessages!,
                        ),
                      if (widget.onGameHistory != null)
                        _shortcut(
                          Icons.sports_esports_outlined,
                          localizations.gameHistoryTitle,
                          widget.onGameHistory!,
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

  Widget _externalLink(
    BuildContext context,
    IconData icon,
    String label,
    String url,
  ) => ListTile(
    leading: Icon(icon, color: Theme.of(context).colorScheme.primary),
    title: Text(label),
    subtitle: Text(url, maxLines: 1, overflow: TextOverflow.ellipsis),
    trailing: const Icon(Icons.chevron_right_rounded),
    onTap: () => ExternalLink.open(url),
  );
}
