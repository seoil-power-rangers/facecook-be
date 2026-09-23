package com.facecook.chat.config;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ChatOperatingHoursPropertiesTest {

    private final ChatOperatingHoursProperties hours =
            new ChatOperatingHoursProperties(LocalTime.of(9, 0), LocalTime.of(18, 0));

    @Test
    void openingTimeIsIncluded() {
        assertThat(hours.contains(LocalTime.of(9, 0))).isTrue();
    }

    @Test
    void closingTimeIsExcluded() {
        assertThat(hours.contains(LocalTime.of(18, 0))).isFalse();
    }

    @Test
    void justBeforeOpeningAndJustBeforeClosing() {
        assertThat(hours.contains(LocalTime.of(8, 59, 59))).isFalse();
        assertThat(hours.contains(LocalTime.of(17, 59, 59))).isTrue();
    }

    @Test
    void defaultsToNineToSixWhenNotConfigured() {
        ChatOperatingHoursProperties defaults = new ChatOperatingHoursProperties(null, null);

        assertThat(defaults.contains(LocalTime.of(9, 0))).isTrue();
        assertThat(defaults.contains(LocalTime.of(18, 0))).isFalse();
    }
}
