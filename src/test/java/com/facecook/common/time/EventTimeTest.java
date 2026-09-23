package com.facecook.common.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class EventTimeTest {

    @Test
    void convertsUtcClockToKoreanWallClockTime() {
        Clock utc = Clock.fixed(Instant.parse("2026-09-30T03:00:00Z"), ZoneOffset.UTC);

        assertThat(EventTime.now(utc)).isEqualTo(LocalDateTime.of(2026, 9, 30, 12, 0));
    }

    @Test
    void rollsOverToTheNextKoreanDayAfterUtcFifteenOClock() {
        // UTC 15:30은 한국 시간으로 다음 날 00:30 — "오늘" 기준 통계·한도가 이 경계를 따라야 한다.
        Clock utc = Clock.fixed(Instant.parse("2026-09-30T15:30:00Z"), ZoneOffset.UTC);

        assertThat(EventTime.now(utc)).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 30));
        assertThat(EventTime.today(utc)).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void resultDoesNotDependOnTheClockOwnZone() {
        Instant instant = Instant.parse("2026-09-30T03:00:00Z");

        assertThat(EventTime.now(Clock.fixed(instant, ZoneOffset.UTC)))
                .isEqualTo(EventTime.now(Clock.fixed(instant, ZoneOffset.ofHours(-7))));
    }
}
