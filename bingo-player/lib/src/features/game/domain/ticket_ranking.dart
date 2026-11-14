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
    final shapes = _shapes[pattern];
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
    final ordered = <String>[
      ...priorities.where(configured.contains),
      ...configured.where((pattern) => !priorities.contains(pattern)),
    ];
    final result = tickets.map((ticket) {
      final winnerPatterns = ordered
          .where(ticket.eligibleClaimPatterns.contains)
          .toList();
      final pattern = winnerPatterns.isNotEmpty
          ? winnerPatterns.first
          : ordered.reduce(
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
