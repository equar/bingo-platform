package com.bakaroo.bingo.backend.feature.game;

import com.bakaroo.bingo.backend.feature.game.entity.BingoPatternType;
import com.bakaroo.bingo.backend.feature.game.service.BingoCard;
import com.bakaroo.bingo.backend.feature.game.service.BingoCardGenerator;
import com.bakaroo.bingo.backend.feature.game.service.BingoPatternEvaluator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BingoGameRulesTest {

    private final BingoCardGenerator generator = new BingoCardGenerator();
    private final BingoPatternEvaluator evaluator = new BingoPatternEvaluator();

    @Test
    void generatedCardsRespectGridRules() {
        BingoCard card = generator.generate();
        assertDoesNotThrow(() -> generator.validate(card));
        assertTrue(card.grid().size() == 5);
        assertTrue(card.grid().get(2).get(2) == 0);

        Set<Integer> values = new HashSet<>();
        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 5; column++) {
                int value = card.grid().get(row).get(column);
                if (row == 2 && column == 2) {
                    continue;
                }
                assertTrue(value >= columnRangeMin(column) && value <= columnRangeMax(column));
                assertTrue(values.add(value));
            }
        }
        assertEquals(24, values.size());
    }

    @Test
    void invalidCenterCellIsRejected() {
        BingoCard card = new BingoCard(List.of(
                List.of(1, 16, 31, 46, 61),
                List.of(2, 17, 32, 47, 62),
                List.of(3, 18, 99, 48, 63),
                List.of(4, 19, 34, 49, 64),
                List.of(5, 20, 35, 50, 65)));
        assertThrows(IllegalArgumentException.class, () -> generator.validate(card));
    }

    @Test
    void evaluatorRecognizesLineTwoLinesAndFullHouse() {
        BingoCard card = new BingoCard(List.of(
                List.of(1, 16, 31, 46, 61),
                List.of(2, 17, 32, 47, 62),
                List.of(3, 18, 0, 48, 63),
                List.of(4, 19, 34, 49, 64),
                List.of(5, 20, 35, 50, 65)));

        Set<Integer> lineNumbers = new HashSet<>(List.of(1, 16, 31, 46, 61));
        assertTrue(evaluator.matches(card, lineNumbers, BingoPatternType.LINE));
        assertFalse(evaluator.matches(card, lineNumbers, BingoPatternType.TWO_LINES));

        Set<Integer> twoLines = new HashSet<>(List.of(1, 16, 31, 46, 61, 2, 17, 32, 47, 62));
        assertTrue(evaluator.matches(card, twoLines, BingoPatternType.TWO_LINES));

        Set<Integer> fullHouse = new HashSet<>(evaluator.allTicketNumbers(card));
        assertTrue(evaluator.matches(card, fullHouse, BingoPatternType.FULL_HOUSE));
    }

    @Test
    void evaluatorRejectsNearMissPatterns() {
        BingoCard card = new BingoCard(List.of(
                List.of(1, 16, 31, 46, 61),
                List.of(2, 17, 32, 47, 62),
                List.of(3, 18, 0, 48, 63),
                List.of(4, 19, 34, 49, 64),
                List.of(5, 20, 35, 50, 65)));

        Set<Integer> nearMiss = new HashSet<>(List.of(1, 16, 31, 46, 2, 17, 32, 47, 63));
        assertFalse(evaluator.matches(card, nearMiss, BingoPatternType.TWO_LINES));
        assertFalse(evaluator.matches(card, nearMiss, BingoPatternType.FULL_HOUSE));
    }

    private int columnRangeMin(int column) {
        return switch (column) {
            case 0 -> 1;
            case 1 -> 16;
            case 2 -> 31;
            case 3 -> 46;
            case 4 -> 61;
            default -> throw new IllegalArgumentException("Unexpected column " + column);
        };
    }

    private int columnRangeMax(int column) {
        return switch (column) {
            case 0 -> 15;
            case 1 -> 30;
            case 2 -> 45;
            case 3 -> 60;
            case 4 -> 75;
            default -> throw new IllegalArgumentException("Unexpected column " + column);
        };
    }
}