package com.facecook.common.session;

import com.facecook.auth.service.UserActivityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 인증된 사용자의 활동 시각(users.last_active_at)을 기록한다.
 *
 * SessionAuthenticationInterceptor가 이미 검증해 둔 AuthenticatedUser를
 * request attribute에서 읽기만 하고, 세션/권한 검증 자체는 하지 않는다 —
 * "세션을 검증한다"와 "활동을 기록한다"는 서로 다른 책임이라, 한 클래스에
 * 같이 두면 활동기록만 바꾸는 변경(예: 디바운스 주기 조정)에도 인증
 * 관련 테스트 전부가 엮이게 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityTrackingInterceptor implements HandlerInterceptor {

    private final UserActivityService userActivityService;

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) {
        if (request.getAttribute(SessionAuthenticationInterceptor.CURRENT_USER_ATTRIBUTE)
                instanceof AuthenticatedUser authenticatedUser) {
            // 활동기록은 부가 기능이다 — 이게 실패해도 요청 자체를 막으면 안 된다.
            try {
                userActivityService.touch(authenticatedUser.userId());
            } catch (RuntimeException exception) {
                log.warn("활동 시각 갱신에 실패했습니다. userId={}", authenticatedUser.userId(), exception);
            }
        }
        return true;
    }
}
