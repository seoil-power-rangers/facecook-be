package com.facecook.config;

import com.facecook.common.session.SessionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 세션 설정값({@link SessionProperties})과 시계({@link Clock}) 빈을 등록한다.
 */
@Configuration
@EnableConfigurationProperties(SessionProperties.class)
public class SessionConfig {

    /**
     * 서버 전체가 "지금"을 얻는 시계. 서비스가 {@code LocalDateTime.now()}를 직접
     * 부르지 않고 이 빈을 주입받는 이유: 테스트에서 고정된 시계
     * ({@code Clock.fixed})로 바꿔 끼우면 "행사 날짜의 콕 한도"처럼 시각에 따라
     * 달라지는 동작을 원하는 날짜로 재현할 수 있다.
     *
     * <p>UTC 시계다. 사람이 보는 한국 시각이 필요하면 {@code EventTime.now(clock)}을 거친다.</p>
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
