package com.facecook.auth.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * 인증코드를 어디에 쓸지(가입/로그인). 같은 이메일이라도 목적별로 Redis 키가 달라
 * 가입용 코드로 로그인할 수 없다({@code VerificationCodeService}).
 *
 * <p>{@code @JsonCreator}: JSON의 문자열을 이 enum으로 바꿀 때 쓸 메서드를 지정한다
 * (대소문자·앞뒤 공백을 무시). 없는 값이면 {@code valueOf}가 실패해 요청 본문을 읽지
 * 못한 것으로 처리되고 400 {@code VALIDATION}이 된다. {@code @JsonValue}: 응답으로
 * 내보낼 때는 소문자로 쓴다.</p>
 */
public enum VerificationPurpose {
    SIGNUP,
    LOGIN;

    @JsonCreator
    public static VerificationPurpose from(String value) {
        if (value == null) {
            return null;
        }
        return VerificationPurpose.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
