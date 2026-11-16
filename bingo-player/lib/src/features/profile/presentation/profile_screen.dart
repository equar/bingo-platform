import 'dart:async';

import 'package:bingo_player/src/core/localization/ethiopian_date_time.dart';
import 'package:bingo_player/src/core/network/api_provider.dart';
import 'package:bingo_player/src/core/network/api_error.dart';
import 'package:bingo_player/src/core/config/app_config.dart';
import 'package:bingo_player/src/core/platform/external_link.dart';
import 'package:bingo_player/src/core/platform/player_notifications.dart';
import 'package:bingo_player/src/core/storage/player_preferences.dart';
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
  const ProfileScreen({super.key, this.onOpenMenu});

  final VoidCallback? onOpenMenu;

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

  Future<void> _logout() async {
    try {
      await ref.read(authRepositoryProvider).logout();
      await ref.read(authSessionControllerProvider.notifier).clearSession();
    } catch (error) {
      if (!mounted) return;
      final message = normalizeApiError(
        error,
      ).userMessage(AppLocalizations.of(context)!);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(message)));
    }
  }

  Future<void> _showChangePinDialog() async {
    final localizations = AppLocalizations.of(context)!;
    final formKey = GlobalKey<FormState>();
    final currentPinController = TextEditingController();
    final newPinController = TextEditingController();
    final confirmPinController = TextEditingController();
    var saving = false;
    var changed = false;
    String? errorMessage;

    await showDialog<void>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text(localizations.changePinTitle),
          content: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: SingleChildScrollView(
              child: Form(
                key: formKey,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    TextFormField(
                      key: const ValueKey('profile-current-pin-field'),
                      controller: currentPinController,
                      obscureText: true,
                      keyboardType: TextInputType.number,
                      maxLength: 6,
                      enabled: !saving,
                      decoration: InputDecoration(
                        labelText: localizations.currentPinLabel,
                      ),
                      validator: (value) => value == null || value.isEmpty
                          ? localizations.requiredField
                          : RegExp(r'^[0-9]{6}$').hasMatch(value)
                          ? null
                          : localizations.passwordTooShort,
                    ),
                    TextFormField(
                      key: const ValueKey('profile-new-pin-field'),
                      controller: newPinController,
                      obscureText: true,
                      keyboardType: TextInputType.number,
                      maxLength: 6,
                      enabled: !saving,
                      decoration: InputDecoration(
                        labelText: localizations.newPinLabel,
                      ),
                      validator: (value) => value == null || value.isEmpty
                          ? localizations.requiredField
                          : RegExp(r'^[0-9]{6}$').hasMatch(value)
                          ? null
                          : localizations.passwordTooShort,
                    ),
                    TextFormField(
                      key: const ValueKey('profile-confirm-pin-field'),
                      controller: confirmPinController,
                      obscureText: true,
                      keyboardType: TextInputType.number,
                      maxLength: 6,
                      enabled: !saving,
                      textInputAction: TextInputAction.done,
                      decoration: InputDecoration(
                        labelText: localizations.confirmNewPinLabel,
                      ),
                      validator: (value) {
                        if (value == null || value.isEmpty) {
                          return localizations.requiredField;
                        }
                        if (!RegExp(r'^[0-9]{6}$').hasMatch(value)) {
                          return localizations.passwordTooShort;
                        }
                        return value == newPinController.text
                            ? null
                            : localizations.pinConfirmationMismatch;
                      },
                    ),
                    if (errorMessage != null) ...[
                      const SizedBox(height: 8),
                      Text(
                        errorMessage!,
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ),
          actions: [
            TextButton(
              onPressed: saving
                  ? null
                  : () => Navigator.of(dialogContext).pop(),
              child: Text(MaterialLocalizations.of(context).cancelButtonLabel),
            ),
            FilledButton(
              key: const ValueKey('profile-change-pin-submit'),
              onPressed: saving
                  ? null
                  : () async {
                      if (!formKey.currentState!.validate()) return;
                      setDialogState(() {
                        saving = true;
                        errorMessage = null;
                      });
                      try {
                        await ref
                            .read(authRepositoryProvider)
                            .changePin(
                              ChangePlayerPinRequest(
                                currentPin: currentPinController.text,
                                newPin: newPinController.text,
                              ),
                            );
                        changed = true;
                        if (dialogContext.mounted) {
                          Navigator.of(dialogContext).pop();
                        }
                      } catch (error) {
                        if (dialogContext.mounted) {
                          setDialogState(() {
                            errorMessage = normalizeApiError(
                              error,
                            ).userMessage(localizations);
                          });
                        }
                      } finally {
                        if (dialogContext.mounted) {
                          setDialogState(() => saving = false);
                        }
                      }
                    },
              child: saving
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Text(localizations.changePinAction),
            ),
          ],
        ),
      ),
    );

    currentPinController.dispose();
    newPinController.dispose();
    confirmPinController.dispose();
    if (changed && mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(localizations.pinChanged)));
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
        leading: widget.onOpenMenu == null
            ? null
            : IconButton(
                key: const ValueKey('profile-menu'),
                tooltip: localizations.homeMenu,
                onPressed: widget.onOpenMenu,
                icon: const Icon(Icons.menu_rounded),
              ),
        actions: [
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
                  ],
                ),
              ),
              const SizedBox(height: 16),
              SectionCard(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: ListTile(
                  key: const ValueKey('profile-change-pin'),
                  leading: const Icon(Icons.lock_reset_rounded),
                  title: Text(localizations.changePinTitle),
                  subtitle: Text(localizations.changePinDescription),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: _showChangePinDialog,
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
                    const SizedBox(height: 12),
                    const CalendarPreferenceSelector(),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              OutlinedButton.icon(
                icon: const Icon(Icons.logout_rounded),
                onPressed: _logout,
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
}

class CalendarPreferenceSelector extends ConsumerStatefulWidget {
  const CalendarPreferenceSelector({super.key});

  @override
  ConsumerState<CalendarPreferenceSelector> createState() =>
      _CalendarPreferenceSelectorState();
}

class _CalendarPreferenceSelectorState
    extends ConsumerState<CalendarPreferenceSelector> {
  bool _saving = false;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final user = ref.watch(authSessionControllerProvider).session?.user;
    final calendar = user?.preferredCalendar ?? 'AUTO';
    final language =
        user?.preferredLanguage ?? Localizations.localeOf(context).languageCode;
    final resolvedEthiopian =
        calendar == 'ETHIOPIAN' ||
        (calendar == 'AUTO' && (language == 'am' || language == 'ti'));
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          l.profileCalendarPreference,
          style: Theme.of(context).textTheme.titleSmall,
        ),
        DropdownButtonFormField<String>(
          key: ValueKey('$calendar-$_saving'),
          initialValue: calendar,
          items: [
            DropdownMenuItem(value: 'AUTO', child: Text(l.calendarAutomatic)),
            DropdownMenuItem(
              value: 'ETHIOPIAN',
              child: Text(l.calendarEthiopian),
            ),
            DropdownMenuItem(
              value: 'GREGORIAN',
              child: Text(l.calendarGregorian),
            ),
          ],
          onChanged: _saving
              ? null
              : (value) {
                  if (value != null) _save(value);
                },
        ),
        const SizedBox(height: 4),
        Text(
          calendar == 'AUTO'
              ? (resolvedEthiopian
                    ? l.calendarAutoEthiopian
                    : l.calendarAutoGregorian)
              : (_saving ? l.calendarPreferenceSaving : ''),
        ),
      ],
    );
  }

  Future<void> _save(String value) async {
    setState(() => _saving = true);
    try {
      final currentSession = ref.read(authSessionControllerProvider).session;
      if (currentSession == null) return;
      final user = await ref
          .read(authRepositoryProvider)
          .updatePreferences(preferredCalendar: value);
      final applied = await ref
          .read(authSessionControllerProvider.notifier)
          .updateUser(user, expectedAccessToken: currentSession.accessToken);
      if (!applied) return;
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)!.calendarPreferenceSaved,
            ),
          ),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)!.calendarPreferenceSaveFailed,
            ),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }
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
