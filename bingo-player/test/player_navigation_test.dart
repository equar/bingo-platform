import 'dart:io';
import 'dart:ui' as ui;

import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/core/models/page_result.dart';
import 'package:bingo_player/src/core/theme/app_theme.dart';
import 'package:bingo_player/src/core/config/app_config.dart';
import 'package:bingo_player/src/core/network/api_provider.dart';
import 'package:bingo_player/src/features/auth/data/auth_repository.dart';
import 'package:bingo_player/src/core/storage/session_storage.dart';
import 'package:bingo_player/src/features/auth/data/auth_session_controller.dart';
import 'package:bingo_player/src/features/auth/presentation/auth_screen.dart';
import 'package:bingo_player/src/features/game/application/live_game_controller.dart';
import 'package:bingo_player/src/features/game/application/live_game_transport.dart';
import 'package:bingo_player/src/features/game/data/game_repository.dart';
import 'package:bingo_player/src/features/game/domain/live_game_models.dart';
import 'package:bingo_player/src/features/game/presentation/session_detail_screen.dart';
import 'package:bingo_player/src/features/home/presentation/home_screen.dart';
import 'package:bingo_player/src/features/home/presentation/home_shell.dart';
import 'package:bingo_player/src/features/lobby/domain/lobby_models.dart';
import 'package:bingo_player/src/features/lobby/presentation/lobby_screen.dart';
import 'package:bingo_player/src/features/player_content/data/player_content_api.dart';
import 'package:bingo_player/src/features/player_content/domain/player_content_models.dart';
import 'package:bingo_player/src/features/wallet/data/wallet_api.dart';
import 'package:bingo_player/src/features/wallet/data/wallet_repository.dart';
import 'package:bingo_player/src/features/wallet/domain/wallet_models.dart';
import 'package:dio/dio.dart';
import 'package:bingo_player/src/features/wallet/presentation/wallet_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'responsive_ui_test.dart' as responsive;
import 'features/auth/auth_screen_test.dart' as auth;

const previewDir = String.fromEnvironment('UI_PREVIEW_DIR');
final room = BingoRoom.fromJson({
  'id': 7,
  'name': 'Addis Bingo',
  'description': 'A classic game of Bingo.',
  'ticketPrice': 10,
  'currency': 'ETB',
  'active': true,
  'sessionCapacity': 100,
  'scheduleRule': 'Every 15 minutes',
  'timezone': 'Africa/Addis_Ababa',
});

GameSession _session({String state = 'OPEN'}) => GameSession(
  id: 7,
  roomId: 7,
  roomName: room.name,
  sessionName: 'Friday Night',
  state: state,
  scheduledStartAt: DateTime.utc(2030),
  ticketPrice: 10,
  currency: 'ETB',
  sessionCapacity: 100,
  ticketCount: 0,
  drawCount: 0,
  createdAt: DateTime.utc(2029),
  updatedAt: DateTime.utc(2029),
);

final session = _session();

Widget app({
  bool login = false,
  double scale = 1,
  Locale locale = const Locale('en'),
  List<GameSession>? sessions,
}) => ProviderScope(
  overrides: [
    activeSessionsProvider.overrideWith((ref) async => sessions ?? [session]),
    liveGameControllerProvider(
      session.id,
    ).overrideWith((ref) => _liveGameController()),
    playerContentApiProvider.overrideWithValue(_UnusedPlayerContentApi()),
    walletRepositoryProvider.overrideWithValue(_UnusedWalletRepository()),
    sessionStorageProvider.overrideWithValue(auth.MemoryStorage()),
    authRepositoryProvider.overrideWithValue(auth.TestAuthRepository()),
  ],
  child: RepaintBoundary(
    key: const ValueKey('ux-preview'),
    child: MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: buildAppTheme(),
      locale: locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(scale)),
        child: child!,
      ),
      home: login ? const AuthScreen(sessionExpired: false) : const HomeShell(),
    ),
  ),
);

Future<void> tab(WidgetTester tester, String label) async {
  final bar = find.byType(NavigationBar);
  await tester.tap(find.descendant(of: bar, matching: find.text(label)));
  await tester.pumpAndSettle();
}

Future<void> _selectUpcomingSessionTab(WidgetTester tester) async {
  final lobby = find.byType(LobbyScreen);
  final context = tester.element(lobby);
  final label = AppLocalizations.of(context)!.upcomingGamesTab;
  await tester.ensureVisible(find.text(label));
  await tester.tap(find.text(label));
  await tester.pumpAndSettle();
}

Future<void> capture(
  WidgetTester tester,
  String name, {
  Key boundaryKey = const ValueKey('ux-preview'),
}) async {
  if (previewDir.isEmpty) return;
  final images = find.byType(Image);
  if (images.evaluate().isNotEmpty) {
    final artwork = tester.widget<Image>(images.first);
    await tester.runAsync(
      () => precacheImage(artwork.image, tester.element(images.first)),
    );
  }
  await tester.pumpAndSettle();
  final boundary = tester.renderObject<RenderRepaintBoundary>(
    find.byKey(boundaryKey),
  );
  await tester.runAsync(() async {
    final image = await boundary.toImage(pixelRatio: 1.5);
    final data = await image.toByteData(format: ui.ImageByteFormat.png);
    await Directory(previewDir).create(recursive: true);
    await File(
      '$previewDir/$name.png',
    ).writeAsBytes(data!.buffer.asUint8List());
    image.dispose();
  });
}

void main() {
  setUpAll(() async {
    if (previewDir.isEmpty) return;
    const textFont = String.fromEnvironment('UI_PREVIEW_TEXT_FONT');
    const iconFont = String.fromEnvironment('UI_PREVIEW_ICON_FONT');
    for (final font in [
      ('Roboto', textFont),
      ('Ahem', textFont),
      ('MaterialIcons', iconFont),
    ]) {
      if (font.$2.isEmpty) continue;
      final loader = FontLoader(font.$1);
      loader.addFont(
        File(
          font.$2,
        ).readAsBytes().then((bytes) => ByteData.sublistView(bytes)),
      );
      await loader.load();
    }
  });

  testWidgets(
    'session navigation stays available and returns to its previous detail',
    (tester) async {
      responsive.viewport(tester, const Size(390, 844));
      await tester.pumpWidget(app(sessions: [_session(state: 'STARTED')]));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('home-play-bingo')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('live-game-7')));
      await tester.pumpAndSettle();
      expect(find.byType(NavigationBar), findsOneWidget);
      expect(find.byType(SessionDetailScreen), findsOneWidget);
      await tab(tester, 'Profile');
      await tab(tester, 'Games');
      expect(find.byType(SessionDetailScreen), findsOneWidget);
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('live-game-7')), findsOneWidget);
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.byType(HomeScreen), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('wallet keeps the selected history tab when switching sections', (
    tester,
  ) async {
    responsive.viewport(tester, const Size(390, 844));
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          playerContentApiProvider.overrideWithValue(_UnusedPlayerContentApi()),
          walletRepositoryProvider.overrideWithValue(_UnusedWalletRepository()),
        ],
        child: responsive.harness(),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('home-menu')));
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('navigation-menu-transactions')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Deposits'));
    await tester.pumpAndSettle();
    final before = tester.state(find.byType(WalletScreen));
    await tab(tester, 'Profile');
    await tab(tester, 'Home');
    expect(identical(before, tester.state(find.byType(WalletScreen))), isTrue);
    final controller = tester.widget<TabBar>(find.byType(TabBar)).controller!;
    expect(controller.index, 1);
    expect(tester.takeException(), isNull);
  });

  for (final size in [const Size(320, 568), const Size(390, 844)]) {
    testWidgets('login and sessions fit ${size.width} with large text', (
      tester,
    ) async {
      responsive.viewport(tester, size);
      await tester.pumpWidget(
        app(login: true, scale: 2, locale: const Locale('am')),
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(
        find.byKey(const ValueKey('auth-submit-button')),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(app(scale: 2, locale: const Locale('am')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('home-play-bingo')));
      await tester.pumpAndSettle();
      await _selectUpcomingSessionTab(tester);
      await tester.ensureVisible(
        find.byKey(const ValueKey('session-details-7')),
      );
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('phone screen previews', (tester) async {
    responsive.viewport(tester, const Size(390, 844));
    await tester.pumpWidget(app(login: true));
    await tester.pumpAndSettle();
    await capture(tester, 'login');
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    await capture(tester, 'home');
    await tester.tap(find.byKey(const ValueKey('home-play-bingo')));
    await tester.pumpAndSettle();
    await capture(tester, 'games');
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpWidget(responsive.harness());
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('home-menu')));
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('navigation-menu-transactions')),
    );
    await tester.pumpAndSettle();
    await capture(tester, 'wallet', boundaryKey: const ValueKey('preview'));
    expect(tester.takeException(), isNull);
  });
}

LiveGameController _liveGameController() => LiveGameController(
  sessionId: session.id,
  repository: _UnusedLiveGameRepository(),
  readAuthSession: () => const AuthSessionState(isLoading: true),
  clearAuthSession: ({String? reason}) async {},
  appConfig: const AppConfig(
    apiBaseUrl: 'http://127.0.0.1:8080',
    apiBaseUrlLabel: 'API_BASE_URL',
  ),
  transport: _UnusedLiveGameTransport(),
);

class _UnusedLiveGameRepository implements LiveGameRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _UnusedLiveGameTransport implements LiveGameTransport {
  @override
  bool get isConnected => false;

  @override
  Future<void> connect({
    required Uri uri,
    required String accessToken,
    required int sessionId,
    required void Function(LiveDrawEvent event) onDraw,
    required void Function(Object error) onError,
    required void Function() onDisconnected,
  }) async {}

  @override
  Future<void> disconnect() async {}
}

class _UnusedPlayerContentApi extends PlayerContentApi {
  _UnusedPlayerContentApi() : super(Dio());

  @override
  Future<PlayerAppContent> getContent() async => const PlayerAppContent();

  @override
  Future<List<PlayerAnnouncement>> getBannerSlides() async => const [];
}

class _UnusedWalletRepository extends WalletRepository {
  _UnusedWalletRepository() : super(WalletApi(Dio()));

  @override
  Future<WalletBalance> getBalance() async => WalletBalance.fromJson({
    'walletId': 1,
    'currency': 'ETB',
    'availableBalance': 1200,
    'promotionalBalance': 20,
    'totalBalance': 1220,
  });

  @override
  Future<PageResult<WalletOwnTransfer>> getOwnTransfers({
    required int page,
    required int size,
  }) async => const PageResult(
    content: [],
    page: 0,
    size: 10,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  );
}
