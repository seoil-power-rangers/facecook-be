package com.facecook.config;

import com.facecook.common.session.SessionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(SessionProperties.class)
public class SessionConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
