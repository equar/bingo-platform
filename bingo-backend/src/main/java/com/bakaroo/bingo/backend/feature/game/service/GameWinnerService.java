package com.bakaroo.bingo.backend.feature.game.service;

import com.bakaroo.bingo.backend.common.exception.ConflictException;
import com.bakaroo.bingo.backend.feature.game.entity.*;
import com.bakaroo.bingo.backend.feature.game.repository.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import org.springframework.stereotype.Service;

/** Winner eligibility and cent allocation depend on cards and calls, never claim/review order. */
@Service
public class GameWinnerService {
    private final TicketRepository tickets;
    private final DrawEventRepository draws;
    private final GameSnapshotMapper snapshots;
    private final BingoPatternEvaluator evaluator = new BingoPatternEvaluator();

    public GameWinnerService(TicketRepository tickets, DrawEventRepository draws, GameSnapshotMapper snapshots) {
        this.tickets = tickets;
        this.draws = draws;
        this.snapshots = snapshots;
    }

    public Winners winners(Long sessionId, BingoPatternType pattern) {
        List<Ticket> sold = tickets.findBySessionIdOrderByIdAsc(sessionId);
        Map<Long, BingoCard> cards = new LinkedHashMap<>();
        for (Ticket ticket : sold) cards.put(ticket.getId(), new BingoCard(snapshots.readGrid(ticket.getGridJson())));
        Set<Integer> called = new HashSet<>();
        for (DrawEvent draw : draws.findBySessionIdOrderBySequenceNoAsc(sessionId)) {
            called.add(draw.getNumberDrawn());
            List<Long> matching = cards.entrySet().stream()
                    .filter(entry -> evaluator.matches(entry.getValue(), called, pattern))
                    .map(Map.Entry::getKey).toList();
            if (!matching.isEmpty()) return new Winners(draw.getSequenceNo(), matching);
        }
        return new Winners(0, List.of());
    }

    public BigDecimal share(WinClaim claim) {
        if (!"EARLIEST_SPLIT_V1".equals(claim.getSession().getRulesVersion())) {
            throw new ConflictException("LEGACY_SESSION_REQUIRES_REVIEW", "Legacy awards require review under their original terms");
        }
        Winners result = winners(claim.getSession().getId(), claim.getClaimedPattern());
        int index = result.ticketIds().indexOf(claim.getTicket().getId());
        if (index < 0 || claim.getDrawSequenceSnapshot() < result.sequence()) {
            throw new ConflictException("NOT_EARLIEST_WINNER", "Only tickets completing this pattern on its earliest call qualify");
        }
        BigDecimal pool = snapshots.readPrizes(claim.getSession().getPrizesSnapshotJson()).stream()
                .filter(prize -> prize.prizeType() == claim.getClaimedPattern()).findFirst()
                .orElseThrow(() -> new ConflictException("PRIZE_NOT_CONFIGURED", "Prize is not configured")).amount();
        return split(pool, result.ticketIds().size(), index);
    }

    public static BigDecimal split(BigDecimal pool, int winners, int index) {
        if (winners < 1 || index < 0 || index >= winners) throw new IllegalArgumentException("Invalid winner index");
        BigInteger[] parts = pool.movePointRight(2).toBigIntegerExact().divideAndRemainder(BigInteger.valueOf(winners));
        // Stable ascending ticket IDs receive leftover cents; all shares sum exactly to the pool.
        return new BigDecimal(parts[0].add(BigInteger.valueOf(index < parts[1].intValueExact() ? 1 : 0)), 2);
    }

    public record Winners(int sequence, List<Long> ticketIds) { }
}
