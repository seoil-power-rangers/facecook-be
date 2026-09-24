package com.facecook.common.session;

import com.facecook.auth.entity.UserRole;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;

/**
 * "관리자만 가능"을 검사하는 한 줄짜리 도우미. 관리자 API 컨트롤러
 * ({@code AdminStatsController}, {@code AdminMissionController},
 * {@code AdminReportController})가 메서드 첫 줄에서 부른다.
 *
 * <p>로그인 여부는 이미 {@code SessionAuthenticationInterceptor}가 확인했으므로
 * 여기서는 역할만 본다. 상태가 없는 검사라 스프링 빈이 아니라
 * {@code static} 메서드로 뒀고, {@code final} 클래스 + {@code private}
 * 생성자로 인스턴스를 만들 수 없게 했다.</p>
 */
public final class AdminAuthorization {
    private AdminAuthorization() {
    }

    public static void requireAdmin(AuthenticatedUser currentUser) {
        if (currentUser.role() != UserRole.ADMIN) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
    }
}
