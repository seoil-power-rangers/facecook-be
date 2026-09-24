package com.facecook.common.session;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * HTTP 요청의 세션 쿠키로 사용자를 인증한다. 인증 정책 자체(토큰 검증·계정 조회·정지
 * 확인)는 {@link SessionAuthenticator}에 있고, 이 클래스는 HTTP식 표현만 맡는다 —
 * 실패하면 예외를 던지고, 정지 계정이면 쿠키도 지운다.
 *
 * 활동 시각 기록은 이 클래스의 책임이 아니다 — ActivityTrackingInterceptor가
 * 이 인터셉터 다음 순서로 등록되어(WebConfig), 여기서 설정한
 * CURRENT_USER_ATTRIBUTE를 읽어 처리한다.
 */
@Component
@RequiredArgsConstructor
public class SessionAuthenticationInterceptor implements HandlerInterceptor {

    public static final String CURRENT_USER_ATTRIBUTE = "currentUser";

    /**
     * 로그에 남길 userId를 요청 속성에서 꺼낸다. 인증 전이거나 공개 경로면 null.
     * 오류 로그({@code GlobalExceptionHandler})와 느린 요청 로그({@code SlowRequestLoggingFilter})가 쓴다.
     */
    public static Long currentUserId(HttpServletRequest request) {
        Object currentUser = request.getAttribute(CURRENT_USER_ATTRIBUTE);
        return currentUser instanceof AuthenticatedUser authenticatedUser ? authenticatedUser.userId() : null;
    }

    private final SessionAuthenticator authenticator;
    private final SessionCookieService cookieService;

    /**
     * 컨트롤러가 실행되기 전에 불린다({@code HandlerInterceptor}). 세션 쿠키를 읽어
     * 인증하고, 성공하면 요청 속성 {@code currentUser}에 {@link AuthenticatedUser}를 넣는다.
     *
     * <p>{@code WebConfig}가 {@code /api/**} 전체에 등록하고 로그인·가입 API만 뺀다 —
     * 그래서 대부분의 컨트롤러는 로그인 확인 코드를 따로 갖지 않는다.</p>
     *
     * <p>예외: {@code UNAUTHORIZED}(쿠키 없음·위조·만료·계정 없음), {@code SUSPENDED}(정지 계정 —
     * 쿠키도 지운다). 여기서 던진 예외도 {@code GlobalExceptionHandler}가 응답으로 바꾼다.</p>
     */
    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            // CORS preflight는 자격증명(쿠키) 없이 오는 게 스펙이라 여기서 막으면
            // 실제 요청이 나가기도 전에 브라우저가 CORS 에러로 처리해버린다.
            return true;
        }

        String token = SessionAuthenticator.readCookie(request, cookieService.cookieName()).orElse(null);
        SessionAuthenticator.Result result = authenticator.authenticate(token);
        switch (result.outcome()) {
            case UNAUTHORIZED -> throw new ApiException(ErrorCode.UNAUTHORIZED);
            case SUSPENDED -> {
                cookieService.clear(response);
                throw new ApiException(ErrorCode.SUSPENDED);
            }
            case AUTHENTICATED -> request.setAttribute(CURRENT_USER_ATTRIBUTE, result.user());
        }
        return true;
    }
}
