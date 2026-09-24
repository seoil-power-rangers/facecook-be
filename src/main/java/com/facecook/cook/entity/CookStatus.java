package com.facecook.cook.entity;

import java.util.Arrays;

/**
 * 콕 상태. 각 값은 DB에 저장되는 소문자 문자열({@link #value()})을 함께 가진다
 * ({@link CookStatusConverter}가 변환). 상태가 바뀌는 규칙은 {@link Cook} 설명 참고.
 */
public enum CookStatus {
    PENDING("pending"),
    MATCHED("matched"),
    EXPIRED("expired"),
    CANCELLED("cancelled"),
    REJECTED("rejected");

    private final String value;

    CookStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static CookStatus from(String value) {
        return Arrays.stream(values())
                .filter(status -> status.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 콕 상태입니다: " + value));
    }
}
