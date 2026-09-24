package com.facecook.chat.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 채팅 운영시간 설정({@link ChatOperatingHoursProperties})을 빈으로 등록한다. */
@Configuration
@EnableConfigurationProperties(ChatOperatingHoursProperties.class)
public class ChatConfig {
}
