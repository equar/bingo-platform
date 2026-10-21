import 'dart:async';
import 'dart:io';

import 'package:bingo_app/src/features/game/application/live_game_transport.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'negotiates heartbeats, ignores heartbeat frames, and sends liveness pings',
    () async {
      final serverHeartbeatObserved = Completer<void>();
      final connectHeartbeatHeaderObserved = Completer<void>();
      final authorizationHeaderObserved = Completer<void>();
      late WebSocket peer;
      Timer? serverHeartbeat;
      final server = await _startServer((socket) {
        peer = socket;
        socket.listen((dynamic message) {
          final text = message.toString();
          if (text.startsWith('CONNECT\n')) {
            if (text.contains('heart-beat:40,40')) {
              connectHeartbeatHeaderObserved.complete();
            }
            if (text.contains('Authorization:Bearer test-token')) {
              authorizationHeaderObserved.complete();
            }
            socket.add('\nCONNECTED\nversion:1.2\nheart-beat:20,20\n\n\u0000');
            serverHeartbeat = Timer.periodic(
              const Duration(milliseconds: 10),
              (_) => socket.add('\n'),
            );
          } else if (text.trim().isEmpty &&
              !serverHeartbeatObserved.isCompleted) {
            serverHeartbeatObserved.complete();
          }
        });
      });
      final transport = StompLiveGameTransport(
        heartbeatOutgoing: const Duration(milliseconds: 40),
        heartbeatIncoming: const Duration(milliseconds: 40),
        connectTimeout: const Duration(seconds: 2),
      );
      final errors = <Object>[];
      var disconnects = 0;
      addTearDown(() async {
        serverHeartbeat?.cancel();
        await transport.disconnect();
        await server.close();
      });

      await transport.connect(
        uri: server.uri,
        accessToken: 'test-token',
        sessionId: 42,
        onDraw: (_) {},
        onError: errors.add,
        onDisconnected: () => disconnects += 1,
      );
      await connectHeartbeatHeaderObserved.future.timeout(
        const Duration(seconds: 2),
      );
      await authorizationHeaderObserved.future.timeout(
        const Duration(seconds: 2),
      );
      await serverHeartbeatObserved.future.timeout(const Duration(seconds: 2));
      await Future<void>.delayed(const Duration(milliseconds: 120));

      expect(transport.isConnected, isTrue);
      expect(errors, isEmpty);
      expect(disconnects, 0);
      expect(peer.readyState, WebSocket.open);
    },
  );

  test('reports a missed server heartbeat as a transport failure', () async {
    final server = await _startServer((socket) {
      socket.listen((dynamic message) {
        if (message.toString().startsWith('CONNECT\n')) {
          socket.add('CONNECTED\nversion:1.2\nheart-beat:10,10\n\n\u0000');
        }
      });
    });
    final transport = StompLiveGameTransport(
      heartbeatOutgoing: const Duration(milliseconds: 30),
      heartbeatIncoming: const Duration(milliseconds: 30),
      connectTimeout: const Duration(seconds: 2),
    );
    final failed = Completer<Object>();
    addTearDown(() async {
      await transport.disconnect();
      await server.close();
    });

    await transport.connect(
      uri: server.uri,
      accessToken: 'test-token',
      sessionId: 42,
      onDraw: (_) {},
      onError: failed.complete,
      onDisconnected: () {},
    );

    final error = await failed.future.timeout(const Duration(seconds: 2));
    expect(error, isA<TimeoutException>());
    expect(transport.isConnected, isFalse);
  });

  test(
    'reports an idle remote disconnect so the controller can reconnect',
    () async {
      final peerReady = Completer<WebSocket>();
      final server = await _startServer((socket) {
        peerReady.complete(socket);
        socket.listen((dynamic message) {
          if (message.toString().startsWith('CONNECT\n')) {
            socket.add('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\u0000');
          }
        });
      });
      final transport = StompLiveGameTransport(
        heartbeatOutgoing: Duration.zero,
        heartbeatIncoming: Duration.zero,
        connectTimeout: const Duration(seconds: 2),
      );
      final disconnected = Completer<void>();
      addTearDown(() async {
        await transport.disconnect();
        await server.close();
      });

      await transport.connect(
        uri: server.uri,
        accessToken: 'test-token',
        sessionId: 42,
        onDraw: (_) {},
        onError: (error) => fail('Unexpected transport error: $error'),
        onDisconnected: disconnected.complete,
      );
      await (await peerReady.future).close();

      await disconnected.future.timeout(const Duration(seconds: 2));
      expect(transport.isConnected, isFalse);
    },
  );
}

Future<_LocalWebSocketServer> _startServer(
  void Function(WebSocket socket) onConnection,
) async {
  final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
  server.listen((request) async {
    onConnection(await WebSocketTransformer.upgrade(request));
  });
  return _LocalWebSocketServer(server);
}

class _LocalWebSocketServer {
  const _LocalWebSocketServer(this._server);

  final HttpServer _server;

  Uri get uri =>
      Uri(scheme: 'ws', host: '127.0.0.1', port: _server.port, path: '/ws');

  Future<void> close() => _server.close(force: true);
}
