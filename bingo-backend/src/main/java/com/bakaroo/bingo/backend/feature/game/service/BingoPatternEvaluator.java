package com.bakaroo.bingo.backend.feature.game.service;

import com.bakaroo.bingo.backend.feature.game.entity.BingoPatternType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BingoPatternEvaluator {

    private static final List<List<String>> FOUR_CORNERS = List.of(
            shape("#...#", ".....", ".....", ".....", "#...#"));
    private static final List<List<String>> POSTAGE_STAMP = List.of(
            shape("##...", "##...", ".....", ".....", "....."),
            shape("...##", "...##", ".....", ".....", "....."),
            shape(".....", ".....", ".....", "##...", "##..."),
            shape(".....", ".....", ".....", "...##", "...##"));
    private static final List<List<String>> X_SHAPE = List.of(
            shape("#...#", ".#.#.", "..#..", ".#.#.", "#...#"));
    private static final List<List<String>> T_SHAPE = List.of(
            shape("#####", "..#..", "..#..", "..#..", "..#.."),
            shape("..#..", "..#..", "..#..", "..#..", "#####"),
            shape("#....", "#....", "#####", "#....", "#...."),
            shape("....#", "....#", "#####", "....#", "....#"));
    private static final List<List<String>> L_SHAPE = List.of(
            shape("#....", "#....", "#....", "#....", "#####"),
            shape("#####", "#....", "#....", "#....", "#...."),
            shape("#####", "....#", "....#", "....#", "....#"),
            shape("....#", "....#", "....#", "....#", "#####"));
    private static final List<List<String>> OUTER_FRAME = List.of(
            shape("#####", "#...#", "#...#", "#...#", "#####"));

    public boolean matches(BingoCard card, Set<Integer> calledNumbers, BingoPatternType patternType) {
        return switch (patternType) {
            case LINE -> completedLines(card, calledNumbers) >= 1;
            case TWO_LINES -> completedLines(card, calledNumbers) >= 2;
            case FULL_HOUSE -> isFullHouse(card, calledNumbers);
            case FOUR_CORNERS -> matchesAnyShape(card, calledNumbers, FOUR_CORNERS);
            case POSTAGE_STAMP -> matchesAnyShape(card, calledNumbers, POSTAGE_STAMP);
            case X_SHAPE -> matchesAnyShape(card, calledNumbers, X_SHAPE);
            case T_SHAPE -> matchesAnyShape(card, calledNumbers, T_SHAPE);
            case L_SHAPE -> matchesAnyShape(card, calledNumbers, L_SHAPE);
            case OUTER_FRAME -> matchesAnyShape(card, calledNumbers, OUTER_FRAME);
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

    private boolean matchesAnyShape(BingoCard card, Set<Integer> calledNumbers, List<List<String>> shapes) {
        return shapes.stream().anyMatch(shape -> matchesShape(card, calledNumbers, shape));
    }

    private boolean matchesShape(BingoCard card, Set<Integer> calledNumbers, List<String> shape) {
        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 5; column++) {
                if (shape.get(row).charAt(column) != '#') {
                    continue;
                }
                int value = card.grid().get(row).get(column);
                if (value != 0 && !calledNumbers.contains(value)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<String> shape(String... rows) {
        return List.of(rows);
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