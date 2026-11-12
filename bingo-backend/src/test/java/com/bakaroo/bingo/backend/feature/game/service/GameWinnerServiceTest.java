package com.bakaroo.bingo.backend.feature.game.service;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameWinnerServiceTest {
    @Test void centAllocationAlwaysConservesPoolEvenForLargeTies() {
        for (int count = 1; count <= 1000; count++) {
            BigDecimal sum = BigDecimal.ZERO;
            for (int index = 0; index < count; index++) {
                BigDecimal share = GameWinnerService.split(new BigDecimal("1234.57"), count, index);
                assertEquals(2, share.scale());
                assertTrue(share.signum() > 0);
                sum = sum.add(share);
            }
            assertEquals(new BigDecimal("1234.57"), sum);
        }
    }

    @Test void leftoverCentsUseStableTicketOrder() {
        assertEquals(new BigDecimal("3.34"), GameWinnerService.split(new BigDecimal("10.00"), 3, 0));
        assertEquals(new BigDecimal("3.33"), GameWinnerService.split(new BigDecimal("10.00"), 3, 1));
        assertEquals(new BigDecimal("3.33"), GameWinnerService.split(new BigDecimal("10.00"), 3, 2));
    }
}
