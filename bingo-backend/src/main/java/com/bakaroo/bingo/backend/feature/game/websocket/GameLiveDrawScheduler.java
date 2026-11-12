package com.bakaroo.bingo.backend.feature.game.websocket;

import com.bakaroo.bingo.backend.common.api.CorrelationIdFilter;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.game.draw-scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class GameLiveDrawScheduler {

    private static final Logger log = LoggerFactory.getLogger(GameLiveDrawScheduler.class);

    private final GameLiveDrawService liveDrawService;

    public GameLiveDrawScheduler(GameLiveDrawService liveDrawService) {
        this.liveDrawService = liveDrawService;
    }

    @Scheduled(fixedDelayString = "#{T(java.time.Duration).parse('${app.game.draw-interval:PT5S}').toMillis()}")
    public void runScheduledDraws() {
        runSweep("scheduled");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        runSweep("startup_recovery");
    }

    private void runSweep(String jobName) {
        String previousCorrelationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        MDC.put(CorrelationIdFilter.MDC_KEY, "job-" + UUID.randomUUID());
        long startedAt = System.nanoTime();
        try {
            var draws = liveDrawService.advanceDueDraws(Instant.now());
            if (!draws.isEmpty()) {
                log.atInfo()
                        .addKeyValue("operation", "game_live_draw_sweep")
                        .addKeyValue("outcome", "committed")
                        .addKeyValue("job", jobName)
                        .addKeyValue("drawCount", draws.size())
                        .addKeyValue("durationMs", elapsedMillis(startedAt))
                        .log("Scheduled draw sweep completed");
            }
        } catch (RuntimeException exception) {
            log.atError()
                    .addKeyValue("operation", "game_live_draw_sweep")
                    .addKeyValue("outcome", "failed")
                    .addKeyValue("job", jobName)
                    .addKeyValue("durationMs", elapsedMillis(startedAt))
                    .addKeyValue("failureType", exception.getClass().getSimpleName())
                    .setCause(exception)
                    .log("Scheduled draw sweep failed");
        } finally {
            if (previousCorrelationId == null) {
                MDC.remove(CorrelationIdFilter.MDC_KEY);
            } else {
                MDC.put(CorrelationIdFilter.MDC_KEY, previousCorrelationId);
            }
        }
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
