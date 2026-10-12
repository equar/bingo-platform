package com.equar.bingo.backend.feature.game.service;

import com.equar.bingo.backend.feature.game.entity.BingoPatternType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BingoPatternEvaluator {

    public boolean matches(BingoCard card, Set<Integer> calledNumbers, BingoPatternType patternType) {
        return switch (patternType) {
            case LINE -> completedLines(card, calledNumbers) >= 1;
            case TWO_LINES -> completedLines(card, calledNumbers) >= 2;
            case FULL_HOUSE -> isFullHouse(card, calledNumbers);
        };
    }

    public int completedLines(BingoCard card, Set<Integer> calledNumbers) {
        int count = 0;
        for (int row = 0; row < 5; row++) {
            if (rowCompleted(card.grid().get(row), calledNumbers)) {
                count++;
            }
        }

        for (int column = 0; column < 5; column++) {
            if (columnCompleted(card.grid(), column, calledNumbers)) {
                count++;
            }
        }

        if (diagonalCompleted(card.grid(), true, calledNumbers)) {
            count++;
        }
        if (diagonalCompleted(card.grid(), false, calledNumbers)) {
            count++;
        }
        return count;
    }

    public boolean isFullHouse(BingoCard card, Set<Integer> calledNumbers) {
        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 5; column++) {
                if (row == 2 && column == 2) {
                    continue;
                }
                if (!calledNumbers.contains(card.grid().get(row).get(column))) {
                    return false;
                }
            }
        }
        return true;
    }

    public Set<Integer> allTicketNumbers(BingoCard card) {
        Set<Integer> numbers = new HashSet<>();
        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 5; column++) {
                int value = card.grid().get(row).get(column);
                if (value != 0) {
                    numbers.add(value);
                }
            }
        }
        return numbers;
    }

    private boolean rowCompleted(List<Integer> rowValues, Set<Integer> calledNumbers) {
        return rowValues.stream().allMatch(value -> value == 0 || calledNumbers.contains(value));
    }

    private boolean columnCompleted(List<List<Integer>> grid, int column, Set<Integer> calledNumbers) {
        for (int row = 0; row < 5; row++) {
            int value = grid.get(row).get(column);
            if (value != 0 && !calledNumbers.contains(value)) {
                return false;
            }
        }
        return true;
    }

    private boolean diagonalCompleted(List<List<Integer>> grid, boolean mainDiagonal, Set<Integer> calledNumbers) {
        for (int index = 0; index < 5; index++) {
            int row = index;
            int column = mainDiagonal ? index : 4 - index;
            int value = grid.get(row).get(column);
            if (value != 0 && !calledNumbers.contains(value)) {
                return false;
            }
        }
        return true;
    }
}