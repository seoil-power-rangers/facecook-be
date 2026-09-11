package com.facecook.common.session;

import com.facecook.auth.entity.UserRole;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;

public final class AdminAuthorization {
    private AdminAuthorization() {
    }

    public static void requireAdmin(AuthenticatedUser currentUser) {
        if (currentUser.role() != UserRole.ADMIN) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
    }
}
