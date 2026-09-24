package com.facecook.superaccount.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.super-account.*} 설정. 환경변수 {@code SUPER_ACCOUNT_LOGIN}·{@code SUPER_ACCOUNT_PASSWORD}에서 온다.
 * 기본값이 빈 문자열이라, 설정하지 않으면 {@code SuperAccountBootstrap}이 슈퍼 계정을 만들지 않는다.
 */
@ConfigurationProperties(prefix = "app.super-account")
public record SuperAccountProperties(
        @DefaultValue("") String login,
        @DefaultValue("") String password
) {
}
