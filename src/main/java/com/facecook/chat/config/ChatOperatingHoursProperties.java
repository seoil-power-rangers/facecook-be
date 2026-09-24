package com.facecook.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;

/**
 * 채팅 운영시간({@code app.chat.open-time}/{@code close-time}, 환경변수 {@code CHAT_OPEN_TIME}/{@code CHAT_CLOSE_TIME},
 * 기본 09:00~18:00, 한국 시간). 운영시간 밖의 전송은 {@code ChatService}가 {@code CLOSED}로 거절한다.
 * 이력 조회는 운영시간과 무관하게 된다.
 */
@ConfigurationProperties(prefix = "app.chat")
public record ChatOperatingHoursProperties(LocalTime openTime, LocalTime closeTime) {

    public ChatOperatingHoursProperties {
        openTime = openTime == null ? LocalTime.of(9, 0) : openTime;
        closeTime = closeTime == null ? LocalTime.of(18, 0) : closeTime;
    }

    /** time이 운영시간 안인지. 여는 시각은 포함하고 닫는 시각은 포함하지 않는다(09:00 가능, 18:00 불가). */
    public boolean contains(LocalTime time) {
        return !time.isBefore(openTime) && time.isBefore(closeTime);
    }
}
