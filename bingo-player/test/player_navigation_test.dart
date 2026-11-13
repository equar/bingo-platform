import 'dart:io';
import 'dart:ui' as ui;

import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/core/theme/app_theme.dart';
import 'package:bingo_player/src/features/auth/data/auth_repository.dart';
import 'package:bingo_player/src/core/storage/session_storage.dart';
import 'package:bingo_player/src/features/auth/presentation/auth_screen.dart';
import 'package:bingo_player/src/features/home/presentation/home_screen.dart';
import 'package:bingo_player/src/features/home/presentation/home_shell.dart';
import 'package:bingo_player/src/features/lobby/domain/lobby_models.dart';
import 'package:bingo_player/src/features/lobby/presentation/lobby_screen.dart';
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

Widget app({
  bool login = false,
  double scale = 1,
  Locale locale = const Locale('en'),
}) => ProviderScope(
  overrides: [
    lobbyRoomsProvider.overrideWith((ref) async => [room]),
    roomDetailsProvider(7).overrideWith((ref) async => room),
    roomSessionsProvider(7).overrideWith((ref) async => []),
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
    'room navigation stays available and returns to its previous detail',
    (tester) async {
      responsive.viewport(tester, const Size(390, 844));
      await tester.pumpWidget(app());
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('home-play-bingo')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('room-details-7')));
      await tester.pumpAndSettle();
      expect(find.byType(NavigationBar), findsOneWidget);
      expect(find.byKey(const ValueKey('room-detail-name')), findsOneWidget);
      await tab(tester, 'Home');
      expect(find.byType(HomeScreen), findsOneWidget);
      await tab(tester, 'Games');
      expect(find.byKey(const ValueKey('room-detail-name')), findsOneWidget);
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('room-details-7')), findsOneWidget);
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
    await tester.pumpWidget(responsive.harness());
    await tester.pumpAndSettle();
    await tab(tester, 'Wallet');
    await tester.tap(find.text('Deposits'));
    await tester.pumpAndSettle();
    final before = tester.state(find.byType(WalletScreen));
    await tab(tester, 'Home');
    await tab(tester, 'Wallet');
    expect(identical(before, tester.state(find.byType(WalletScreen))), isTrue);
    final controller = tester.widget<TabBar>(find.byType(TabBar)).controller!;
    expect(controller.index, 1);
    expect(tester.takeException(), isNull);
  });

  for (final size in [const Size(320, 568), const Size(390, 844)]) {
    testWidgets('login and rooms fit ${size.width} with large text', (
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
      await tester.ensureVisible(find.byKey(const ValueKey('room-details-7')));
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
    await tab(tester, 'Games');
    await capture(tester, 'games');
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpWidget(responsive.harness());
    await tester.pumpAndSettle();
    await tab(tester, 'Wallet');
    await capture(tester, 'wallet', boundaryKey: const ValueKey('preview'));
    expect(tester.takeException(), isNull);
  });
}
