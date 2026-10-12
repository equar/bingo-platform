package com.equar.bingo.backend.feature.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record BusinessProperties(String businessTimezone) {

    public ZoneId zoneId() {
        return ZoneId
                .of(businessTimezone == null || businessTimezone.isBlank() ? "Africa/Addis_Ababa" : businessTimezone);
    }
}