package com.equar.bingo.backend.feature.community.service;

import java.time.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CommunityClock {
  private final Clock clock;
  private final ZoneId zone;

  @Autowired
  public CommunityClock(@Value("${app.business-timezone:Africa/Addis_Ababa}") String zone) {
    this(Clock.systemUTC(), ZoneId.of(zone));
  }

  public CommunityClock(Clock clock, ZoneId zone) {
    this.clock = clock;
    this.zone = zone;
  }

  public Instant now() {
    return clock.instant();
  }

  public LocalDate today() {
    return now().atZone(zone).toLocalDate();
  }

  public String timezone() {
    return zone.getId();
  }

  public Instant dayStart(LocalDate day) {
    return day.atStartOfDay(zone).toInstant();
  }
}
