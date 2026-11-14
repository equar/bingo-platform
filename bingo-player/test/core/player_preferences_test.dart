import 'package:bingo_player/src/core/storage/player_preferences.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'manual marks and ticket selection are isolated by account and game',
    () async {
      SharedPreferences.setMockInitialValues({});
      final preferences = PlayerPreferences();
      await preferences.load();

      await preferences.setScope('account-7-session-40');
      await preferences.toggleManualMark(300, 17);
      await preferences.toggleSelectedTicket(300);
      final calledNumberHistory = [7, 17, 42];
      expect(preferences.manualMarksForTicket(300), {17});
      expect(preferences.selectedTicketIds, {300});

      await preferences.clearManualMarks();
      expect(preferences.manualMarksForTicket(300), isEmpty);
      expect(preferences.selectedTicketIds, {300});
      expect(calledNumberHistory, [7, 17, 42]);
      await preferences.toggleManualMark(300, 17);

      await preferences.setScope('account-8-session-40');
      expect(preferences.manualMarksForTicket(300), isEmpty);
      expect(preferences.selectedTicketIds, isEmpty);

      await preferences.setScope('account-7-session-40');
      expect(preferences.manualMarksForTicket(300), {17});
      expect(preferences.selectedTicketIds, {300});
    },
  );

  test('board supports one through four tickets and caps selections', () async {
    SharedPreferences.setMockInitialValues({});
    final preferences = PlayerPreferences();
    await preferences.load();
    await preferences.setScope('account-1-session-2');
    await preferences.setBoardSize(4);
    for (final id in [1, 2, 3, 4, 5]) {
      await preferences.toggleSelectedTicket(id);
    }
    expect(preferences.cardsOnBoard, 4);
    expect(preferences.selectedTicketIds, {2, 3, 4, 5});
    await preferences.setBoardSize(1);
    expect(preferences.cardsOnBoard, 1);
    expect(preferences.selectedTicketIds, {2});
  });

  test('pattern priorities are distinct and limited to three', () async {
    SharedPreferences.setMockInitialValues({});
    final preferences = PlayerPreferences();
    await preferences.load();
    for (final pattern in ['LINE', 'TWO_LINES', 'FULL_HOUSE', 'OUTER_FRAME']) {
      await preferences.toggleSortPriority(pattern);
    }
    expect(preferences.sortPriorities, ['LINE', 'TWO_LINES', 'FULL_HOUSE']);
    await preferences.toggleSortPriority('LINE');
    await preferences.toggleSortPriority('OUTER_FRAME');
    expect(preferences.sortPriorities, [
      'TWO_LINES',
      'FULL_HOUSE',
      'OUTER_FRAME',
    ]);
    expect(preferences.sortPriorities.toSet(), hasLength(3));
  });

  test(
    'seeds deterministically, keeps selections stable, and fills requested slots',
    () async {
      SharedPreferences.setMockInitialValues({});
      final preferences = PlayerPreferences();
      await preferences.load();
      await preferences.setScope('account-1-session-2');
      await preferences.ensureBoardSelection([9, 4, 7, 2]);
      expect(preferences.selectedTicketIds, {2, 4});

      await preferences.ensureBoardSelection([9, 2, 4, 7, 1]);
      expect(preferences.selectedTicketIds, {2, 4});

      await preferences.setBoardSize(4);
      await preferences.ensureBoardSelection([9, 2, 4, 7, 1]);
      expect(preferences.selectedTicketIds, {1, 2, 4, 7});
    },
  );

  test(
    'sanitizes invalid stored board, color, marks, and duplicate priorities',
    () async {
      SharedPreferences.setMockInitialValues({
        'player.boardSize': 99,
        'player.selectionColor': 999,
        'player.ticketSortPriorities': ['LINE', 'LINE', 'BOGUS', 'X_SHAPE'],
        'player.manualMarks.account-1-session-2.10': ['0', '-1', '76', '24'],
      });
      final preferences = PlayerPreferences();
      await preferences.load();
      expect(preferences.cardsOnBoard, 2);
      expect(
        preferences.selectionColorIndex,
        PlayerPreferences.selectionColors.length - 1,
      );
      expect(preferences.sortPriorities, ['LINE', 'X_SHAPE']);

      await preferences.setScope('account-1-session-2');
      expect(preferences.manualMarksForTicket(10), {24});
    },
  );
}
