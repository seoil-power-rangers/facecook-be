package com.facecook.superaccount.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.super-account")
public record SuperAccountProperties(
        @DefaultValue("rhgustjrwkwlxjf") String login,
        @DefaultValue("") String password
) {
}
