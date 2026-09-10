package com.facecook.common.session;

public record SessionToken(Long userId, long expiresAtEpochSeconds) {

    public boolean isExpired(long nowEpochSeconds) {
        return expiresAtEpochSeconds <= nowEpochSeconds;
    }
}
