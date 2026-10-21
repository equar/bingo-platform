import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;

import 'package:bingo_app/src/features/game/domain/live_game_models.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

abstract class LiveGameTransport {
  bool get isConnected;

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
  StompLiveGameTransport({
    this.heartbeatOutgoing = const Duration(seconds: 10),
    this.heartbeatIncoming = const Duration(seconds: 10),
    this.heartbeatTimeoutMultiplier = 2,
    this.connectTimeout = const Duration(seconds: 15),
  }) : assert(heartbeatOutgoing >= Duration.zero),
       assert(heartbeatIncoming >= Duration.zero),
       assert(heartbeatTimeoutMultiplier >= 1),
       assert(connectTimeout > Duration.zero);

  static const String _protocolVersion = '1.2';

  final Duration heartbeatOutgoing;
  final Duration heartbeatIncoming;
  final int heartbeatTimeoutMultiplier;
  final Duration connectTimeout;

  WebSocketChannel? _channel;
  StreamSubscription<dynamic>? _subscription;
  Completer<void>? _connectCompleter;
  Timer? _heartbeatTimer;
  bool _connected = false;
  bool _disconnecting = false;
  bool _failureReported = false;
  final StringBuffer _frameBuffer = StringBuffer();
  void Function(LiveDrawEvent event)? _onDraw;
  void Function(Object error)? _onError;
  void Function()? _onDisconnected;
  int? _sessionId;
  Duration _negotiatedOutgoing = Duration.zero;
  Duration _negotiatedIncoming = Duration.zero;
  DateTime? _lastClientActivity;
  DateTime? _lastServerActivity;

  @override
  bool get isConnected => _connected;

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
    _disconnecting = false;
    _failureReported = false;
    _frameBuffer.clear();
    _negotiatedOutgoing = Duration.zero;
    _negotiatedIncoming = Duration.zero;

    final channel = WebSocketChannel.connect(uri);
    _channel = channel;
    final connectedCompleter = Completer<void>();
    unawaited(
      connectedCompleter.future.then<void>((_) {}, onError: (Object _) {}),
    );
    _connectCompleter = connectedCompleter;
    _subscription = channel.stream.listen(
      _handleMessage,
      onError: _handleStreamError,
      onDone: _handleStreamDone,
      cancelOnError: false,
    );

    try {
      await channel.ready.timeout(connectTimeout);
      if (!identical(_channel, channel)) {
        throw StateError('STOMP connection was closed before it became ready');
      }
      _sendFrame('CONNECT', {
        'accept-version': _protocolVersion,
        'heart-beat':
            '${heartbeatOutgoing.inMilliseconds},${heartbeatIncoming.inMilliseconds}',
        'Authorization': 'Bearer $accessToken',
      });
      await connectedCompleter.future.timeout(connectTimeout);
    } catch (_) {
      if (identical(_channel, channel)) {
        await _closeChannel(channel);
      }
      rethrow;
    }
  }

  @override
  Future<void> disconnect() async {
    final pendingConnect = _connectCompleter;
    if (pendingConnect != null && !pendingConnect.isCompleted) {
      pendingConnect.completeError(
        StateError('STOMP connection was cancelled'),
      );
    }

    final channel = _channel;
    if (channel == null) {
      _stopHeartbeat();
      _connected = false;
      _sessionId = null;
      _connectCompleter = null;
      return;
    }
    await _closeChannel(channel);
  }

  Future<void> _closeChannel(WebSocketChannel channel) async {
    if (!identical(_channel, channel) || _disconnecting) {
      return;
    }
    _disconnecting = true;
    _stopHeartbeat();
    try {
      if (_connected) {
        _sendFrame('DISCONNECT', const {});
      }
    } finally {
      _connected = false;
      final subscription = _subscription;
      _subscription = null;
      _channel = null;
      _sessionId = null;
      _connectCompleter = null;
      try {
        await subscription?.cancel();
      } finally {
        try {
          await channel.sink.close();
        } finally {
          _disconnecting = false;
        }
      }
    }
  }

  void _handleMessage(dynamic message) {
    if (_disconnecting || _failureReported) {
      return;
    }
    _lastServerActivity = DateTime.now();
    final text = message.toString().replaceFirst(RegExp(r'^(?:\r?\n)+'), '');
    if (text.isEmpty) {
      return;
    }
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
          _lastClientActivity = DateTime.now();
          _negotiateHeartbeats(frame.headers['heart-beat']);
          _startHeartbeat();
          if (_sessionId != null) {
            _sendFrame('SUBSCRIBE', {
              'id': 'draws-${_sessionId!}',
              'destination': '/topic/session/${_sessionId!}/draws',
              'ack': 'auto',
            });
          }
          final completer = _connectCompleter;
          if (completer != null && !completer.isCompleted) {
            completer.complete();
          }
          break;
        case 'MESSAGE':
          final decoded = jsonDecode(frame.body) as Map<String, dynamic>;
          _onDraw?.call(LiveDrawEvent.fromJson(decoded));
          break;
        case 'ERROR':
          final error = StateError(
            frame.body.isEmpty ? 'Live game stream error' : frame.body,
          );
          final completer = _connectCompleter;
          if (!_connected && completer != null && !completer.isCompleted) {
            completer.completeError(error);
          } else {
            _signalConnectedError(error);
          }
          break;
        default:
          break;
      }
    }
  }

  void _handleStreamError(Object error, StackTrace stackTrace) {
    if (_disconnecting || _failureReported) {
      return;
    }
    _stopHeartbeat();
    final completer = _connectCompleter;
    if (!_connected && completer != null && !completer.isCompleted) {
      completer.completeError(error, stackTrace);
      return;
    }
    _signalConnectedError(error);
  }

  void _handleStreamDone() {
    _stopHeartbeat();
    if (_disconnecting || _failureReported) {
      return;
    }
    if (!_connected) {
      final completer = _connectCompleter;
      if (completer != null && !completer.isCompleted) {
        completer.completeError(
          StateError('STOMP connection closed before CONNECTED'),
        );
      }
      return;
    }
    _connected = false;
    _onDisconnected?.call();
  }

  void _signalConnectedError(Object error) {
    if (_failureReported || _disconnecting) {
      return;
    }
    _failureReported = true;
    _connected = false;
    _stopHeartbeat();
    _onError?.call(error);
  }

  void _negotiateHeartbeats(String? header) {
    final serverValues = header?.split(',');
    final serverOutgoing =
        int.tryParse(
          serverValues != null && serverValues.isNotEmpty
              ? serverValues[0]
              : '',
        ) ??
        0;
    final serverIncoming =
        int.tryParse(
          serverValues != null && serverValues.length > 1
              ? serverValues[1]
              : '',
        ) ??
        0;
    final clientOutgoing = heartbeatOutgoing.inMilliseconds;
    final clientIncoming = heartbeatIncoming.inMilliseconds;

    _negotiatedOutgoing = clientOutgoing > 0 && serverIncoming > 0
        ? Duration(milliseconds: math.max(clientOutgoing, serverIncoming))
        : Duration.zero;
    _negotiatedIncoming = serverOutgoing > 0 && clientIncoming > 0
        ? Duration(milliseconds: math.max(serverOutgoing, clientIncoming))
        : Duration.zero;
  }

  void _startHeartbeat() {
    _stopHeartbeat();
    final activeIntervals = [
      _negotiatedOutgoing,
      _negotiatedIncoming,
    ].where((interval) => interval > Duration.zero);
    if (activeIntervals.isEmpty) {
      return;
    }
    final checkEvery = activeIntervals
        .map((interval) => interval.inMilliseconds)
        .reduce(math.min)
        .clamp(1, 1 << 31)
        .toInt();
    _lastClientActivity = DateTime.now();
    _lastServerActivity = DateTime.now();
    _heartbeatTimer = Timer.periodic(
      Duration(milliseconds: checkEvery),
      (_) => _checkHeartbeat(),
    );
  }

  void _checkHeartbeat() {
    if (!_connected) {
      _stopHeartbeat();
      return;
    }
    final now = DateTime.now();
    if (_negotiatedOutgoing > Duration.zero &&
        now.difference(_lastClientActivity ?? now) >= _negotiatedOutgoing) {
      _channel?.sink.add('\n');
      _lastClientActivity = now;
    }

    if (_negotiatedIncoming > Duration.zero &&
        now.difference(_lastServerActivity ?? now).inMilliseconds >
            _negotiatedIncoming.inMilliseconds * heartbeatTimeoutMultiplier) {
      _signalConnectedError(
        TimeoutException(
          'No STOMP heartbeat received within '
          '${_negotiatedIncoming.inMilliseconds * heartbeatTimeoutMultiplier} ms',
        ),
      );
    }
  }

  void _stopHeartbeat() {
    _heartbeatTimer?.cancel();
    _heartbeatTimer = null;
  }

  void _sendFrame(
    String command,
    Map<String, String> headers, [
    String body = '',
  ]) {
    final buffer = StringBuffer('$command\n');
    headers.forEach((key, value) {
      buffer.write('$key:$value\n');
    });
    buffer.write('\n');
    buffer.write(body);
    buffer.write('\u0000');
    _channel?.sink.add(buffer.toString());
    _lastClientActivity = DateTime.now();
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
    final parts = rawFrame.trimLeft().replaceAll('\r\n', '\n').split('\n\n');
    final headerLines = parts.first.split('\n');
    final command = headerLines.first.trim();
    final headers = <String, String>{};
    for (final line in headerLines.skip(1)) {
      final colonIndex = line.indexOf(':');
      if (colonIndex <= 0) {
        continue;
      }
      headers[line.substring(0, colonIndex).trim()] = line
          .substring(colonIndex + 1)
          .trim();
    }
    final body = parts.length > 1
        ? parts.sublist(1).join('\n\n').replaceAll('\r', '')
        : '';
    return _StompFrame(command: command, headers: headers, body: body);
  }
}

class _ParsedFrames {
  const _ParsedFrames(this.frames, this.remainder);

  final List<_StompFrame> frames;
  final String remainder;
}

class _StompFrame {
  const _StompFrame({
    required this.command,
    required this.headers,
    required this.body,
  });

  final String command;
  final Map<String, String> headers;
  final String body;
}
