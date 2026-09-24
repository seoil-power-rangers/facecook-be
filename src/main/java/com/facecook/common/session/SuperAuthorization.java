package com.facecook.common.session;

import com.facecook.auth.entity.UserRole;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;

/**
 * "슈퍼 계정만 가능"을 검사하는 도우미. {@code SuperAccountController}와
 * {@code SuperAccountService}가 부른다. 만든 이유와 형태는
 * {@link AdminAuthorization}과 같다.
 */
public final class SuperAuthorization {
    private SuperAuthorization() {
    }

    public static void requireSuper(AuthenticatedUser currentUser) {
        if (currentUser.role() != UserRole.SUPER) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
    }
}
