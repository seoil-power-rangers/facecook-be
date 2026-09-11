package com.facecook.push.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.push.vapid")
public record VapidProperties(String publicKey, String privateKey) {

    public VapidProperties {
        publicKey = publicKey == null ? "" : publicKey;
        privateKey = privateKey == null ? "" : privateKey;
    }
}
