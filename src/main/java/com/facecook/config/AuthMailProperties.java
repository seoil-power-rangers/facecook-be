package com.facecook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public record AuthMailProperties(String from) {

    public AuthMailProperties {
        from = from == null ? "" : from;
    }
}
