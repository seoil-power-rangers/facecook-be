package com.facecook.cook.entity;

import java.util.Arrays;

public enum CookStatus {
    PENDING("pending"),
    MATCHED("matched"),
    EXPIRED("expired"),
    CANCELLED("cancelled");

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
