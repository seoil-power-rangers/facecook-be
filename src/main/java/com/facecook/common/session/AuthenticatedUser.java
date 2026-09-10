package com.facecook.common.session;

import com.facecook.auth.entity.UserRole;

public record AuthenticatedUser(Long userId, String email, UserRole role) {
}
