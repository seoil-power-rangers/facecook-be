package com.facecook.common.session;

import com.facecook.auth.entity.UserRole;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;

public final class SuperAuthorization {
    private SuperAuthorization() {
    }

    public static void requireSuper(AuthenticatedUser currentUser) {
        if (currentUser.role() != UserRole.SUPER) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
    }
}
