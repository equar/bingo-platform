package com.bakaroo.bingo.backend.feature.game.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bakaroo.bingo.backend.feature.game.entity.BingoPatternType;
import com.bakaroo.bingo.backend.feature.game.entity.PrizeSettlementPath;
import com.bakaroo.bingo.backend.feature.game.model.BingoPrizeConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameSnapshotMapperTest {

    private final GameSnapshotMapper mapper = new GameSnapshotMapper(new ObjectMapper());

    @Test
    void readsAndWritesPrizeSnapshotsWithWinnerCaps() {
        List<BingoPrizeConfig> prizes = mapper.readPrizes("""
                [{"prizeType":"LINE","amount":10.00,"settlementPath":"PLAYER_WALLET","maxWinners":1}]
                """);

        assertEquals(1, prizes.getFirst().maxWinners());
        assertTrue(mapper.writePrizes(List.of(new BingoPrizeConfig(
                BingoPatternType.LINE, new BigDecimal("10.00"), PrizeSettlementPath.PLAYER_WALLET, 1)))
                .contains("\"maxWinners\":1"));
    }
}
