package com.bakaroo.bingo.backend.config;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class StartupLoggingListener {

    private static final Logger log = LoggerFactory.getLogger(StartupLoggingListener.class);
    private final Environment environment;
    private final DataSource dataSource;

    public StartupLoggingListener(Environment environment, DataSource dataSource) {
        this.environment = environment;
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logReady() {
        String[] profiles = environment.getActiveProfiles();
        log.atInfo()
                .addKeyValue("operation", "service_start")
                .addKeyValue("outcome", "ready")
                .addKeyValue("activeProfiles", profiles.length == 0 ? "default" : String.join(",", profiles))
                .log("Backend is ready");

        long startedAt = System.nanoTime();
        try (var connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(2);
            (valid ? log.atInfo() : log.atWarn())
                    .addKeyValue("operation", "dependency_check")
                    .addKeyValue("dependency", "database")
                    .addKeyValue("outcome", valid ? "available" : "unavailable")
                    .addKeyValue("durationMs", elapsedMillis(startedAt))
                    .log("Startup dependency check completed");
        } catch (SQLException exception) {
            log.atError()
                    .addKeyValue("operation", "dependency_check")
                    .addKeyValue("dependency", "database")
                    .addKeyValue("outcome", "unavailable")
                    .addKeyValue("durationMs", elapsedMillis(startedAt))
                    .setCause(exception)
                    .log("Startup dependency check failed");
        }
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }
}
