package com.facecook.common.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code application.yml}의 {@code app.session.*} 설정값(세션 서명 키, 쿠키 이름·
 * 수명·보안 속성). 실제 값은 환경변수({@code SESSION_SECRET} 등)에서 온다.
 *
 * <p>{@code @ConfigurationProperties} + {@code record}: 스프링이 설정 파일을 읽어
 * 이 record를 만들어 빈으로 등록한다({@code SessionConfig}의
 * {@code @EnableConfigurationProperties}). 아래 compact 생성자에서 기본값을
 * 채우고, 서명 키가 없으면 서버가 아예 뜨지 않게 한다 — 키 없이 떠서 모든
 * 로그인이 실패하는 것보다 기동 실패가 원인을 찾기 쉽다.</p>
 */
@ConfigurationProperties(prefix = "app.session")
public record SessionProperties(
        String secret,
        String cookieName,
        long maxAgeSeconds,
        boolean secure,
        String sameSite,
        String domain
) {

    public SessionProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.session.secret(SESSION_SECRET)이 설정되어야 합니다.");
        }
        cookieName = (cookieName == null || cookieName.isBlank()) ? "FACECOOK_SESSION" : cookieName;
        maxAgeSeconds = maxAgeSeconds <= 0 ? 60 * 60 * 24 * 7 : maxAgeSeconds;
        sameSite = (sameSite == null || sameSite.isBlank()) ? "Lax" : sameSite;
        // domain은 비워두면 쿠키 Domain 속성을 아예 안 붙인다(로컬 개발 등 기본 동작 유지).
        domain = (domain == null || domain.isBlank()) ? null : domain;
    }
}
