import 'dart:async';
import 'dart:convert';

import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/core/network/api_client.dart';
import 'package:bingo_app/src/features/community/presentation/daily_challenges_screen.dart';
import 'package:bingo_app/src/features/community/presentation/leaderboard_screen.dart';
import 'package:bingo_app/src/features/community/presentation/private_games_screen.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

class CommunityServer implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  bool host = true, active = true, claimed = false, visible = false;
  bool failList = false, failClaim = false, failRooms = false;
  String state = 'OPEN', alias = 'BingoStar';
  Completer<void>? holdPage, holdJoin;
  Map<String, dynamic> get game => {
    'roomId': 7,
    'sessionId': 9,
    'name': 'Friends',
    'state': state,
    'host': host,
    'active': active,
    'memberCount': 2,
    'capacity': 10,
    'scheduledStartAt': '2026-11-01T12:00:00Z',
  };
  Map<String, dynamic> get session => {
    'id': 9,
    'roomId': 7,
    'roomName': 'Friends',
    'sessionName': 'Friends',
    'state': state,
    'ticketPrice': 10,
    'currency': 'ETB',
    'configuredPatterns': ['LINE'],
  };
  Map<String, dynamic> get room => {
    'id': 7,
    'name': 'Approved room',
    'ticketPrice': 10,
    'currency': 'ETB',
    'active': true,
    'sessionCapacity': 10,
  };
  Iterable<RequestOptions> calls(String method, String path) =>
      requests.where((r) => r.method == method && r.path == path);

  @override
  Future<ResponseBody> fetch(
    RequestOptions r,
    Stream<Uint8List>? stream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(r);
    Object data;
    var status = 200;
    switch ('${r.method} ${r.path}') {
      case 'GET /api/v1/private-games':
        data = [game];
        if (failList) {
          status = 503;
          data = {'message': 'Try again', 'code': 'UNAVAILABLE'};
        }
      case 'GET /api/v1/rooms':
        data = [room];
        if (failRooms) {
          status = 503;
          data = {'message': 'Template unavailable'};
        }
      case 'GET /api/v1/rooms/7':
        data = room;
      case 'GET /api/v1/rooms/7/sessions':
        data = [session];
      case 'POST /api/v1/private-games':
        data = {'game': game};
        status = 201;
      case 'POST /api/v1/private-games/join':
        if (holdJoin != null) {
          await holdJoin!.future;
        }
        data = game;
      case 'GET /api/v1/private-games/7':
        data = {'game': game, 'session': session, 'room': room};
      case 'POST /api/v1/private-games/7/invite':
        data = {'code': 'ABCDEFGH234567', 'expiresAt': '2026-11-02T12:00:00Z'};
      case 'POST /api/v1/private-games/7/start':
        state = 'STARTED';
        data = game;
      case 'POST /api/v1/private-games/7/cancel':
        state = 'CANCELLED';
        data = game;
      case 'GET /api/v1/challenges/daily':
        data = {
          'businessDate': '2026-11-01',
          'timezone': 'Africa/Addis_Ababa',
          'resetsAt': '2026-11-01T21:00:00Z',
          'totalPoints': claimed ? 70 : 50,
          'challenges': [
            {
              'code': 'FINISH_GAME',
              'target': 1,
              'progress': 1,
              'points': 20,
              'completed': true,
              'claimed': claimed,
            },
            {
              'code': 'EXPLORE_ROOMS',
              'target': 2,
              'progress': 1,
              'points': 30,
              'completed': false,
              'claimed': false,
            },
            {
              'code': 'VERIFIED_BINGO',
              'target': 1,
              'progress': 1,
              'points': 50,
              'completed': true,
              'claimed': true,
            },
          ],
        };
      case 'POST /api/v1/challenges/FINISH_GAME/claim':
        if (failClaim) {
          status = 503;
          data = {'code': 'UNAVAILABLE', 'message': 'Try again'};
        } else {
          claimed = true;
          data = {'points': 20};
        }
      case 'GET /api/v1/leaderboard':
        if (r.queryParameters['page'] == 1 && holdPage != null) {
          await holdPage!.future;
        }
        data = {
          'entries': [
            {'rank': 1, 'alias': alias, 'points': 20, 'me': true},
          ],
          'totalPlayers': 26,
          'page': r.queryParameters['page'],
          'size': 25,
          'myPoints': 20,
          'myRank': visible ? 1 : null,
          'profile': {'alias': alias, 'visible': visible},
          'timezone': 'Africa/Addis_Ababa',
        };
      case 'PUT /api/v1/leaderboard/profile':
        alias = (r.data as Map)['alias'] as String? ?? 'GeneratedAlias';
        visible = (r.data as Map)['visible'] == true;
        data = {'alias': alias, 'visible': visible};
      default:
        throw StateError('Unexpected request: ${r.method} ${r.path}');
    }
    return ResponseBody.fromString(
      jsonEncode(data),
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

Future<void> mount(
  WidgetTester tester,
  CommunityServer server,
  Widget screen,
) async {
  tester.view.physicalSize = const Size(1000, 1600);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  final dio = Dio(BaseOptions(baseUrl: 'https://example.invalid'))
    ..httpClientAdapter = server;
  await tester.pumpWidget(
    ProviderScope(
      overrides: [dioProvider.overrideWithValue(dio)],
      child: MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: screen,
      ),
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> tap(WidgetTester tester, String text) async {
  final f = find.text(text).last;
  await tester.ensureVisible(f);
  await tester.tap(f);
  await tester.pumpAndSettle();
}

ButtonStyleButton action(WidgetTester tester, String text) =>
    tester.widget<ButtonStyleButton>(
      find
          .ancestor(
            of: find.text(text).last,
            matching: find.byWidgetPredicate((w) => w is ButtonStyleButton),
          )
          .first,
    );

void main() {
  testWidgets('join validates input, posts code and opens the joined room', (
    t,
  ) async {
    final s = CommunityServer();
    await mount(t, s, const PrivateGamesScreen());
    await tap(t, 'Join with code');
    expect(action(t, 'Join with code').onPressed, isNull);
    await t.enterText(find.byType(TextField), 'ABCDEFGH234567');
    await t.pump();
    await tap(t, 'Join with code');
    expect(s.calls('POST', '/api/v1/private-games/join').single.data, {
      'code': 'ABCDEFGH234567',
    });
    expect(s.calls('GET', '/api/v1/private-games/7'), hasLength(1));
    expect(find.byType(PrivateGameScreen), findsOneWidget);
  });

  testWidgets(
    'create submits selected rules, generates invite and copies exact code',
    (t) async {
      final s = CommunityServer();
      await mount(t, s, const PrivateGamesScreen());
      await tap(t, 'Create game');
      expect(action(t, 'Create game').onPressed, isNull);
      await t.enterText(find.byType(TextField), 'Weekend friends');
      await t.tap(find.byType(DropdownButtonFormField<int>).first);
      await t.pumpAndSettle();
      await tap(t, 'Approved room · 10 ETB');
      await tap(t, 'Create game');
      final r = s.calls('POST', '/api/v1/private-games').single;
      expect(r.data['name'], 'Weekend friends');
      expect(r.data['templateRoomId'], 7);
      expect(DateTime.parse(r.data['scheduledStartAt']).isUtc, isTrue);
      expect(r.headers['Idempotency-Key'], isNotEmpty);
      expect(s.calls('POST', '/api/v1/private-games/7/invite'), hasLength(1));
      String? copied;
      t.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        (call) async {
          if (call.method == 'Clipboard.setData') {
            copied = (call.arguments as Map)['text'] as String;
          }
          return null;
        },
      );
      addTearDown(
        () => t.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          SystemChannels.platform,
          null,
        ),
      );
      await tap(t, 'Copy');
      expect(copied, 'ABCDEFGH234567');
      await tap(t, 'Back');
      expect(find.byType(PrivateGameScreen), findsOneWidget);
    },
  );

  testWidgets(
    'start invokes start only and removes host controls after reload',
    (t) async {
      final s = CommunityServer();
      await mount(t, s, const PrivateGameScreen(roomId: 7));
      await tap(t, 'Start game');
      expect(s.calls('POST', '/api/v1/private-games/7/start'), hasLength(1));
      expect(find.text('Start game'), findsNothing);
    },
  );
  for (final entry in [
    ('Generate invite', 'invite'),
    ('Cancel game', 'cancel'),
  ]) {
    testWidgets(
      '${entry.$1} requires confirmation and sends the matching action',
      (t) async {
        final s = CommunityServer();
        await mount(t, s, const PrivateGameScreen(roomId: 7));
        await tap(t, entry.$1);
        await tap(t, 'Back');
        expect(s.requests.where((r) => r.method == 'POST'), isEmpty);
        await tap(t, entry.$1);
        await tap(t, 'Continue');
        expect(
          s.calls('POST', '/api/v1/private-games/7/${entry.$2}'),
          hasLength(1),
        );
      },
    );
  }
  testWidgets('member and disabled-room views do not expose host actions', (
    t,
  ) async {
    final s = CommunityServer()..host = false;
    await mount(t, s, const PrivateGameScreen(roomId: 7));
    expect(find.text('Start game'), findsNothing);
    expect(find.text('Cancel game'), findsNothing);
    expect(find.text('Generate invite'), findsNothing);
  });
  testWidgets(
    'open room uses the private room ID and returning reloads details',
    (t) async {
      final s = CommunityServer();
      await mount(t, s, const PrivateGameScreen(roomId: 7));
      await tap(t, 'Open room & tickets');
      expect(s.calls('GET', '/api/v1/rooms/7'), hasLength(1));
      expect(s.calls('GET', '/api/v1/rooms/7/sessions'), hasLength(1));
      await t.pageBack();
      await t.pumpAndSettle();
      expect(s.calls('GET', '/api/v1/private-games/7'), hasLength(2));
    },
  );
  testWidgets(
    'private list error retry fetches again and game tile opens its room',
    (t) async {
      final s = CommunityServer()..failList = true;
      await mount(t, s, const PrivateGamesScreen());
      s.failList = false;
      await t.tap(find.widgetWithText(TextButton, 'Try again'));
      await t.pumpAndSettle();
      await tap(t, 'Friends');
      expect(s.calls('GET', '/api/v1/private-games'), hasLength(2));
      expect(s.calls('GET', '/api/v1/private-games/7'), hasLength(1));
    },
  );
  testWidgets(
    'claim uses correct challenge/date and refreshes points; unfinished is disabled',
    (t) async {
      final s = CommunityServer();
      await mount(t, s, const DailyChallengesScreen());
      final buttons = find.widgetWithText(FilledButton, 'Claim points');
      expect(buttons, findsNWidgets(2));
      expect(t.widget<FilledButton>(buttons.last).onPressed, isNull);
      await t.tap(buttons.first);
      await t.pumpAndSettle();
      final r = s.calls('POST', '/api/v1/challenges/FINISH_GAME/claim').single;
      expect(r.data, {'businessDate': '2026-11-01'});
      expect(r.headers['Idempotency-Key'], isNotEmpty);
      expect(find.text('70 points'), findsOneWidget);
      expect(find.text('Claimed'), findsNWidgets(2));
    },
  );
  testWidgets(
    'claim failure keeps retry idempotency key and never awards local points',
    (t) async {
      final s = CommunityServer()..failClaim = true;
      await mount(t, s, const DailyChallengesScreen());
      await t.tap(find.widgetWithText(FilledButton, 'Claim points').first);
      await t.pumpAndSettle();
      expect(find.text('50 points'), findsOneWidget);
      s.failClaim = false;
      await t.tap(find.widgetWithText(FilledButton, 'Claim points').first);
      await t.pumpAndSettle();
      final r = s
          .calls('POST', '/api/v1/challenges/FINISH_GAME/claim')
          .toList();
      expect(r, hasLength(2));
      expect(r[0].headers['Idempotency-Key'], r[1].headers['Idempotency-Key']);
    },
  );
  testWidgets('ranking pagination and period selection send correct queries', (
    t,
  ) async {
    final s = CommunityServer();
    await mount(t, s, const LeaderboardScreen());
    expect(action(t, 'Previous').onPressed, isNull);
    await tap(t, 'Next');
    expect(s.calls('GET', '/api/v1/leaderboard').last.queryParameters, {
      'period': 'WEEKLY',
      'page': 1,
      'size': 25,
    });
    expect(action(t, 'Next').onPressed, isNull);
    await tap(t, 'Previous');
    await t.tap(find.byType(DropdownButtonFormField<String>));
    await t.pumpAndSettle();
    await tap(t, 'Today');
    expect(s.calls('GET', '/api/v1/leaderboard').last.queryParameters, {
      'period': 'DAILY',
      'page': 0,
      'size': 25,
    });
  });
  testWidgets(
    'profile Back discards edits; Save posts alias and visibility then reloads',
    (t) async {
      final s = CommunityServer();
      await mount(t, s, const LeaderboardScreen());
      await tap(t, 'Public ranking profile');
      await t.enterText(find.byType(TextField), 'Discarded');
      await tap(t, 'Back');
      expect(s.calls('PUT', '/api/v1/leaderboard/profile'), isEmpty);
      await tap(t, 'Public ranking profile');
      await t.enterText(find.byType(TextField), 'NewAlias');
      await t.tap(find.byType(SwitchListTile));
      await t.pump();
      await tap(t, 'Save');
      expect(s.calls('PUT', '/api/v1/leaderboard/profile').single.data, {
        'alias': 'NewAlias',
        'visible': true,
      });
      expect(s.calls('GET', '/api/v1/leaderboard'), hasLength(2));
      expect(find.text('NewAlias'), findsOneWidget);
    },
  );
  testWidgets('loading next page hides stale pagination actions', (t) async {
    final s = CommunityServer()..holdPage = Completer<void>();
    await mount(t, s, const LeaderboardScreen());
    await t.tap(find.text('Next'));
    await t.pump();
    await t.pump(const Duration(milliseconds: 50));
    expect(find.text('Next'), findsNothing);
    s.holdPage!.complete();
    await t.pumpAndSettle();
    expect(action(t, 'Next').onPressed, isNull);
  });
  for (final item in <(Widget, String)>[
    (const PrivateGamesScreen(), '/api/v1/private-games'),
    (const PrivateGameScreen(roomId: 7), '/api/v1/private-games/7'),
    (const DailyChallengesScreen(), '/api/v1/challenges/daily'),
    (const LeaderboardScreen(), '/api/v1/leaderboard'),
  ]) {
    testWidgets('refresh reloads ${item.$2} without a state error', (t) async {
      final s = CommunityServer();
      await mount(t, s, item.$1);
      await t.tap(find.byIcon(Icons.refresh));
      await t.pumpAndSettle();
      expect(s.calls('GET', item.$2), hasLength(2));
      expect(t.takeException(), isNull);
    });
  }
  testWidgets('create and join Back leave without mutations', (t) async {
    final s = CommunityServer();
    await mount(t, s, const PrivateGamesScreen());
    await tap(t, 'Create game');
    await tap(t, 'Back');
    await tap(t, 'Join with code');
    await tap(t, 'Back');
    expect(s.requests.where((r) => r.method == 'POST'), isEmpty);
  });
  testWidgets('disabled private game has no host actions', (t) async {
    final s = CommunityServer()..active = false;
    await mount(t, s, const PrivateGameScreen(roomId: 7));
    expect(find.text('Start game'), findsNothing);
    expect(find.text('Cancel game'), findsNothing);
    expect(find.text('Generate invite'), findsNothing);
  });
  testWidgets(
    'template load error can retry without abandoning the creation form',
    (t) async {
      final s = CommunityServer()..failRooms = true;
      await mount(t, s, const PrivateGamesScreen());
      await tap(t, 'Create game');
      s.failRooms = false;
      await tap(t, 'Try again');
      expect(s.calls('GET', '/api/v1/rooms'), hasLength(2));
      expect(find.byType(DropdownButtonFormField<int>), findsNWidgets(2));
    },
  );
  testWidgets('join cannot be dismissed or submitted twice while pending', (
    t,
  ) async {
    final s = CommunityServer()..holdJoin = Completer<void>();
    await mount(t, s, const PrivateGamesScreen());
    await tap(t, 'Join with code');
    await t.enterText(find.byType(TextField), 'ABCDEFGH234567');
    await t.pump();
    await t.tap(find.text('Join with code').last);
    await t.pump();
    await t.pump(const Duration(milliseconds: 50));
    expect(action(t, 'Back').onPressed, isNull);
    await t.binding.handlePopRoute();
    await t.pump();
    expect(find.byType(AlertDialog), findsOneWidget);
    expect(s.calls('POST', '/api/v1/private-games/join'), hasLength(1));
    s.holdJoin!.complete();
    await t.pumpAndSettle();
    expect(find.byType(PrivateGameScreen), findsOneWidget);
  });
  testWidgets('profile can opt out and submit an empty alias as null', (
    t,
  ) async {
    final s = CommunityServer()..visible = true;
    await mount(t, s, const LeaderboardScreen());
    await tap(t, 'Public ranking profile');
    await t.enterText(find.byType(TextField), '  ');
    await t.tap(find.byType(SwitchListTile));
    await t.pump();
    await tap(t, 'Save');
    expect(s.calls('PUT', '/api/v1/leaderboard/profile').single.data, {
      'alias': null,
      'visible': false,
    });
  });
}
