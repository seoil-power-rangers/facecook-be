package com.facecook.auth.dto;

import com.facecook.auth.entity.User;
import com.facecook.common.session.AuthenticatedUser;

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

    public static AuthVerificationResponse from(AuthenticatedUser user) {
        return new AuthVerificationResponse(
                user.userId(),
                user.email(),
                user.role().name().toLowerCase(Locale.ROOT)
        );
    }
}
