import 'package:bingo_app/src/features/game/application/live_game_timeline.dart';
import 'package:bingo_app/src/features/game/domain/live_game_models.dart';
import 'package:flutter_test/flutter_test.dart';

LiveDrawEvent _draw({required int sequenceNo, required int numberDrawn}) {
  return LiveDrawEvent(
    sessionId: 42,
    sequenceNo: sequenceNo,
    numberDrawn: numberDrawn,
    drawnAt: DateTime.utc(2026, 1, 1),
    id: sequenceNo,
    operationId: 'op-$sequenceNo',
  );
}

void main() {
  test('deduplicates and orders out-of-order draw events', () {
    final timeline = LiveGameTimeline();
    final sequenceTwo = _draw(sequenceNo: 2, numberDrawn: 18);
    final sequenceOne = _draw(sequenceNo: 1, numberDrawn: 7);

    expect(timeline.ingest(sequenceTwo), isTrue);
    expect(timeline.hasGap, isTrue);
    expect(timeline.ingest(sequenceOne), isTrue);
    expect(timeline.ingest(sequenceTwo), isFalse);
    expect(timeline.lastSequence, 2);
    expect(timeline.orderedDraws.map((event) => event.sequenceNo), orderedEquals([1, 2]));
  });

  test('tracks the next missing sequence when a gap exists', () {
    final timeline = LiveGameTimeline();
    timeline.ingest(_draw(sequenceNo: 1, numberDrawn: 12));
    timeline.ingest(_draw(sequenceNo: 3, numberDrawn: 44));

    expect(timeline.nextMissingSequence(), 2);
  });

  test('ignores invalid sequence numbers and resets cleanly', () {
    final timeline = LiveGameTimeline();

    expect(timeline.ingest(_draw(sequenceNo: 0, numberDrawn: 9)), isFalse);
    expect(timeline.ingest(_draw(sequenceNo: -1, numberDrawn: 9)), isFalse);
    expect(timeline.lastSequence, 0);

    timeline.ingest(_draw(sequenceNo: 1, numberDrawn: 9));
    timeline.reset();

    expect(timeline.lastSequence, 0);
    expect(timeline.orderedDraws, isEmpty);
    expect(timeline.nextMissingSequence(), isNull);
  });

}
