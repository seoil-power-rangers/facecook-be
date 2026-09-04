package com.facecook.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;

@Configuration
@EnableConfigurationProperties(AuthMailProperties.class)
public class AuthConfig {

    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }
}
