import 'ticket_models.dart';

enum TicketClaimState {
  approaching,
  claimable,
  submitted,
  rejected,
  awarded,
  closed,
}

class TicketRank {
  const TicketRank({
    required this.ticket,
    required this.pattern,
    required this.remainingCells,
    required this.claimState,
  });

  final TicketResponse ticket;
  final String pattern;
  final int remainingCells;
  final TicketClaimState claimState;
}

/// Mirrors the server's supported 75-ball pattern placements. Manual daubs
/// are deliberately excluded: only server-called numbers contribute.
class TicketRanking {
  static const supportedPatterns = <String>[
    'LINE',
    'TWO_LINES',
    'FULL_HOUSE',
    'FOUR_CORNERS',
    'POSTAGE_STAMP',
    'X_SHAPE',
    'T_SHAPE',
    'L_SHAPE',
    'OUTER_FRAME',
    'HORIZONTAL_LINE',
    'VERTICAL_LINE',
    'DIAGONAL_LINES',
    'LINE_TOUCHING_FREE',
    'LINE_NOT_TOUCHING_FREE',
    'NON_CROSSING_LINES',
    'SQUARE',
    'RECTANGLE',
    'SMALL_X',
    'SMALL_T',
    'SMALL_CROSS',
    'BIG_T',
    'BIG_Z',
    'BIG_H',
    'BIG_L',
    'BIG_U',
    'HALF_HOUSE',
    'BIG_O',
    'CORNER_BALLS',
  ];

  static int remainingCells(
    List<List<int>> grid,
    Set<int> called,
    String pattern,
  ) {
    final lines = <Set<(int, int)>>[
      for (var r = 0; r < 5; r++) {for (var c = 0; c < 5; c++) (r, c)},
      for (var c = 0; c < 5; c++) {for (var r = 0; r < 5; r++) (r, c)},
      {for (var i = 0; i < 5; i++) (i, i)},
      {for (var i = 0; i < 5; i++) (i, 4 - i)},
    ];
    if (pattern == 'LINE') return _minimum(grid, called, lines);
    if (pattern == 'LINE_ANYWHERE') return _minimum(grid, called, lines);
    if (pattern == 'HORIZONTAL_LINE') {
      return _minimum(grid, called, lines.take(5));
    }
    if (pattern == 'VERTICAL_LINE') {
      return _minimum(grid, called, lines.skip(5).take(5));
    }
    if (pattern == 'DIAGONAL_LINES') {
      return _minimum(grid, called, lines.skip(10));
    }
    if (pattern == 'LINE_TOUCHING_FREE') {
      return _minimum(
        grid,
        called,
        lines.where((line) => line.contains((2, 2))),
      );
    }
    if (pattern == 'LINE_NOT_TOUCHING_FREE') {
      return _minimum(
        grid,
        called,
        lines.where((line) => !line.contains((2, 2))),
      );
    }
    if (pattern == 'NON_CROSSING_LINES') {
      var best = 25;
      for (var i = 0; i < lines.length; i++) {
        for (var j = i + 1; j < lines.length; j++) {
          if (lines[i].intersection(lines[j]).isNotEmpty) continue;
          final needed = _needed(grid, called, {...lines[i], ...lines[j]});
          if (needed < best) best = needed;
        }
      }
      return best;
    }
    if (pattern == 'MOST_CALLED') {
      return grid
          .expand((row) => row)
          .where((number) => number != 0 && !called.contains(number))
          .length;
    }
    if (pattern == 'TWO_LINES') {
      var best = 25;
      for (var i = 0; i < lines.length; i++) {
        for (var j = i + 1; j < lines.length; j++) {
          final needed = _needed(grid, called, {...lines[i], ...lines[j]});
          if (needed < best) best = needed;
        }
      }
      return best;
    }
    if (pattern == 'FULL_HOUSE') {
      return _needed(grid, called, {
        for (var r = 0; r < 5; r++)
          for (var c = 0; c < 5; c++) (r, c),
      });
    }
    final shapes = switch (pattern) {
      'SQUARE' => _rectangles(squareOnly: true),
      'RECTANGLE' => _rectangles(squareOnly: false),
      'SMALL_X' => _smallX,
      'SMALL_T' => _smallT,
      'SMALL_CROSS' => _smallCross,
      'BIG_T' => _shapes['T_SHAPE'],
      'BIG_Z' => _bigZ,
      'BIG_H' => _bigH,
      'BIG_L' => _shapes['L_SHAPE'],
      'BIG_U' => _bigU,
      'CORNER_BALLS' => _shapes['FOUR_CORNERS'],
      'HALF_HOUSE' => _halfHouse,
      'BIG_O' => _shapes['OUTER_FRAME'],
      _ => _shapes[pattern],
    };
    if (shapes == null || shapes.isEmpty) return 25;
    return _minimum(grid, called, shapes);
  }

  static List<TicketRank> rank({
    required List<TicketResponse> tickets,
    required Set<int> called,
    required List<String> configuredPatterns,
    List<String>? configuredPrizePatterns,
    required List<String> priorities,
    required Map<String, String> claimStatuses,
    required bool claimWindowOpen,
  }) {
    final configured = supportedPatterns
        .where(
          (pattern) =>
              configuredPatterns.contains(pattern) &&
              (configuredPrizePatterns == null ||
                  configuredPrizePatterns.contains(pattern)),
        )
        .toList();
    if (configured.isEmpty) return const [];
    final configuredPriorities = priorities.where(configured.contains).toList();
    final ordered = <String>[
      ...configuredPriorities,
      ...configured.where((pattern) => !configuredPriorities.contains(pattern)),
    ];
    final result = tickets.map((ticket) {
      final winnerPatterns = ordered
          .where(ticket.eligibleClaimPatterns.contains)
          .toList();
      final pattern = winnerPatterns.isNotEmpty
          ? winnerPatterns.first
          : configuredPriorities.isNotEmpty
          ? configuredPriorities.first
          : configured.reduce(
              (best, next) =>
                  remainingCells(ticket.grid, called, next) <
                      remainingCells(ticket.grid, called, best)
                  ? next
                  : best,
            );
      final recordedStatus = _claimState(
        claimStatuses['${ticket.id}:$pattern'],
      );
      final status = winnerPatterns.isEmpty
          ? (claimWindowOpen
                ? TicketClaimState.approaching
                : TicketClaimState.closed)
          : recordedStatus != TicketClaimState.claimable
          ? recordedStatus
          : claimWindowOpen
          ? TicketClaimState.claimable
          : TicketClaimState.closed;
      return TicketRank(
        ticket: ticket,
        pattern: pattern,
        remainingCells: remainingCells(ticket.grid, called, pattern),
        claimState: status,
      );
    }).toList();
    result.sort((a, b) {
      final aClaim = a.claimState == TicketClaimState.claimable ? 0 : 1;
      final bClaim = b.claimState == TicketClaimState.claimable ? 0 : 1;
      final claimOrder = aClaim.compareTo(bClaim);
      if (claimOrder != 0) return claimOrder;
      for (final priority in configuredPriorities) {
        final priorityOrder = remainingCells(
          a.ticket.grid,
          called,
          priority,
        ).compareTo(remainingCells(b.ticket.grid, called, priority));
        if (priorityOrder != 0) return priorityOrder;
      }
      final patternOrder = ordered
          .indexOf(a.pattern)
          .compareTo(ordered.indexOf(b.pattern));
      if (patternOrder != 0) return patternOrder;
      final remaining = a.remainingCells.compareTo(b.remainingCells);
      return remaining != 0 ? remaining : a.ticket.id.compareTo(b.ticket.id);
    });
    return result;
  }

  static TicketClaimState _claimState(String? status) =>
      switch (status?.toUpperCase()) {
        'APPROVED' => TicketClaimState.awarded,
        'PENDING_REVIEW' || 'PENDING' => TicketClaimState.submitted,
        'REJECTED' || 'FAILED_SERVER_VALIDATION' => TicketClaimState.rejected,
        'CLOSED' => TicketClaimState.closed,
        _ => TicketClaimState.claimable,
      };

  static int _minimum(
    List<List<int>> grid,
    Set<int> called,
    Iterable<Set<(int, int)>> patterns,
  ) {
    var best = 25;
    for (final pattern in patterns) {
      final remaining = _needed(grid, called, pattern);
      if (remaining < best) best = remaining;
    }
    return best;
  }

  static int _needed(
    List<List<int>> grid,
    Set<int> called,
    Set<(int, int)> cells,
  ) => cells.where((cell) {
    final number = grid[cell.$1][cell.$2];
    return number != 0 && !called.contains(number);
  }).length;

  static List<Set<(int, int)>> _rectangles({required bool squareOnly}) => [
    for (var height = 2; height <= 5; height++)
      for (var width = 2; width <= 5; width++)
        if (squareOnly ? height == width : height != width)
          for (var top = 0; top <= 5 - height; top++)
            for (var left = 0; left <= 5 - width; left++)
              {
                for (var row = top; row < top + height; row++)
                  for (var column = left; column < left + width; column++)
                    (row, column),
              },
  ];

  static final List<Set<(int, int)>> _smallX = [
    {(1, 1), (1, 3), (2, 2), (3, 1), (3, 3)},
  ];
  static final List<Set<(int, int)>> _smallT = [
    {(1, 1), (1, 2), (1, 3), (2, 2), (3, 2)},
    {(3, 1), (3, 2), (3, 3), (2, 2), (1, 2)},
    {(1, 1), (2, 1), (3, 1), (2, 2), (2, 3)},
    {(1, 3), (2, 3), (3, 3), (2, 2), (2, 1)},
  ];
  static final List<Set<(int, int)>> _smallCross = [
    {(1, 2), (2, 1), (2, 2), (2, 3), (3, 2)},
  ];
  static final List<Set<(int, int)>> _bigZ = [
    {
      for (var column = 0; column < 5; column++) (0, column),
      for (var index = 1; index < 4; index++) (index, 4 - index),
      for (var column = 0; column < 5; column++) (4, column),
    },
  ];
  static final List<Set<(int, int)>> _bigH = [
    {
      for (var row = 0; row < 5; row++) (row, 0),
      for (var row = 0; row < 5; row++) (row, 4),
      for (var column = 0; column < 5; column++) (2, column),
    },
  ];
  static final List<Set<(int, int)>> _bigU = [
    {
      for (var row = 0; row < 5; row++) (row, 0),
      for (var row = 0; row < 5; row++) (row, 4),
      for (var column = 0; column < 5; column++) (4, column),
    },
  ];
  static final List<Set<(int, int)>> _halfHouse = [
    for (var top = 0; top <= 3; top++)
      {
        for (var row = top; row < top + 2; row++)
          for (var column = 0; column < 5; column++) (row, column),
      },
    for (var left = 0; left <= 3; left++)
      {
        for (var row = 0; row < 5; row++)
          for (var column = left; column < left + 2; column++) (row, column),
      },
  ];

  static final Map<String, List<Set<(int, int)>>> _shapes = {
    'FOUR_CORNERS': [
      {(0, 0), (0, 4), (4, 0), (4, 4)},
    ],
    'POSTAGE_STAMP': [
      for (final top in [0, 3])
        for (final left in [0, 3])
          {
            for (var r = top; r < top + 2; r++)
              for (var c = left; c < left + 2; c++) (r, c),
          },
    ],
    'X_SHAPE': [
      {(0, 0), (0, 4), (1, 1), (1, 3), (2, 2), (3, 1), (3, 3), (4, 0), (4, 4)},
    ],
    'T_SHAPE': [
      {(0, 0), (0, 1), (0, 2), (0, 3), (0, 4), (1, 2), (2, 2), (3, 2), (4, 2)},
      {(4, 0), (4, 1), (4, 2), (4, 3), (4, 4), (0, 2), (1, 2), (2, 2), (3, 2)},
      {(0, 0), (1, 0), (2, 0), (3, 0), (4, 0), (2, 1), (2, 2), (2, 3), (2, 4)},
      {(0, 4), (1, 4), (2, 4), (3, 4), (4, 4), (2, 0), (2, 1), (2, 2), (2, 3)},
    ],
    'L_SHAPE': [
      {(0, 0), (1, 0), (2, 0), (3, 0), (4, 0), (4, 1), (4, 2), (4, 3), (4, 4)},
      {(0, 0), (0, 1), (0, 2), (0, 3), (0, 4), (1, 0), (2, 0), (3, 0), (4, 0)},
      {(0, 0), (0, 1), (0, 2), (0, 3), (0, 4), (1, 4), (2, 4), (3, 4), (4, 4)},
      {(4, 0), (4, 1), (4, 2), (4, 3), (4, 4), (0, 4), (1, 4), (2, 4), (3, 4)},
    ],
    'OUTER_FRAME': [
      {
        for (var r = 0; r < 5; r++)
          for (var c = 0; c < 5; c++)
            if (r == 0 || r == 4 || c == 0 || c == 4) (r, c),
      },
    ],
  };
}
