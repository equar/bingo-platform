import 'package:bingo_player/src/core/platform/player_notifications.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('bingo_player/notifications');

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('returns the Android notification permission decision', () async {
    var granted = true;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          expect(call.method, 'requestPermission');
          return granted;
        });

    expect(await PlayerNotifications.requestPermission(), true);
    granted = false;
    expect(await PlayerNotifications.requestPermission(), false);
  });

  test('unsupported platforms leave local alerts disabled', () async {
    expect(await PlayerNotifications.requestPermission(), false);
    await PlayerNotifications.show(
      title: 'Bingo',
      body: 'Number 7 was called.',
      id: 7,
    );
  });

  test('sends the localized alert payload and stops native audio', () async {
    final calls = <MethodCall>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call);
          return true;
        });

    await PlayerNotifications.show(
      title: 'Bingo game',
      body: 'Number 7 was called.',
      id: 23,
    );
    await PlayerNotifications.stopSound();

    expect(calls.map((call) => call.method), ['show', 'stopSound']);
    expect(calls.first.arguments, {
      'title': 'Bingo game',
      'body': 'Number 7 was called.',
      'id': 23,
    });
  });
}
