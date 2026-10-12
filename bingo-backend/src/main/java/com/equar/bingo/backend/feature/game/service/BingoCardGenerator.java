package com.equar.bingo.backend.feature.game.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BingoCardGenerator {

    private static final int[][] COLUMN_RANGES = {
            { 1, 15 },
            { 16, 30 },
            { 31, 45 },
            { 46, 60 },
            { 61, 75 }
    };

    private final SecureRandom secureRandom = new SecureRandom();

    public BingoCard generate() {
        List<List<Integer>> grid = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < COLUMN_RANGES.length; columnIndex++) {
            List<Integer> numbers = shuffledNumbers(COLUMN_RANGES[columnIndex][0], COLUMN_RANGES[columnIndex][1]);
            grid.add(numbers);
        }

        List<List<Integer>> rows = new ArrayList<>();
        for (int row = 0; row < 5; row++) {
            List<Integer> rowValues = new ArrayList<>();
            for (int column = 0; column < 5; column++) {
                if (row == 2 && column == 2) {
                    rowValues.add(0);
                } else {
                    rowValues.add(grid.get(column).get(row));
                }
            }
            rows.add(List.copyOf(rowValues));
        }

        return new BingoCard(List.copyOf(rows));
    }

    public void validate(BingoCard card) {
        if (card.grid() == null || card.grid().size() != 5) {
            throw new IllegalArgumentException("Bingo card must have 5 rows");
        }

        for (int row = 0; row < 5; row++) {
            List<Integer> rowValues = card.grid().get(row);
            if (rowValues == null || rowValues.size() != 5) {
                throw new IllegalArgumentException("Bingo card must have 5 columns");
            }
            for (int column = 0; column < 5; column++) {
                int value = rowValues.get(column);
                if (row == 2 && column == 2) {
                    if (value != 0) {
                        throw new IllegalArgumentException("Center cell must be FREE");
                    }
                    continue;
                }
                int[] range = COLUMN_RANGES[column];
                if (value < range[0] || value > range[1]) {
                    throw new IllegalArgumentException("Bingo card number out of range for column " + column);
                }
            }
        }
    }

    private List<Integer> shuffledNumbers(int min, int max) {
        List<Integer> values = new ArrayList<>();
        for (int value = min; value <= max; value++) {
            values.add(value);
        }
        Collections.shuffle(values, secureRandom);
        return values.subList(0, 5);
    }
}