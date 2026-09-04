package com.facecook.auth.dto;

public record RequestCodeResponse(
        int expiresInSeconds,
        int resendAfterSeconds
) {
}
