import 'dart:async';
import 'dart:convert';

import 'package:bingo_app/src/features/game/domain/live_game_models.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

abstract class LiveGameTransport {
  Future<void> connect({
    required Uri uri,
    required String accessToken,
    required int sessionId,
    required void Function(LiveDrawEvent event) onDraw,
    required void Function(Object error) onError,
    required void Function() onDisconnected,
  });

  Future<void> disconnect();
}

class StompLiveGameTransport implements LiveGameTransport {
  WebSocketChannel? _channel;
  StreamSubscription? _subscription;
  bool _connected = false;
  final StringBuffer _frameBuffer = StringBuffer();
  void Function(LiveDrawEvent event)? _onDraw;
  void Function(Object error)? _onError;
  void Function()? _onDisconnected;
  int? _sessionId;

  @override
  Future<void> connect({
    required Uri uri,
    required String accessToken,
    required int sessionId,
    required void Function(LiveDrawEvent event) onDraw,
    required void Function(Object error) onError,
    required void Function() onDisconnected,
  }) async {
    await disconnect();
    _sessionId = sessionId;
    _onDraw = onDraw;
    _onError = onError;
    _onDisconnected = onDisconnected;
    _connected = false;
    _frameBuffer.clear();

    _channel = WebSocketChannel.connect(uri);
    _subscription = _channel!.stream.listen(
      _handleMessage,
      onError: (Object error) {
        _onError?.call(error);
        unawaited(disconnect());
      },
      onDone: () {
        _onDisconnected?.call();
      },
      cancelOnError: true,
    );

    _sendFrame('CONNECT', {
      'accept-version': '1.2',
      'heart-beat': '0,0',
      'Authorization': 'Bearer $accessToken',
    });
  }

  @override
  Future<void> disconnect() async {
    try {
      if (_connected) {
        _sendFrame('DISCONNECT', const {});
      }
    } catch (_) {
      // Ignore disconnect errors.
    }

    await _subscription?.cancel();
    _subscription = null;
    await _channel?.sink.close();
    _channel = null;
    _connected = false;
    _sessionId = null;
  }

  void _handleMessage(dynamic message) {
    final text = message.toString();
    _frameBuffer.write(text);
    final frames = _consumeFrames(_frameBuffer.toString());
    if (frames.remainder.isNotEmpty) {
      _frameBuffer
        ..clear()
        ..write(frames.remainder);
    } else {
      _frameBuffer.clear();
    }

    for (final frame in frames.frames) {
      switch (frame.command) {
        case 'CONNECTED':
          _connected = true;
          if (_sessionId != null) {
            _sendFrame('SUBSCRIBE', {
              'id': 'draws-${_sessionId!}',
              'destination': '/topic/session/${_sessionId!}/draws',
              'ack': 'auto',
            });
          }
          break;
        case 'MESSAGE':
          final decoded = jsonDecode(frame.body) as Map<String, dynamic>;
          _onDraw?.call(LiveDrawEvent.fromJson(decoded));
          break;
        case 'ERROR':
          _onError?.call(StateError(frame.body.isEmpty ? 'Live game stream error' : frame.body));
          unawaited(disconnect());
          break;
        default:
          break;
      }
    }
  }

  void _sendFrame(String command, Map<String, String> headers, [String body = '']) {
    final buffer = StringBuffer('$command\n');
    headers.forEach((key, value) {
      buffer.write('$key:$value\n');
    });
    buffer.write('\n');
    buffer.write(body);
    buffer.write('\u0000');
    _channel?.sink.add(buffer.toString());
  }

  _ParsedFrames _consumeFrames(String input) {
    final frames = <_StompFrame>[];
    var remaining = input;
    while (true) {
      final terminatorIndex = remaining.indexOf('\u0000');
      if (terminatorIndex < 0) {
        break;
      }
      final raw = remaining.substring(0, terminatorIndex);
      remaining = remaining.substring(terminatorIndex + 1);
      if (raw.trim().isEmpty) {
        continue;
      }
      frames.add(_parseFrame(raw));
    }
    return _ParsedFrames(frames, remaining);
  }

  _StompFrame _parseFrame(String rawFrame) {
    final parts = rawFrame.split('\n\n');
    final headerLines = parts.first.split('\n');
    final command = headerLines.first.trim();
    final headers = <String, String>{};
    for (final line in headerLines.skip(1)) {
      final colonIndex = line.indexOf(':');
      if (colonIndex <= 0) {
        continue;
      }
      headers[line.substring(0, colonIndex).trim()] = line.substring(colonIndex + 1).trim();
    }
    final body = parts.length > 1 ? parts.sublist(1).join('\n\n').replaceAll('\r', '') : '';
    return _StompFrame(command: command, headers: headers, body: body);
  }
}

class _ParsedFrames {
  const _ParsedFrames(this.frames, this.remainder);

  final List<_StompFrame> frames;
  final String remainder;
}

class _StompFrame {
  const _StompFrame({required this.command, required this.headers, required this.body});

  final String command;
  final Map<String, String> headers;
  final String body;
}
