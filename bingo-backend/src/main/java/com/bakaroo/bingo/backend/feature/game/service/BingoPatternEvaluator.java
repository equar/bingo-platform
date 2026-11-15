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
    private static final List<List<String>> SMALL_X = List.of(
            shape(".....", ".#.#.", "..#..", ".#.#.", "....."));
    private static final List<List<String>> SMALL_T = List.of(
            shape(".....", ".###.", "..#..", "..#..", "....."),
            shape(".....", "..#..", "..#..", ".###.", "....."),
            shape(".....", ".#...", ".###.", ".#...", "....."),
            shape(".....", "...#.", ".###.", "...#.", "....."));
    private static final List<List<String>> SMALL_CROSS = List.of(
            shape(".....", "..#..", ".###.", "..#..", "....."));
    private static final List<List<String>> BIG_Z = List.of(
            shape("#####", "....#", "...#.", "..#..", "#####"));
    private static final List<List<String>> BIG_H = List.of(
            shape("#...#", "#...#", "#####", "#...#", "#...#"));
    private static final List<List<String>> BIG_U = List.of(
            shape("#...#", "#...#", "#...#", "#...#", "#####"));
    private static final List<List<String>> HALF_HOUSE = halfHouseShapes();

    public boolean matches(BingoCard card, Set<Integer> calledNumbers, BingoPatternType patternType) {
        return switch (patternType) {
            case LINE -> completedLines(card, calledNumbers) >= 1;
            case TWO_LINES -> completedLines(card, calledNumbers) >= 2;
            case FULL_HOUSE -> isFullHouse(card, calledNumbers);
            case FOUR_CORNERS -> matchesAnyShape(card, calledNumbers, FOUR_CORNERS);
            case POSTAGE_STAMP -> matchesAnyShape(card, calledNumbers, POSTAGE_STAMP);
            case X_SHAPE -> matchesAnyShape(card, calledNumbers, X_SHAPE);
            case T_SHAPE, BIG_T -> matchesAnyShape(card, calledNumbers, T_SHAPE);
            case L_SHAPE, BIG_L -> matchesAnyShape(card, calledNumbers, L_SHAPE);
            case OUTER_FRAME -> matchesAnyShape(card, calledNumbers, OUTER_FRAME);
            case CORNER_BALLS -> matchesAnyShape(card, calledNumbers, FOUR_CORNERS);
            case HORIZONTAL_LINE -> horizontalLines(card, calledNumbers) >= 1;
            case VERTICAL_LINE -> verticalLines(card, calledNumbers) >= 1;
            case DIAGONAL_LINES -> diagonalLines(card, calledNumbers) == 2;
            case LINE_TOUCHING_FREE -> freeCenterLines(card, calledNumbers, true);
            case LINE_NOT_TOUCHING_FREE -> freeCenterLines(card, calledNumbers, false);
            case NON_CROSSING_LINES -> hasNonCrossingLines(card, calledNumbers);
            case SQUARE -> matchesAnyShape(card, calledNumbers, rectangleShapes(true));
            case RECTANGLE -> matchesAnyShape(card, calledNumbers, rectangleShapes(false));
            case SMALL_X -> matchesAnyShape(card, calledNumbers, SMALL_X);
            case SMALL_T -> matchesAnyShape(card, calledNumbers, SMALL_T);
            case SMALL_CROSS -> matchesAnyShape(card, calledNumbers, SMALL_CROSS);
            case BIG_Z -> matchesAnyShape(card, calledNumbers, BIG_Z);
            case BIG_H -> matchesAnyShape(card, calledNumbers, BIG_H);
            case BIG_U -> matchesAnyShape(card, calledNumbers, BIG_U);
            case HALF_HOUSE -> matchesAnyShape(card, calledNumbers, HALF_HOUSE);
            case BIG_O -> matchesAnyShape(card, calledNumbers, OUTER_FRAME);
        };
    }

    public int completedLines(BingoCard card, Set<Integer> calledNumbers) {
        return horizontalLines(card, calledNumbers) + verticalLines(card, calledNumbers)
                + diagonalLines(card, calledNumbers);
    }

    private int horizontalLines(BingoCard card, Set<Integer> calledNumbers) {
        int count = 0;
        for (int row = 0; row < 5; row++) {
            if (rowCompleted(card.grid().get(row), calledNumbers)) {
                count++;
            }
        }

        return count;
    }

    private int verticalLines(BingoCard card, Set<Integer> calledNumbers) {
        int count = 0;
        for (int column = 0; column < 5; column++) {
            if (columnCompleted(card.grid(), column, calledNumbers)) {
                count++;
            }
        }

        return count;
    }

    private int diagonalLines(BingoCard card, Set<Integer> calledNumbers) {
        int count = 0;
        if (diagonalCompleted(card.grid(), true, calledNumbers)) count++;
        if (diagonalCompleted(card.grid(), false, calledNumbers)) count++;
        return count;
    }

    private boolean freeCenterLines(BingoCard card, Set<Integer> calledNumbers, boolean touching) {
        for (int row = 0; row < 5; row++) {
            if ((row == 2) == touching && rowCompleted(card.grid().get(row), calledNumbers)) return true;
        }
        for (int column = 0; column < 5; column++) {
            if ((column == 2) == touching && columnCompleted(card.grid(), column, calledNumbers)) return true;
        }
        return touching && (diagonalCompleted(card.grid(), true, calledNumbers)
                || diagonalCompleted(card.grid(), false, calledNumbers));
    }

    private boolean hasNonCrossingLines(BingoCard card, Set<Integer> calledNumbers) {
        List<Set<Integer>> completed = new java.util.ArrayList<>();
        for (int row = 0; row < 5; row++) {
            if (rowCompleted(card.grid().get(row), calledNumbers)) {
                Set<Integer> cells = new HashSet<>();
                for (int col = 0; col < 5; col++) cells.add(row * 5 + col);
                completed.add(cells);
            }
        }
        for (int col = 0; col < 5; col++) {
            if (columnCompleted(card.grid(), col, calledNumbers)) {
                Set<Integer> cells = new HashSet<>();
                for (int row = 0; row < 5; row++) cells.add(row * 5 + col);
                completed.add(cells);
            }
        }
        if (diagonalCompleted(card.grid(), true, calledNumbers)) completed.add(Set.of(0, 6, 12, 18, 24));
        if (diagonalCompleted(card.grid(), false, calledNumbers)) completed.add(Set.of(4, 8, 12, 16, 20));
        for (int i = 0; i < completed.size(); i++) {
            for (int j = i + 1; j < completed.size(); j++) {
                if (java.util.Collections.disjoint(completed.get(i), completed.get(j))) return true;
            }
        }
        return false;
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

    private static List<List<String>> rectangleShapes(boolean squareOnly) {
        List<List<String>> result = new java.util.ArrayList<>();
        for (int height = 2; height <= 5; height++) {
            for (int width = 2; width <= 5; width++) {
                if ((height == width) != squareOnly) continue;
                for (int top = 0; top <= 5 - height; top++) {
                    for (int left = 0; left <= 5 - width; left++) {
                        String[] rows = {".....", ".....", ".....", ".....", "....."};
                        for (int row = top; row < top + height; row++) {
                            char[] cells = rows[row].toCharArray();
                            for (int col = left; col < left + width; col++) cells[col] = '#';
                            rows[row] = new String(cells);
                        }
                        result.add(List.of(rows));
                    }
                }
            }
        }
        return result;
    }

    private static List<List<String>> halfHouseShapes() {
        List<List<String>> result = new java.util.ArrayList<>();
        for (int top = 0; top <= 3; top++) {
            String[] rows = {".....", ".....", ".....", ".....", "....."};
            for (int row = top; row < top + 2; row++) rows[row] = "#####";
            result.add(List.of(rows));
        }
        for (int left = 0; left <= 3; left++) {
            String[] rows = new String[5];
            for (int row = 0; row < 5; row++) {
                char[] cells = ".....".toCharArray();
                cells[left] = '#';
                cells[left + 1] = '#';
                rows[row] = new String(cells);
            }
            result.add(List.of(rows));
        }
        return result;
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
