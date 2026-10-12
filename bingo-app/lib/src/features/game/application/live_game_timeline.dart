import 'package:bingo_app/src/features/game/domain/live_game_models.dart';

class LiveGameTimeline {
  final Map<int, LiveDrawEvent> _bySequence = <int, LiveDrawEvent>{};

  int _lastSequence = 0;

  int get lastSequence => _lastSequence;

  List<LiveDrawEvent> get orderedDraws => _bySequence.values.toList()
    ..sort((left, right) => left.sequenceNo.compareTo(right.sequenceNo));

  bool containsSequence(int sequenceNo) => _bySequence.containsKey(sequenceNo);

  bool ingest(LiveDrawEvent event) {
    if (event.sequenceNo <= 0) {
      return false;
    }
    if (_bySequence.containsKey(event.sequenceNo)) {
      return false;
    }
    _bySequence[event.sequenceNo] = event;
    if (event.sequenceNo > _lastSequence) {
      _lastSequence = event.sequenceNo;
    }
    return true;
  }

  bool get hasGap => _bySequence.isNotEmpty && _bySequence.length != _lastSequence;

  int? nextMissingSequence() {
    for (int sequence = 1; sequence <= _lastSequence; sequence += 1) {
      if (!_bySequence.containsKey(sequence)) {
        return sequence;
      }
    }
    return null;
  }

  void reset() {
    _bySequence.clear();
    _lastSequence = 0;
  }
}
