package com.equar.bingo.backend.feature.community;

import static org.assertj.core.api.Assertions.*;

import com.equar.bingo.backend.feature.community.service.CommunityClock;
import java.time.*;
import org.junit.jupiter.api.Test;

class CommunityClockTest {
  @Test
  void addisDayBoundaryUsesServerTimeInsteadOfDeviceTime() {
    var before =
        new CommunityClock(
            Clock.fixed(Instant.parse("2026-10-29T20:59:59Z"), ZoneOffset.UTC),
            ZoneId.of("Africa/Addis_Ababa"));
    var after =
        new CommunityClock(
            Clock.fixed(Instant.parse("2026-10-29T21:00:00Z"), ZoneOffset.UTC),
            ZoneId.of("Africa/Addis_Ababa"));
    assertThat(before.today()).isEqualTo(LocalDate.of(2026, 10, 29));
    assertThat(after.today()).isEqualTo(LocalDate.of(2026, 10, 30));
    assertThat(before.dayStart(before.today().plusDays(1))).isEqualTo(after.now());
  }

  @Test
  void resetBoundaryHandlesTimezoneDaylightSavingChanges() {
    var clock =
        new CommunityClock(
            Clock.fixed(Instant.parse("2026-03-08T12:00:00Z"), ZoneOffset.UTC),
            ZoneId.of("America/New_York"));
    assertThat(
            Duration.between(
                clock.dayStart(clock.today()), clock.dayStart(clock.today().plusDays(1))))
        .isEqualTo(Duration.ofHours(23));
  }
}
