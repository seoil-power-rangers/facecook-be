package com.facecook.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 작업을 켠다. 지금은 WebSocket 접속 기록의 만료 연장
 * ({@link com.facecook.chat.redis.ChatPresenceService#refreshLeases})이 유일하다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
