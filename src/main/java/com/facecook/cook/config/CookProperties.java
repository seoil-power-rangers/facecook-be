package com.facecook.cook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 콕 기능 설정.
 *
 * @param rejectEnabled 받은 콕 거절 API를 켤지 여부(환경변수 {@code COOK_REJECT_ENABLED}, 기본 꺼짐).
 *                      꺼져 있어도 이미 저장된 거절 상태를 읽고 처리하는 코드는 항상 동작한다.
 */
@ConfigurationProperties(prefix = "app.cook")
public record CookProperties(boolean rejectEnabled) {
}
