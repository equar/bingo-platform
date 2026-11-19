import 'package:bingo_player/src/core/models/page_result.dart';
import 'package:bingo_player/src/features/wallet/domain/wallet_models.dart';
import 'package:bingo_player/src/features/wallet/data/wallet_api.dart';
import 'package:bingo_player/src/features/wallet/data/wallet_repository.dart';
import 'package:bingo_player/src/features/wallet/presentation/wallet_screen.dart';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:bingo_player/src/core/theme/app_theme.dart';
import 'package:bingo_player/src/core/widgets/app_status_view.dart';
import 'package:bingo_player/src/features/home/presentation/home_shell.dart';
import 'package:bingo_player/src/features/lobby/presentation/lobby_screen.dart';
import 'package:bingo_player/src/features/wallet/presentation/deposit_dialog.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:dio/dio.dart';

const previewDirectory = String.fromEnvironment('UI_PREVIEW_DIR');

Widget harness({
  Widget? home,
  WalletRepository? walletRepository,
  double scale = 1,
  Locale locale = const Locale('en'),
  double keyboard = 0,
}) => ProviderScope(
  overrides: [
    if (walletRepository != null)
      walletRepositoryProvider.overrideWithValue(walletRepository),
    lobbyRoomsProvider.overrideWith((ref) async => []),
    walletBalanceProvider.overrideWith(
      (ref) async => WalletBalance.fromJson({
        'walletId': 1,
        'currency': 'ETB',
        'availableBalance': 1200,
        'promotionalBalance': 20,
        'totalBalance': 1220,
      }),
    ),
    walletTransactionsProvider.overrideWith(
      (ref, page) async => PageResult.fromJson({
        'content': [
          {
            'id': 1,
            'transactionType': 'DEPOSIT',
            'amount': 1200,
            'currency': 'ETB',
            'balanceAfter': 1220,
          },
        ],
        'page': page,
        'size': 10,
        'totalPages': 1,
        'totalElements': 1,
        'first': true,
        'last': true,
      }, WalletTransaction.fromJson),
    ),
    walletDepositsProvider.overrideWith(
      (ref, page) async => PageResult<DepositClaim>.fromJson({
        'content': [],
        'page': page,
        'size': 10,
        'totalPages': 0,
        'totalElements': 0,
        'first': true,
        'last': true,
      }, DepositClaim.fromJson),
    ),
  ],
  child: RepaintBoundary(
    key: const ValueKey('preview'),
    child: MaterialApp(
      debugShowCheckedModeBanner: false,
      locale: locale,
      theme: buildAppTheme(),
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          textScaler: TextScaler.linear(scale),
          viewInsets: EdgeInsets.only(bottom: keyboard),
        ),
        child: child!,
      ),
      home: home ?? const HomeShell(),
    ),
  ),
);

void viewport(WidgetTester tester, Size size) {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

Future<void> savePreview(WidgetTester tester, String name) async {
  if (previewDirectory.isEmpty) return;
  final images = find.byType(Image);
  if (images.evaluate().isNotEmpty) {
    final artwork = tester.widget<Image>(images.first);
    final context = tester.element(images.first);
    await tester.runAsync(() => precacheImage(artwork.image, context));
  }
  await tester.pumpAndSettle();
  final boundary = tester.renderObject<RenderRepaintBoundary>(
    find.byKey(const ValueKey('preview')),
  );
  await tester.runAsync(() async {
    final image = await boundary.toImage(pixelRatio: 1);
    final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
    await Directory(previewDirectory).create(recursive: true);
    await File(
      '$previewDirectory/$name.png',
    ).writeAsBytes(bytes!.buffer.asUint8List());
    image.dispose();
  });
}

void main() {
  setUpAll(() async {
    if (previewDirectory.isEmpty) return;
    // Optional font paths keep preview generation portable; normal tests need no local fonts.
    for (final font in [
      ('Roboto', const String.fromEnvironment('UI_PREVIEW_TEXT_FONT')),
      ('MaterialIcons', const String.fromEnvironment('UI_PREVIEW_ICON_FONT')),
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

  for (final size in [
    const Size(320, 568),
    const Size(390, 844),
    const Size(844, 390),
    const Size(1024, 768),
  ]) {
    for (final scale in [1.0, 2.0]) {
      testWidgets('home fits ${size.width}x${size.height} at ${scale}x text', (
        tester,
      ) async {
        viewport(tester, size);
        await tester.pumpWidget(harness(scale: scale));
        await tester.pumpAndSettle();
        final wide = size.width >= 840 && size.height >= 480;
        expect(
          find.byType(NavigationRail),
          wide ? findsOneWidget : findsNothing,
        );
        expect(
          find.byType(NavigationBar),
          wide ? findsNothing : findsOneWidget,
        );
        expect(tester.takeException(), isNull);
        if (scale == 1 && size.height > 390) {
          await savePreview(tester, 'home-${size.width.toInt()}');
        }
        await tester.ensureVisible(find.text('Play & Win'));
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
      });
    }
  }

  testWidgets('home uses light status icons over its navy header', (
    tester,
  ) async {
    viewport(tester, const Size(390, 680));
    tester.view.viewPadding = const FakeViewPadding(top: 24);
    addTearDown(tester.view.resetViewPadding);
    await tester.pumpWidget(harness());
    await tester.pumpAndSettle();

    final region = tester.widget<AnnotatedRegion<SystemUiOverlayStyle>>(
      find.byType(AnnotatedRegion<SystemUiOverlayStyle>),
    );
    expect(region.value.statusBarIconBrightness, Brightness.light);
    expect(region.value.statusBarColor, const Color(0xFF081D46));
    expect(
      find.byKey(const ValueKey('home-status-bar-backdrop')),
      findsOneWidget,
    );
    await tester.drag(
      find.byKey(const ValueKey('home-scroll')),
      const Offset(0, -500),
    );
    await tester.pumpAndSettle();
    expect(
      tester
          .getTopLeft(find.byKey(const ValueKey('home-header-background')))
          .dy,
      lessThan(0),
    );
    expect(
      find.byKey(const ValueKey('home-status-bar-backdrop')),
      findsOneWidget,
    );
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('Amharic home fits a narrow phone at large text', (tester) async {
    viewport(tester, const Size(320, 568));
    await tester.pumpWidget(harness(scale: 2, locale: const Locale('am')));
    await tester.pumpAndSettle();
    final context = tester.element(find.byType(HomeShell));
    await tester.ensureVisible(
      find.text(AppLocalizations.of(context)!.homePlayAndWin),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('tablet rail opens the games destination', (tester) async {
    viewport(tester, const Size(1024, 768));
    await tester.pumpWidget(harness());
    await tester.pumpAndSettle();
    await tester.tap(
      find.descendant(
        of: find.byType(NavigationRail),
        matching: find.text('Games'),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byType(LobbyScreen), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'status actions remain reachable in a short large-text viewport',
    (tester) async {
      viewport(tester, const Size(320, 240));
      var retried = false;
      await tester.pumpWidget(
        harness(
          scale: 2,
          home: Scaffold(
            body: AppStatusView(
              title: 'Connection unavailable',
              message: 'Check your connection and try again.',
              onAction: () => retried = true,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.byType(FilledButton));
      await tester.tap(find.byType(FilledButton));
      expect(retried, isTrue);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'wallet pagination and transfer actions fit a narrow large-text screen',
    (tester) async {
      viewport(tester, const Size(320, 568));
      await tester.pumpWidget(harness(scale: 2, home: const WalletScreen()));
      await tester.pumpAndSettle();
      final context = tester.element(find.byType(WalletScreen));
      final l = AppLocalizations.of(context)!;
      await tester.scrollUntilVisible(
        find.byType(TabBarView),
        200,
        scrollable: find
            .descendant(
              of: find.byType(ListView).first,
              matching: find.byType(Scrollable),
            )
            .first,
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('${l.walletPageLabel} 1/1'));
      expect(tester.takeException(), isNull);
      final transferTab = find.text(l.walletTransfersTab);
      await tester.ensureVisible(transferTab);
      await tester.tap(transferTab);
      await tester.pumpAndSettle();
      expect(find.text(l.transferHistoryTitle), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('transfer requires confirmation before submitting', (
    tester,
  ) async {
    viewport(tester, const Size(400, 800));
    final repository = _TransferConfirmationRepository();
    await tester.pumpWidget(
      harness(home: const WalletScreen(), walletRepository: repository),
    );
    await tester.pumpAndSettle();
    final context = tester.element(find.byType(WalletScreen));
    final l = AppLocalizations.of(context)!;
    await tester.ensureVisible(find.text(l.walletTransfersTab));
    await tester.tap(find.text(l.walletTransfersTab));
    await tester.pumpAndSettle();
    await tester.pump(const Duration(seconds: 1));
    expect(find.text('Your transfers'), findsOneWidget);
    expect(find.textContaining('251992030395'), findsOneWidget);
    expect(find.text('25 ETB'), findsOneWidget);
    await tester.tap(
      find.byKey(const ValueKey('wallet-open-transfer-form-button')),
    );
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField).at(0), '0992030395');
    await tester.enterText(find.byType(TextField).at(1), '50');
    await tester.tap(find.text(l.lookupRecipient));
    await tester.pumpAndSettle();
    await tester.tap(find.text(l.sendTransfer));
    await tester.pumpAndSettle();
    expect(find.byType(AlertDialog), findsOneWidget);
    expect(find.text('Mulugeta Recipient'), findsNWidgets(2));
    expect(find.text('251992030395'), findsNWidgets(2));
    expect(find.text('50 ETB'), findsOneWidget);
    expect(repository.transferCalls, 0);
    await tester.tap(find.text(l.confirm));
    await tester.pumpAndSettle();
    expect(repository.transferCalls, 1);
  });

  testWidgets('deposit form remains usable above a keyboard on a small phone', (
    tester,
  ) async {
    viewport(tester, const Size(320, 568));
    await tester.pumpWidget(
      harness(
        scale: 1.5,
        keyboard: 220,
        home: const Scaffold(body: DepositDialog(currency: 'ETB')),
      ),
    );
    await tester.pumpAndSettle();
    await tester.ensureVisible(
      find.byKey(const ValueKey('deposit-reference-field')),
    );
    await tester.enterText(
      find.byKey(const ValueKey('deposit-reference-field')),
      'REFERENCE123',
    );
    expect(tester.takeException(), isNull);
  });
}

class _TransferConfirmationRepository extends WalletRepository {
  _TransferConfirmationRepository() : super(WalletApi(Dio()));

  var transferCalls = 0;

  @override
  Future<PageResult<WalletOwnTransfer>> getOwnTransfers({
    required int page,
    required int size,
  }) async => PageResult(
    content: [
      WalletOwnTransfer(
        id: 3,
        operationId: 'wallet-transfer-3',
        recipientPhoneNumber: '251992030395',
        amount: 25,
        currency: 'ETB',
        status: 'COMPLETED',
        createdAt: DateTime.utc(2026, 11, 17, 11),
      ),
    ],
    page: 0,
    size: 10,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
  );

  @override
  Future<WalletRecipientLookup> lookupRecipient(String phoneNumber) async =>
      const WalletRecipientLookup(
        userId: 7,
        displayName: 'Mulugeta Recipient',
        normalizedPhone: '251992030395',
      );

  @override
  Future<WalletTransferResponse> submitTransfer(
    WalletTransferRequest request,
    String idempotencyKey,
  ) async {
    transferCalls++;
    throw Exception('Transfer API should not be called before confirmation.');
  }
}
