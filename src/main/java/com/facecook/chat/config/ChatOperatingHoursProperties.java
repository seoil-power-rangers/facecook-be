package com.facecook.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;

@ConfigurationProperties(prefix = "app.chat")
public record ChatOperatingHoursProperties(LocalTime openTime, LocalTime closeTime) {

    public ChatOperatingHoursProperties {
        openTime = openTime == null ? LocalTime.of(9, 0) : openTime;
        closeTime = closeTime == null ? LocalTime.of(18, 0) : closeTime;
    }
}
