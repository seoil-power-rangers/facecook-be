package com.facecook.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled}가 붙은 메서드를 주기적으로 실행하게 켠다. 이 설정이 없으면
 * {@code @Scheduled}는 아무 일도 하지 않는다.
 *
 * <p>현재 주기 작업(서버마다 각자 돈다):</p>
 * <ul>
 * <li>{@link com.facecook.chat.redis.ChatPresenceService#refreshLeases} — 30초마다
 * 이 서버가 들고 있는 WebSocket 접속 기록의 만료를 연장</li>
 * <li>{@link com.facecook.push.service.PushDeliveryMonitor#report} — 30초마다 푸시
 * 거절·실패 횟수와 실행기 상태를 로그로 남김(문제가 있을 때만)</li>
 * </ul>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
