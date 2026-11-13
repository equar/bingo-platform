import 'package:bingo_player/src/features/game/domain/ticket_models.dart';
import 'package:bingo_player/src/features/game/domain/ticket_ranking.dart';
import 'package:flutter_test/flutter_test.dart';

TicketResponse _ticket() => TicketResponse(
  id: 10,
  sessionId: 20,
  ownerId: 30,
  publicTicketCode: 'T-10',
  operationId: 'op-10',
  grid: [
    [1, 2, 3, 4, 5],
    [6, 7, 8, 9, 10],
    [11, 12, 0, 14, 15],
    [16, 17, 18, 19, 20],
    [21, 22, 23, 24, 25],
  ],
  purchasedAt: DateTime.utc(2026),
);

Set<int> _numbersAt(TicketResponse ticket, List<List<int>> cells) => {
  for (final cell in cells)
    if (ticket.grid[cell[0]][cell[1]] != 0) ticket.grid[cell[0]][cell[1]],
};

List<List<int>> _cells(String layout) => [
  for (var row = 0; row < 5; row++)
    for (var column = 0; column < 5; column++)
      if (layout.split('\n')[row][column] == '#') [row, column],
];

void main() {
  group('TicketRanking', () {
    test('uses only called numbers and excludes the free center', () {
      final ticket = _ticket();
      final called = {1, 2, 3, 4, 5};

      expect(TicketRanking.remainingCells(ticket.grid, called, 'LINE'), 0);
      expect(
        TicketRanking.remainingCells(ticket.grid, called, 'FULL_HOUSE'),
        19,
      );
    });

    test('matches every supported server pattern placement', () {
      final ticket = _ticket();
      final cases = <(String, String)>[
        ('LINE', '#####\n.....\n.....\n.....\n.....'),
        ('TWO_LINES', '#####\n#....\n#....\n#....\n#....'),
        ('FOUR_CORNERS', '#...#\n.....\n.....\n.....\n#...#'),
        ('POSTAGE_STAMP', '##...\n##...\n.....\n.....\n.....'),
        ('POSTAGE_STAMP', '...##\n...##\n.....\n.....\n.....'),
        ('POSTAGE_STAMP', '.....\n.....\n.....\n##...\n##...'),
        ('POSTAGE_STAMP', '.....\n.....\n.....\n...##\n...##'),
        ('X_SHAPE', '#...#\n.#.#.\n..#..\n.#.#.\n#...#'),
        ('T_SHAPE', '#####\n..#..\n..#..\n..#..\n..#..'),
        ('T_SHAPE', '..#..\n..#..\n..#..\n..#..\n#####'),
        ('T_SHAPE', '#....\n#....\n#####\n#....\n#....'),
        ('T_SHAPE', '....#\n....#\n#####\n....#\n....#'),
        ('L_SHAPE', '#....\n#....\n#....\n#....\n#####'),
        ('L_SHAPE', '#####\n#....\n#....\n#....\n#....'),
        ('L_SHAPE', '#####\n....#\n....#\n....#\n....#'),
        ('L_SHAPE', '....#\n....#\n....#\n....#\n#####'),
        ('OUTER_FRAME', '#####\n#...#\n#...#\n#...#\n#####'),
      ];
      for (final (pattern, layout) in cases) {
        expect(
          TicketRanking.remainingCells(
            ticket.grid,
            _numbersAt(ticket, _cells(layout)),
            pattern,
          ),
          0,
          reason: '$pattern should match $layout',
        );
      }
      expect(
        TicketRanking.remainingCells(
          ticket.grid,
          _numbersAt(ticket, [
            for (var r = 0; r < 5; r++)
              for (var c = 0; c < 5; c++) [r, c],
          ]),
          'FULL_HOUSE',
        ),
        0,
      );
    });

    test('ranks eligible tickets first and resolves ties by ticket ID', () {
      final first = _ticket();
      final second = TicketResponse(
        id: 11,
        sessionId: first.sessionId,
        ownerId: first.ownerId,
        publicTicketCode: 'T-11',
        operationId: 'op-11',
        grid: first.grid,
        purchasedAt: first.purchasedAt,
        eligibleClaimPatterns: const ['LINE'],
      );
      final ranked = TicketRanking.rank(
        tickets: [second, first],
        called: {1, 2, 3, 4, 5},
        configuredPatterns: const ['LINE'],
        priorities: const ['LINE'],
        claimStatuses: const {},
        claimWindowOpen: true,
      );

      expect(ranked.map((item) => item.ticket.id), [11, 10]);
      expect(ranked.first.claimState, TicketClaimState.claimable);
    });

    test(
      'uses only configured prizes and never lets priority add an unawarded pattern',
      () {
        final ranked = TicketRanking.rank(
          tickets: [_ticket()],
          called: const {},
          configuredPatterns: const ['LINE', 'FULL_HOUSE', 'OUTER_FRAME'],
          configuredPrizePatterns: const ['LINE'],
          priorities: const ['OUTER_FRAME', 'FULL_HOUSE'],
          claimStatuses: const {},
          claimWindowOpen: true,
        );
        expect(ranked, hasLength(1));
        expect(ranked.single.pattern, 'LINE');
        expect(ranked.single.claimState, TicketClaimState.approaching);
      },
    );

    test(
      'reports server-eligible, submitted, awarded, and closed distinctly',
      () {
        final eligible = TicketResponse(
          id: 10,
          sessionId: 20,
          ownerId: 30,
          publicTicketCode: 'T-10',
          operationId: 'op-10',
          grid: _ticket().grid,
          purchasedAt: DateTime.utc(2026),
          eligibleClaimPatterns: const ['LINE'],
        );
        TicketClaimState rankState(String? status, {bool window = true}) =>
            TicketRanking.rank(
              tickets: [eligible],
              called: const {},
              configuredPatterns: const ['LINE'],
              priorities: const [],
              claimStatuses: status == null ? const {} : {'10:LINE': status},
              claimWindowOpen: window,
            ).single.claimState;

        expect(rankState(null), TicketClaimState.claimable);
        expect(rankState('PENDING_REVIEW'), TicketClaimState.submitted);
        expect(rankState('APPROVED'), TicketClaimState.awarded);
        expect(rankState('CLOSED'), TicketClaimState.closed);
        expect(rankState(null, window: false), TicketClaimState.closed);
      },
    );

    test('counts a crossing number once when estimating two lines', () {
      final ticket = _ticket();
      // The first row and column miss only their shared top-left cell.
      final called = _numbersAt(ticket, [
        for (var column = 1; column < 5; column++) [0, column],
        for (var row = 1; row < 5; row++) [row, 0],
      ]);
      expect(TicketRanking.remainingCells(ticket.grid, called, 'TWO_LINES'), 1);
    });
  });
}
