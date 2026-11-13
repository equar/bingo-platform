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

  static String money(int minor, String currency) =>
      '${(minor / 100).toStringAsFixed(minor % 100 == 0 ? 0 : 2)} $currency';

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final colors = Theme.of(context).colorScheme;
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
      style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(48)),
      onPressed: () => Navigator.pop(context),
      child: Text(l.claimDialogDismiss),
    );
    return Dialog(
      key: const ValueKey('winner-celebration-dialog'),
      backgroundColor: colors.surfaceContainerHigh,
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
                        Icon(
                          Icons.emoji_events,
                          color: colors.primary,
                          size: 30,
                        ),
                        const SizedBox(width: 10),
                        Flexible(
                          child: Text(
                            l.celebrationBingo,
                            textAlign: TextAlign.center,
                            style: TextStyle(
                              color: colors.primary,
                              fontSize: 30,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Icon(
                          Icons.emoji_events,
                          color: colors.primary,
                          size: 30,
                        ),
                      ],
                    ),
                    const SizedBox(height: 20),
                    Text(
                      l.celebrationWinningTickets(tickets.length),
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: colors.onSurface,
                        fontSize: 22,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      l.celebrationCongratulations,
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: colors.onSurfaceVariant,
                        fontSize: 16,
                      ),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      total,
                      key: const ValueKey('celebration-grand-total'),
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: colors.primary,
                        fontSize: 38,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 20),
                    Container(
                      decoration: BoxDecoration(
                        color: colors.surfaceContainerHighest,
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
                              Divider(color: colors.outlineVariant, height: 1),
                            Padding(
                              padding: const EdgeInsets.symmetric(vertical: 16),
                              child: Row(
                                children: [
                                  Icon(
                                    Icons.emoji_events_outlined,
                                    color: colors.primary,
                                    size: 22,
                                  ),
                                  const SizedBox(width: 10),
                                  Expanded(
                                    child: Text(
                                      l.celebrationTicket(tickets[index].id),
                                      style: TextStyle(
                                        color: colors.onSurface,
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
                                      style: TextStyle(
                                        color: colors.primary,
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
                          style: TextStyle(
                            color: colors.onSurfaceVariant,
                            fontSize: 16,
                          ),
                        ),
                        Text(
                          total,
                          style: TextStyle(
                            color: colors.primary,
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
                          color: colors.tertiaryContainer,
                          borderRadius: BorderRadius.circular(24),
                        ),
                        child: Text(
                          l.celebrationTicketsWon(tickets.length, totalTickets),
                          style: TextStyle(
                            color: colors.onTertiaryContainer,
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
