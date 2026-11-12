package com.bakaroo.bingo.backend.feature.game.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.bakaroo.bingo.backend.common.exception.ConflictException;
import com.bakaroo.bingo.backend.feature.game.entity.*;
import com.bakaroo.bingo.backend.feature.game.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameWinnerServiceTest {
    @Test void laterCompletedTicketsCannotWinEvenAfterEveryNumberIsCalled() {
        var tickets = mock(TicketRepository.class);
        var draws = mock(DrawEventRepository.class);
        var mapper = new GameSnapshotMapper(new ObjectMapper());
        var first = mock(Ticket.class);
        var later = mock(Ticket.class);
        var tied = mock(Ticket.class);
        when(first.getId()).thenReturn(10L);
        when(later.getId()).thenReturn(11L);
        when(tied.getId()).thenReturn(12L);
        String grid = "[[1,16,31,46,61],[2,17,32,47,62],[3,18,0,48,63],[4,19,33,49,64],[5,20,34,50,65]]";
        when(first.getGridJson()).thenReturn(grid);
        when(tied.getGridJson()).thenReturn(grid);
        when(later.getGridJson()).thenReturn("[[6,16,31,46,61],[7,17,32,47,62],[8,18,0,48,63],[9,19,33,49,64],[10,20,34,50,65]]");
        when(tickets.findBySessionIdOrderByIdAsc(42L)).thenReturn(List.of(first,later,tied));
        var calls = new ArrayList<DrawEvent>();
        for (int n=1;n<=75;n++) {
            var d = new DrawEvent();
            d.setSequenceNo(n); d.setNumberDrawn(n); calls.add(d);
        }
        when(draws.findBySessionIdOrderBySequenceNoAsc(42L)).thenReturn(calls);
        var service = new GameWinnerService(tickets,draws,mapper);
        var result = service.winners(42L,BingoPatternType.LINE);
        assertEquals(5,result.sequence());
        assertEquals(List.of(10L,12L),result.ticketIds());
        var session = new GameSession();
        ReflectionTestUtils.setField(session,"id",42L);
        session.setRulesVersion("EARLIEST_SPLIT_V1");
        session.setPrizesSnapshotJson("[{\"prizeType\":\"LINE\",\"amount\":10.00,\"settlementPath\":\"PLAYER_WALLET\",\"maxWinners\":1}]");
        var claim = new WinClaim();
        claim.setSession(session); claim.setClaimedPattern(BingoPatternType.LINE);
        claim.setDrawSequenceSnapshot(75); claim.setTicket(later);
        assertThrows(ConflictException.class, () -> service.share(claim));
        claim.setTicket(first); assertEquals(new BigDecimal("5.00"),service.share(claim));
        claim.setTicket(tied); assertEquals(new BigDecimal("5.00"),service.share(claim));
    }

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
