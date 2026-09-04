package com.facecook.auth.dto;

import com.facecook.auth.entity.User;

import java.util.Locale;

public record AuthVerificationResponse(
        Long userId,
        String email,
        String role
) {
    public static AuthVerificationResponse from(User user) {
        return new AuthVerificationResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name().toLowerCase(Locale.ROOT)
        );
    }
}
