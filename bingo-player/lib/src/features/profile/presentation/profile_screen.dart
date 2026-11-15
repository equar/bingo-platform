import 'dart:async';

import 'package:bingo_player/src/core/localization/ethiopian_date_time.dart';
import 'package:bingo_player/src/core/network/api_provider.dart';
import 'package:bingo_player/src/core/config/app_config.dart';
import 'package:bingo_player/src/core/platform/external_link.dart';
import 'package:bingo_player/src/core/platform/player_notifications.dart';
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
    this.onSupport,
    this.onMessages,
    this.onGameHistory,
    this.onDeposit,
    this.onWithdraw,
    this.onTransactions,
  });

  final VoidCallback? onSupport;
  final VoidCallback? onMessages;
  final VoidCallback? onGameHistory;
  final VoidCallback? onDeposit;
  final VoidCallback? onWithdraw;
  final VoidCallback? onTransactions;

  @override
  ConsumerState<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends ConsumerState<ProfileScreen> {
  UserProfile? _serverProfile;
  bool _isRefreshing = true;
  bool _hasRefreshError = false;
  Future<PlayerAppContent>? _contentFuture;
  Future<List<PlayerAnnouncement>>? _bannerSlidesFuture;
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
      _bannerSlidesFuture = ref
          .read(playerContentApiProvider)
          .getBannerSlides();
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
    final apiBaseUrl = ref.watch(appConfigProvider).apiBaseUrl;
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
                            width: 132,
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
                                Text(
                                  localizations.withdrawableBalance,
                                  textAlign: TextAlign.center,
                                  style: const TextStyle(fontSize: 10),
                                ),
                                const SizedBox(height: 6),
                                const Divider(height: 1),
                                const SizedBox(height: 6),
                                Text(
                                  balance.promotionalBalance.toStringAsFixed(2),
                                  style: TextStyle(
                                    color: Theme.of(
                                      context,
                                    ).colorScheme.primary,
                                    fontWeight: FontWeight.w800,
                                  ),
                                ),
                                Text(
                                  localizations.promotionalBalanceLabel,
                                  textAlign: TextAlign.center,
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis,
                                  style: const TextStyle(fontSize: 10),
                                ),
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
                    final hasContactInfo = [
                      content.telegramUrl,
                      content.tiktokUrl,
                      content.termsUrl,
                      content.supportPhoneNumbers,
                    ].any((value) => value != null && value.isNotEmpty);
                    return FutureBuilder<List<PlayerAnnouncement>>(
                      future: _bannerSlidesFuture,
                      builder: (context, announcementsSnapshot) {
                        final announcements =
                            announcementsSnapshot.data ?? const [];
                        if (!hasContactInfo &&
                            (title == null || title.isEmpty) &&
                            (body == null || body.isEmpty) &&
                            (content.bannerImageUrl == null ||
                                content.bannerImageUrl!.isEmpty) &&
                            announcements.isEmpty) {
                          return const SizedBox.shrink();
                        }
                        return Column(
                          children: [
                            if ((title?.isNotEmpty ?? false) ||
                                (body?.isNotEmpty ?? false) ||
                                (content.bannerImageUrl?.isNotEmpty ?? false) ||
                                announcements.isNotEmpty)
                              _ProfileBannerCarousel(
                                content: content,
                                slides: announcements,
                                apiBaseUrl: apiBaseUrl,
                                accessToken: session.accessToken,
                                isAmharic: isAmharic,
                              ),
                            if (hasContactInfo) ...[
                              const SizedBox(height: 12),
                              SectionCard(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      localizations.contactUs,
                                      style: Theme.of(
                                        context,
                                      ).textTheme.titleMedium,
                                    ),
                                    if (content.telegramUrl?.isNotEmpty ??
                                        false)
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
                                        leading: const Icon(
                                          Icons.phone_outlined,
                                        ),
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
                          ],
                        );
                      },
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
                      onChanged: (enabled) async {
                        if (enabled) {
                          final granted =
                              await PlayerNotifications.requestPermission();
                          await preferences.setNotifications(granted);
                        } else {
                          await preferences.setNotifications(false);
                        }
                      },
                    ),
                    if (preferences.notificationsEnabled)
                      Padding(
                        padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                        child: DropdownButtonFormField<String>(
                          initialValue: preferences.notificationMode,
                          decoration: InputDecoration(
                            labelText: localizations.notificationFrequencyTitle,
                            helperText:
                                localizations.notificationFrequencyDescription,
                          ),
                          items: [
                            DropdownMenuItem(
                              value: 'my_card',
                              child: Text(localizations.notificationMyCard),
                            ),
                            DropdownMenuItem(
                              value: 'every_call',
                              child: Text(localizations.notificationEveryCall),
                            ),
                            DropdownMenuItem(
                              value: 'win',
                              child: Text(localizations.notificationWin),
                            ),
                          ],
                          onChanged: (value) {
                            if (value != null) {
                              preferences.setNotificationMode(value);
                            }
                          },
                        ),
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
              if (widget.onSupport != null ||
                  widget.onMessages != null ||
                  widget.onGameHistory != null ||
                  widget.onDeposit != null ||
                  widget.onWithdraw != null ||
                  widget.onTransactions != null) ...[
                SectionCard(
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  child: Column(
                    children: [
                      if (widget.onDeposit != null)
                        _shortcut(
                          Icons.south_west_rounded,
                          localizations.walletDepositsTab,
                          widget.onDeposit!,
                        ),
                      if (widget.onWithdraw != null)
                        _shortcut(
                          Icons.north_east_rounded,
                          'Withdraw',
                          widget.onWithdraw!,
                        ),
                      if (widget.onTransactions != null)
                        _shortcut(
                          Icons.receipt_long_outlined,
                          localizations.walletTransactionsTab,
                          widget.onTransactions!,
                        ),
                      if (widget.onGameHistory != null)
                        _shortcut(
                          Icons.sports_esports_outlined,
                          localizations.gameHistoryTitle,
                          widget.onGameHistory!,
                        ),
                      if (widget.onMessages != null)
                        _shortcut(
                          Icons.mail_outline_rounded,
                          localizations.messagesTitle,
                          widget.onMessages!,
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

  Widget _shortcut(IconData icon, String label, VoidCallback onTap) => ListTile(
    leading: Icon(icon, color: Theme.of(context).colorScheme.primary),
    title: Text(label),
    trailing: const Icon(Icons.chevron_right_rounded),
    onTap: onTap,
  );
}

class _ProfileBannerCarousel extends StatefulWidget {
  const _ProfileBannerCarousel({
    required this.content,
    required this.slides,
    required this.isAmharic,
    required this.apiBaseUrl,
    required this.accessToken,
  });

  final PlayerAppContent content;
  final List<PlayerAnnouncement> slides;
  final bool isAmharic;
  final String apiBaseUrl;
  final String accessToken;

  @override
  State<_ProfileBannerCarousel> createState() => _ProfileBannerCarouselState();
}

class _ProfileBannerCarouselState extends State<_ProfileBannerCarousel> {
  final PageController _controller = PageController();
  Timer? _timer;
  int _index = 0;

  bool get _hasConfiguredBanner =>
      (widget.content.bannerTitleEn?.isNotEmpty ?? false) ||
      (widget.content.bannerTitleAm?.isNotEmpty ?? false) ||
      (widget.content.bannerBodyEn?.isNotEmpty ?? false) ||
      (widget.content.bannerBodyAm?.isNotEmpty ?? false) ||
      (widget.content.bannerImageUrl?.isNotEmpty ?? false);

  int get _slideCount => (_hasConfiguredBanner ? 1 : 0) + widget.slides.length;

  @override
  void initState() {
    super.initState();
    _syncTimer();
  }

  @override
  void didUpdateWidget(covariant _ProfileBannerCarousel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (_index >= _slideCount) {
      _index = 0;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && _controller.hasClients) _controller.jumpToPage(0);
      });
    }
    _syncTimer();
  }

  void _syncTimer() {
    _timer?.cancel();
    if (_slideCount > 1) {
      _timer = Timer.periodic(const Duration(seconds: 6), (_) {
        if (!_controller.hasClients || !mounted) return;
        final next = (_index + 1) % _slideCount;
        _controller.animateToPage(
          next,
          duration: const Duration(milliseconds: 350),
          curve: Curves.easeOut,
        );
      });
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_slideCount == 0) return const SizedBox.shrink();
    final title =
        widget.isAmharic && (widget.content.bannerTitleAm?.isNotEmpty ?? false)
        ? widget.content.bannerTitleAm!
        : widget.content.bannerTitleEn;
    final body =
        widget.isAmharic && (widget.content.bannerBodyAm?.isNotEmpty ?? false)
        ? widget.content.bannerBodyAm!
        : widget.content.bannerBodyEn;
    return Column(
      children: [
        SizedBox(
          height: (widget.content.bannerImageUrl?.isNotEmpty ?? false)
              ? 280
              : 180,
          child: PageView.builder(
            controller: _controller,
            itemCount: _slideCount,
            onPageChanged: (index) => setState(() => _index = index),
            itemBuilder: (context, index) {
              final hasBanner = _hasConfiguredBanner;
              if (hasBanner && index == 0) {
                return _slideCard(
                  context,
                  title: title ?? '',
                  body: body ?? '',
                  imageUrl: widget.content.bannerImageUrl,
                );
              }
              final announcement = widget.slides[index - (hasBanner ? 1 : 0)];
              return _slideCard(
                context,
                title:
                    widget.isAmharic &&
                        (announcement.titleAm?.isNotEmpty ?? false)
                    ? announcement.titleAm!
                    : announcement.titleEn,
                body:
                    widget.isAmharic &&
                        (announcement.bodyAm?.isNotEmpty ?? false)
                    ? announcement.bodyAm!
                    : announcement.bodyEn,
                imageUrl: announcement.imageUrl,
              );
            },
          ),
        ),
        if (_slideCount > 1)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                for (var index = 0; index < _slideCount; index++)
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 180),
                    width: index == _index ? 18 : 6,
                    height: 6,
                    margin: const EdgeInsets.symmetric(horizontal: 3),
                    decoration: BoxDecoration(
                      color: index == _index
                          ? Theme.of(context).colorScheme.primary
                          : Theme.of(context).colorScheme.outlineVariant,
                      borderRadius: BorderRadius.circular(6),
                    ),
                  ),
              ],
            ),
          ),
      ],
    );
  }

  Widget _slideCard(
    BuildContext context, {
    required String title,
    required String body,
    String? imageUrl,
  }) => SectionCard(
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (imageUrl?.isNotEmpty ?? false)
          ClipRRect(
            borderRadius: BorderRadius.circular(14),
            child: Image.network(
              imageUrl!.startsWith('/')
                  ? Uri.parse(widget.apiBaseUrl).resolve(imageUrl).toString()
                  : imageUrl,
              headers: {'Authorization': 'Bearer ${widget.accessToken}'},
              width: double.infinity,
              height: 96,
              fit: BoxFit.cover,
              errorBuilder: (_, _, _) => const SizedBox.shrink(),
            ),
          ),
        if (title.isNotEmpty)
          Padding(
            padding: EdgeInsets.only(
              top: (imageUrl?.isNotEmpty ?? false) ? 8 : 0,
            ),
            child: Text(
              title,
              style: Theme.of(context).textTheme.titleLarge,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        if (body.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(top: 6),
            child: Text(body, maxLines: 3, overflow: TextOverflow.ellipsis),
          ),
      ],
    ),
  );
}
