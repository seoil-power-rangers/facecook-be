package com.facecook.common.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
