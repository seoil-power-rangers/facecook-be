package com.facecook.auth.entity;

/** 계정 상태. {@code SUSPENDED}면 모든 API·WebSocket 진입이 막힌다({@code SessionAuthenticator}). */
public enum UserStatus {
    ACTIVE,
    SUSPENDED
}
