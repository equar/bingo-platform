import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:bingo_app/src/features/game/domain/ticket_models.dart';
import 'package:flutter/material.dart';

/// Displays server-calculated shares; the UI never estimates a split prize.
class WinnerCelebrationDialog extends StatelessWidget {
  const WinnerCelebrationDialog({
    super.key,
    required this.tickets,
    required this.totalTickets,
  });
  final List<TicketResponse> tickets;
  final int totalTickets;
  static const navy = Color(0xFF142E46);
  static const gold = Color(0xFFFFD174);
  static const rowColor = Color(0xFF27465E);

  static String money(int minor, String currency) =>
      '${(minor / 100).toStringAsFixed(minor % 100 == 0 ? 0 : 2)} $currency';

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final totals = <String, int>{};
    for (final ticket in tickets) {
      totals.update(
        ticket.prizeCurrency,
        (value) => value + ticket.totalWinningMinor,
        ifAbsent: () => ticket.totalWinningMinor,
      );
    }
    final total = totals.entries.map((e) => money(e.value, e.key)).join('\n');
    final action = FilledButton(
      key: const ValueKey('winner-celebration-ok'),
      style: FilledButton.styleFrom(
        backgroundColor: gold,
        foregroundColor: navy,
        minimumSize: const Size.fromHeight(48),
      ),
      onPressed: () => Navigator.pop(context),
      child: Text(l.claimDialogDismiss),
    );
    return Dialog(
      key: const ValueKey('winner-celebration-dialog'),
      backgroundColor: navy,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: 560,
          maxHeight: MediaQuery.sizeOf(context).height * .85,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Flexible(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(20),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Icon(Icons.emoji_events, color: gold, size: 30),
                        const SizedBox(width: 10),
                        Flexible(
                          child: Text(
                            l.celebrationBingo,
                            textAlign: TextAlign.center,
                            style: const TextStyle(
                              color: gold,
                              fontSize: 30,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ),
                        const SizedBox(width: 10),
                        const Icon(Icons.emoji_events, color: gold, size: 30),
                      ],
                    ),
                    const SizedBox(height: 20),
                    Text(
                      l.celebrationWinningTickets(tickets.length),
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 22,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      l.celebrationCongratulations,
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        color: Color(0xFFCDDAE8),
                        fontSize: 16,
                      ),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      total,
                      key: const ValueKey('celebration-grand-total'),
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        color: gold,
                        fontSize: 38,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 20),
                    Container(
                      decoration: BoxDecoration(
                        color: rowColor,
                        borderRadius: BorderRadius.circular(16),
                      ),
                      padding: const EdgeInsets.symmetric(horizontal: 14),
                      child: Column(
                        children: [
                          for (
                            var index = 0;
                            index < tickets.length;
                            index++
                          ) ...[
                            if (index > 0)
                              const Divider(
                                color: Color(0xFF456077),
                                height: 1,
                              ),
                            Padding(
                              padding: const EdgeInsets.symmetric(vertical: 16),
                              child: Row(
                                children: [
                                  const Icon(
                                    Icons.emoji_events_outlined,
                                    color: gold,
                                    size: 22,
                                  ),
                                  const SizedBox(width: 10),
                                  Expanded(
                                    child: Text(
                                      l.celebrationTicket(tickets[index].id),
                                      style: const TextStyle(
                                        color: Colors.white,
                                        fontSize: 16,
                                      ),
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                  Flexible(
                                    child: Text(
                                      money(
                                        tickets[index].totalWinningMinor,
                                        tickets[index].prizeCurrency,
                                      ),
                                      textAlign: TextAlign.end,
                                      style: const TextStyle(
                                        color: gold,
                                        fontSize: 17,
                                        fontWeight: FontWeight.bold,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                    const SizedBox(height: 20),
                    Wrap(
                      alignment: WrapAlignment.spaceBetween,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      spacing: 12,
                      children: [
                        Text(
                          l.celebrationTotal,
                          style: const TextStyle(
                            color: Color(0xFFCDDAE8),
                            fontSize: 16,
                          ),
                        ),
                        Text(
                          total,
                          style: const TextStyle(
                            color: gold,
                            fontSize: 24,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 14),
                    Center(
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 6,
                        ),
                        decoration: BoxDecoration(
                          color: const Color(0xFF16483F),
                          borderRadius: BorderRadius.circular(24),
                        ),
                        child: Text(
                          l.celebrationTicketsWon(tickets.length, totalTickets),
                          style: const TextStyle(
                            color: Color(0xFF53E2A2),
                            fontSize: 14,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 20),
                  ],
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
              child: action,
            ),
          ],
        ),
      ),
    );
  }
}
