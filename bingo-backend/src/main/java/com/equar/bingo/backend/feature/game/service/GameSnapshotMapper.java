package com.equar.bingo.backend.feature.game.service;

import com.equar.bingo.backend.feature.game.entity.BingoPatternType;
import com.equar.bingo.backend.feature.game.entity.PrizeSettlementPath;
import com.equar.bingo.backend.feature.game.model.BingoPrizeConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GameSnapshotMapper {

    private static final TypeReference<List<BingoPatternType>> PATTERN_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<BingoPrizeConfig>> PRIZE_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<List<Integer>>> GRID_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public GameSnapshotMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String writePatterns(List<BingoPatternType> patterns) {
        return write(patterns);
    }

    public List<BingoPatternType> readPatterns(String value) {
        return read(value, PATTERN_LIST);
    }

    public String writePrizes(List<BingoPrizeConfig> prizes) {
        return write(prizes);
    }

    public List<BingoPrizeConfig> readPrizes(String value) {
        return read(value, PRIZE_LIST);
    }

    public String writeGrid(List<List<Integer>> grid) {
        return write(grid);
    }

    public List<List<Integer>> readGrid(String value) {
        return read(value, GRID_LIST);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to write game snapshot", exception);
        }
    }

    private <T> T read(String value, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(value, typeReference);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read game snapshot", exception);
        }
    }
}