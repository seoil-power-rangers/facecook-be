package com.facecook.chat.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ChatOperatingHoursProperties.class)
public class ChatConfig {
}
