package com.bakaroo.bingo.backend.feature.game.service;

import com.bakaroo.bingo.backend.feature.audit.service.AuditLogService;
import com.bakaroo.bingo.backend.feature.game.config.GameProperties;
import com.bakaroo.bingo.backend.feature.game.entity.*;
import com.bakaroo.bingo.backend.feature.game.repository.*;
import com.bakaroo.bingo.backend.feature.game.websocket.GameLiveDrawService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GameDrawCompletionTest {
    GameSessionRepository sessions = mock(GameSessionRepository.class);
    DrawEventRepository draws = mock(DrawEventRepository.class);
    GameWinnerService winners = mock(GameWinnerService.class);
    GameSession game = new GameSession();
    Instant now = Instant.parse("2026-11-12T05:00:00Z");
    GameLiveDrawService service;
    @BeforeEach void setup() {
        org.springframework.test.util.ReflectionTestUtils.setField(game,"id",42L); game.setState(GameSessionState.DRAWING);
        game.setStartedAt(now.minusSeconds(60));
        game.setPatternsSnapshotJson("[\"LINE\"]");
        when(sessions.findByIdForUpdate(42L)).thenReturn(Optional.of(game));
        when(draws.findTopBySessionIdOrderBySequenceNoDesc(42L)).thenReturn(Optional.empty());
        when(draws.findBySessionIdOrderBySequenceNoAsc(42L)).thenReturn(List.of());
        when(draws.save(any(DrawEvent.class))).thenAnswer(i -> i.getArgument(0));
        when(winners.winners(anyLong(), any())).thenReturn(new GameWinnerService.Winners(0,List.of()));
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var properties = mock(GameProperties.class);
        when(properties.drawInterval()).thenReturn(Duration.ofSeconds(10));
        service = new GameLiveDrawService(sessions, draws, properties,
                mock(ApplicationEventPublisher.class), mock(AuditLogService.class), manager,
                winners, new GameSnapshotMapper(new ObjectMapper()));
    }
    @Test void lineOnlyClosesOnWinningCallAndDoesNotDrawAgain() {
        when(winners.winners(42L,BingoPatternType.LINE)).thenReturn(
            new GameWinnerService.Winners(0,List.of()), new GameWinnerService.Winners(1,List.of(10L,11L)));
        assertTrue(service.advanceSessionDrawIfDue(42L,now).isPresent());
        assertEquals(GameSessionState.CLOSED,game.getState()); assertEquals(now,game.getEndedAt());
        assertTrue(service.advanceSessionDrawIfDue(42L,now.plusSeconds(10)).isEmpty());
        verify(draws,times(1)).save(any());
    }
    @Test void lineAndFullHouseContinueUntilBothAreComplete() {
        game.setPatternsSnapshotJson("[\"LINE\",\"FULL_HOUSE\"]");
        when(winners.winners(42L,BingoPatternType.LINE)).thenReturn(new GameWinnerService.Winners(1,List.of(10L)));
        assertTrue(service.advanceSessionDrawIfDue(42L,now).isPresent());
        assertEquals(GameSessionState.DRAWING,game.getState()); assertNull(game.getEndedAt());
    }
    @Test void restartedSchedulerClosesCompletedGameWithoutExtraCall() {
        when(winners.winners(42L,BingoPatternType.LINE)).thenReturn(new GameWinnerService.Winners(20,List.of(10L)));
        assertTrue(service.advanceSessionDrawIfDue(42L,now).isEmpty());
        assertEquals(GameSessionState.CLOSED,game.getState()); verify(draws,never()).save(any());
    }
    @Test void noWinnerKeepsDrawing() {
        assertTrue(service.advanceSessionDrawIfDue(42L,now).isPresent());
        assertEquals(GameSessionState.DRAWING,game.getState());
    }
}
