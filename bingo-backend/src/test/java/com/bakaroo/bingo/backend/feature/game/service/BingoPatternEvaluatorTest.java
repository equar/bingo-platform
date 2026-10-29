package com.bakaroo.bingo.backend.feature.game.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bakaroo.bingo.backend.feature.game.entity.BingoPatternType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BingoPatternEvaluatorTest {

    private final BingoPatternEvaluator evaluator = new BingoPatternEvaluator();
    private final BingoCard card = new BingoCard(List.of(
            List.of(1, 2, 3, 4, 5),
            List.of(6, 7, 8, 9, 10),
            List.of(11, 12, 0, 13, 14),
            List.of(15, 16, 17, 18, 19),
            List.of(20, 21, 22, 23, 24)));

    @Test
    void lineRequiresEveryNonFreeNumberInOneLine() {
        assertTrue(evaluator.matches(card, Set.of(1, 2, 3, 4, 5), BingoPatternType.LINE));
        assertFalse(evaluator.matches(card, Set.of(1, 2, 3, 4), BingoPatternType.LINE));
    }

    @Test
    void twoLinesRequiresTwoCompletedRowsColumnsOrDiagonals() {
        assertTrue(evaluator.matches(card, Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), BingoPatternType.TWO_LINES));
        assertFalse(evaluator.matches(card, Set.of(1, 2, 3, 4, 5), BingoPatternType.TWO_LINES));
    }

    @Test
    void fullHouseRequiresEveryNonFreeNumberAndTreatsCenterAsFree() {
        Set<Integer> allTicketNumbers = evaluator.allTicketNumbers(card);
        assertEquals(24, allTicketNumbers.size());
        assertTrue(evaluator.matches(card, allTicketNumbers, BingoPatternType.FULL_HOUSE));

        Set<Integer> oneMissing = new HashSet<>(allTicketNumbers);
        oneMissing.remove(24);
        assertFalse(evaluator.matches(card, oneMissing, BingoPatternType.FULL_HOUSE));
    }
}
