import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/features/home/presentation/home_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';

Widget _home({
  required VoidCallback onGames,
  VoidCallback? onSupport,
  double textScale = 1,
}) => MaterialApp(
  localizationsDelegates: const [
    AppLocalizations.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
  ],
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(
      context,
    ).copyWith(textScaler: TextScaler.linear(textScale)),
    child: child!,
  ),
  home: Scaffold(
    body: HomeScreen(
      onGames: onGames,
      onWallet: () {},
      onProfile: () {},
      onSupport: onSupport ?? () {},
    ),
  ),
);

void main() {
  testWidgets('Play Bingo opens the existing games flow', (tester) async {
    var opened = false;
    await tester.pumpWidget(_home(onGames: () => opened = true));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('home-play-bingo')));
    expect(opened, isTrue);
  });

  testWidgets('upcoming features are explicit and can lead to real games', (
    tester,
  ) async {
    var opened = false;
    await tester.pumpWidget(_home(onGames: () => opened = true));
    await tester.pumpAndSettle();
    expect(find.text('Coming soon'), findsNWidgets(3));
    await tester.ensureVisible(find.text('Private Game'));
    await tester.tap(find.text('Private Game'));
    await tester.pumpAndSettle();
    expect(opened, isFalse);
    expect(find.text('Browse games'), findsOneWidget);
    await tester.tap(find.text('Browse games'));
    await tester.pumpAndSettle();
    expect(opened, isTrue);
  });

  testWidgets('support remains accessible from the menu', (tester) async {
    var opened = false;
    await tester.pumpWidget(
      _home(onGames: () {}, onSupport: () => opened = true),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('home-menu')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('support-tab')));
    await tester.pumpAndSettle();
    expect(opened, isTrue);
  });

  testWidgets('home stays scrollable on narrow screens with larger text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(_home(onGames: () {}, textScale: 1.5));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Play & Win'));
    expect(tester.takeException(), isNull);
  });
}
