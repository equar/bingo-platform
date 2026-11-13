import 'package:bingo_player/src/features/game/domain/live_game_models.dart';
import 'package:bingo_player/src/features/game/presentation/live_call_panel.dart';
import 'package:bingo_player/l10n/app_localizations.dart';
import 'package:flutter/services.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

LiveDrawEvent _draw(int sequence) => LiveDrawEvent(
  sessionId: 42,
  sequenceNo: sequence,
  numberDrawn: sequence,
  drawnAt: DateTime.utc(2026, 1, 1).add(Duration(seconds: sequence)),
  id: sequence,
  operationId: 'draw-$sequence',
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('bingo_player/notifications');

  testWidgets(
    'sounds the first live call and resumes after reconnect without replaying history',
    (tester) async {
      final sounds = <bool>[];
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (
        call,
      ) async {
        if (call.method == 'playSound') {
          sounds.add((call.arguments as Map)['win'] == true);
        }
        return null;
      });
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          channel,
          null,
        ),
      );

      Widget panel({
        required List<LiveDrawEvent> draws,
        required bool connected,
        bool soundEnabled = true,
      }) => MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: LiveCallPanel(
            draws: draws,
            isLive: true,
            connected: connected,
            finished: false,
            soundEnabled: soundEnabled,
          ),
        ),
      );

      await tester.pumpWidget(panel(draws: const [], connected: true));
      await tester.pumpWidget(panel(draws: [_draw(1)], connected: true));
      await tester.pump();
      expect(sounds, [false]);

      await tester.pumpWidget(panel(draws: [_draw(1)], connected: false));
      await tester.pumpWidget(
        panel(draws: [_draw(1), _draw(2)], connected: false),
      );
      await tester.pumpWidget(
        panel(draws: [_draw(1), _draw(2)], connected: true),
      );
      await tester.pumpWidget(
        panel(draws: [_draw(1), _draw(2), _draw(3)], connected: true),
      );
      await tester.pump();
      expect(sounds, [false, false]);
      await tester.pumpWidget(
        panel(
          draws: [_draw(1), _draw(2), _draw(3), _draw(4)],
          connected: true,
          soundEnabled: false,
        ),
      );
      expect(sounds, [false, false]);
      await tester.pumpWidget(
        panel(
          draws: [_draw(1), _draw(2), _draw(3), _draw(4), _draw(5)],
          connected: true,
        ),
      );
      await tester.pump();
      expect(sounds, [false, false, false]);
    },
  );

  testWidgets('alerts only on new connected calls after reconnection', (
    tester,
  ) async {
    final alerts = <Map<Object?, Object?>>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (
      call,
    ) async {
      if (call.method == 'show') {
        alerts.add(Map<Object?, Object?>.from(call.arguments as Map));
      }
      return true;
    });
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        channel,
        null,
      ),
    );

    Widget panel({
      required List<LiveDrawEvent> draws,
      required bool connected,
    }) => MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(
        body: LiveCallPanel(
          draws: draws,
          isLive: true,
          connected: connected,
          finished: false,
          notificationsEnabled: true,
        ),
      ),
    );

    await tester.pumpWidget(panel(draws: [_draw(1)], connected: true));
    await tester.pumpWidget(panel(draws: [_draw(1)], connected: false));
    await tester.pumpWidget(
      panel(draws: [_draw(1), _draw(2)], connected: false),
    );
    await tester.pumpWidget(
      panel(draws: [_draw(1), _draw(2)], connected: true),
    );
    expect(alerts, isEmpty);

    await tester.pumpWidget(
      panel(draws: [_draw(1), _draw(2), _draw(3)], connected: true),
    );
    await tester.pump();
    expect(alerts, hasLength(1));
    expect(alerts.single['body'], 'Bingo number 3 was called.');
  });
}
