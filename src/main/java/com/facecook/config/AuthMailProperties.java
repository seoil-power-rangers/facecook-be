package com.facecook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 메일 보내는 사람 주소({@code app.mail.from}, 환경변수 {@code SMTP_FROM}).
 * 비어 있으면 {@code BrevoAuthMailService}가 From을 따로 지정하지 않는다.
 */
@ConfigurationProperties(prefix = "app.mail")
public record AuthMailProperties(String from) {

    public AuthMailProperties {
        from = from == null ? "" : from;
    }
}
