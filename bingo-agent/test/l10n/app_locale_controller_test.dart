import 'package:bingo_agent/src/core/localization/app_locale_controller.dart';
import 'package:bingo_agent/src/core/localization/language_selector.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  test('restores and persists the selected language', () async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString('agent.locale', 'am');

    final controller = AppLocaleController(preferences: preferences);
    await controller.ready;
    expect(controller.state, const Locale('am'));

    await controller.setLocale(const Locale('en'));
    expect(preferences.getString('agent.locale'), 'en');
    expect(controller.state, const Locale('en'));
    controller.dispose();

    final relaunchedController = AppLocaleController(preferences: preferences);
    await relaunchedController.ready;
    expect(relaunchedController.state, const Locale('en'));
    relaunchedController.dispose();
  });

  test('falls back to English for an unsupported saved language', () async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString('agent.locale', 'fr');
    final controller = AppLocaleController(preferences: preferences);
    await controller.ready;
    expect(controller.state, const Locale('en'));
    controller.dispose();
  });

  testWidgets('switches language without losing form input', (tester) async {
    await tester.pumpWidget(ProviderScope(child: const _LocalizedTestApp()));
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const ValueKey('locale-test-input')),
      'keep this',
    );
    await tester.tap(find.byKey(const ValueKey('agent-language-selector')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('አማርኛ').last);
    await tester.pumpAndSettle();

    expect(find.text('ቢንጎ ኤጀንት'), findsOneWidget);
    expect(
      tester
          .widget<TextField>(find.byKey(const ValueKey('locale-test-input')))
          .controller!
          .text,
      'keep this',
    );
  });
}

class _LocalizedTestApp extends ConsumerWidget {
  const _LocalizedTestApp();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return MaterialApp(
      locale: ref.watch(appLocaleControllerProvider),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: const _LocalizedTestScreen(),
    );
  }
}

class _LocalizedTestScreen extends StatefulWidget {
  const _LocalizedTestScreen();

  @override
  State<_LocalizedTestScreen> createState() => _LocalizedTestScreenState();
}

class _LocalizedTestScreenState extends State<_LocalizedTestScreen> {
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Column(
        children: [
          const LanguageSelector(),
          Text(AppLocalizations.of(context)!.appTitle),
          TextField(
            key: const ValueKey('locale-test-input'),
            controller: _controller,
          ),
        ],
      ),
    );
  }
}
