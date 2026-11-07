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

    @Test
    void fourCornersRequiresEveryCorner() {
        assertTrue(evaluator.matches(card, Set.of(1, 5, 20, 24), BingoPatternType.FOUR_CORNERS));
        assertFalse(evaluator.matches(card, Set.of(1, 5, 20), BingoPatternType.FOUR_CORNERS));
    }

    @Test
    void postageStampAcceptsAnyCornerBlock() {
        assertTrue(evaluator.matches(card, Set.of(1, 2, 6, 7), BingoPatternType.POSTAGE_STAMP));
        assertTrue(evaluator.matches(card, Set.of(4, 5, 9, 10), BingoPatternType.POSTAGE_STAMP));
        assertTrue(evaluator.matches(card, Set.of(15, 16, 20, 21), BingoPatternType.POSTAGE_STAMP));
        assertTrue(evaluator.matches(card, Set.of(18, 19, 23, 24), BingoPatternType.POSTAGE_STAMP));
        assertFalse(evaluator.matches(card, Set.of(1, 2, 6), BingoPatternType.POSTAGE_STAMP));
    }

    @Test
    void xShapeRequiresBothDiagonals() {
        assertTrue(evaluator.matches(card, Set.of(1, 5, 7, 9, 16, 18, 20, 24), BingoPatternType.X_SHAPE));
        assertFalse(evaluator.matches(card, Set.of(1, 7, 18, 24), BingoPatternType.X_SHAPE));
    }

    @Test
    void tShapeAcceptsRotations() {
        assertTrue(evaluator.matches(card, Set.of(1, 2, 3, 4, 5, 8, 17, 22), BingoPatternType.T_SHAPE));
        assertTrue(evaluator.matches(card, Set.of(3, 8, 17, 20, 21, 22, 23, 24), BingoPatternType.T_SHAPE));
        assertTrue(evaluator.matches(card, Set.of(1, 6, 11, 12, 13, 14, 15, 20), BingoPatternType.T_SHAPE));
        assertTrue(evaluator.matches(card, Set.of(5, 10, 11, 12, 13, 14, 19, 24), BingoPatternType.T_SHAPE));
        assertFalse(evaluator.matches(card, Set.of(1, 2, 3, 4, 8, 17, 22), BingoPatternType.T_SHAPE));
    }

    @Test
    void lShapeAcceptsRotations() {
        assertTrue(evaluator.matches(card, Set.of(1, 6, 11, 15, 20, 21, 22, 23, 24), BingoPatternType.L_SHAPE));
        assertTrue(evaluator.matches(card, Set.of(1, 2, 3, 4, 5, 6, 11, 15, 20), BingoPatternType.L_SHAPE));
        assertTrue(evaluator.matches(card, Set.of(1, 2, 3, 4, 5, 10, 14, 19, 24), BingoPatternType.L_SHAPE));
        assertTrue(evaluator.matches(card, Set.of(5, 10, 14, 19, 20, 21, 22, 23, 24), BingoPatternType.L_SHAPE));
        assertFalse(evaluator.matches(card, Set.of(1, 6, 11, 15, 20, 21, 22, 23), BingoPatternType.L_SHAPE));
    }

    @Test
    void outerFrameRequiresTheEntireBorder() {
        Set<Integer> border = Set.of(1, 2, 3, 4, 5, 6, 10, 11, 14, 15, 19, 20, 21, 22, 23, 24);
        assertTrue(evaluator.matches(card, border, BingoPatternType.OUTER_FRAME));
        assertFalse(evaluator.matches(card, Set.of(1, 2, 3, 4, 5, 6, 10, 11, 14, 15, 19, 20, 21, 22, 23),
                BingoPatternType.OUTER_FRAME));
    }
}
