package com.facecook.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.admin.stats")
public record AdminStatsProperties(
        @DefaultValue("STATUS") ActiveUserCriterion activeUserCriterion
) {
}
