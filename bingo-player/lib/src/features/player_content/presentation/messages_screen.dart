import 'package:bingo_player/src/core/network/api_provider.dart';
import 'package:bingo_player/src/features/player_content/domain/player_content_models.dart';
import 'package:bingo_player/src/core/localization/ethiopian_date_time.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/core/storage/player_preferences.dart';

class MessagesScreen extends ConsumerStatefulWidget {
  const MessagesScreen({super.key});

  @override
  ConsumerState<MessagesScreen> createState() => _MessagesScreenState();
}

class _MessagesScreenState extends ConsumerState<MessagesScreen> {
  late Future<List<PlayerAnnouncement>> _messages;

  @override
  void initState() {
    super.initState();
    _messages = ref.read(playerContentApiProvider).getMessages();
  }

  Future<void> _refresh() async {
    setState(
      () => _messages = ref.read(playerContentApiProvider).getMessages(),
    );
    await _messages;
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(AppLocalizations.of(context)!.messagesTitle)),
    body: RefreshIndicator(
      onRefresh: _refresh,
      child: FutureBuilder<List<PlayerAnnouncement>>(
        future: _messages,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return ListView(
              children: [
                const SizedBox(height: 140),
                Center(
                  child: Text(AppLocalizations.of(context)!.messagesLoadError),
                ),
                Center(
                  child: TextButton(
                    onPressed: _refresh,
                    child: Text(AppLocalizations.of(context)!.retry),
                  ),
                ),
              ],
            );
          }
          final messages = snapshot.data ?? const [];
          if (messages.isEmpty) {
            return ListView(
              children: [
                SizedBox(height: 140),
                Center(child: Icon(Icons.mark_email_read_outlined, size: 48)),
                Center(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Text(AppLocalizations.of(context)!.messagesEmpty),
                  ),
                ),
              ],
            );
          }
          final language = Localizations.localeOf(context).languageCode;
          final highlightUnread = ref
              .watch(playerPreferencesProvider)
              .notificationsEnabled;
          return ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: messages.length,
            separatorBuilder: (_, _) => const SizedBox(height: 10),
            itemBuilder: (context, index) {
              final message = messages[index];
              final amharic = language == 'am';
              final title = amharic && (message.titleAm?.isNotEmpty ?? false)
                  ? message.titleAm!
                  : message.titleEn;
              final body = amharic && (message.bodyAm?.isNotEmpty ?? false)
                  ? message.bodyAm!
                  : message.bodyEn;
              return Card(
                child: ExpansionTile(
                  leading: Icon(
                    message.read
                        ? Icons.drafts_outlined
                        : Icons.mark_email_unread_outlined,
                  ),
                  title: Text(
                    title,
                    style: TextStyle(
                      fontWeight: message.read || !highlightUnread
                          ? FontWeight.w500
                          : FontWeight.w800,
                    ),
                  ),
                  subtitle: Text(
                    EthiopianDateTime.date(
                      message.createdAt,
                      languageCode: language,
                    ),
                  ),
                  onExpansionChanged: (expanded) {
                    if (expanded && !message.read) {
                      ref
                          .read(playerContentApiProvider)
                          .markMessageRead(message.id)
                          .then((_) => _refresh());
                    }
                  },
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                      child: Align(
                        alignment: AlignmentDirectional.centerStart,
                        child: Text(body),
                      ),
                    ),
                  ],
                ),
              );
            },
          );
        },
      ),
    ),
  );
}
