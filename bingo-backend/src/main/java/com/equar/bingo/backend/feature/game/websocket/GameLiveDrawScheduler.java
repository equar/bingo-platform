package com.equar.bingo.backend.feature.game.websocket;

import java.time.Instant;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GameLiveDrawScheduler {

    private final GameLiveDrawService liveDrawService;

    public GameLiveDrawScheduler(GameLiveDrawService liveDrawService) {
        this.liveDrawService = liveDrawService;
    }

    @Scheduled(fixedDelayString = "#{T(java.time.Duration).parse('${app.game.draw-interval:PT10S}').toMillis()}")
    public void runScheduledDraws() {
        liveDrawService.advanceDueDraws(Instant.now());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        liveDrawService.advanceDueDraws(Instant.now());
    }
}