package com.facecook.common.session;

import com.facecook.auth.entity.User;
import com.facecook.auth.entity.UserStatus;
import com.facecook.auth.repository.UserRepository;
import com.facecook.auth.service.UserActivityService;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * 세션 쿠키를 검증하고, 요청마다 DB에서 계정 상태를 다시 확인한다.
 * 역할·정지여부를 토큰에 담지 않고 항상 DB를 조회하는 이유는, 계정이 방금
 * 정지돼도(예: 신고 처리) 이미 발급된 세션이 즉시 막히게 하기 위함이다.
 */
@Component
@RequiredArgsConstructor
public class SessionAuthenticationInterceptor implements HandlerInterceptor {

    public static final String CURRENT_USER_ATTRIBUTE = "currentUser";

    private final SessionTokenSigner signer;
    private final SessionCookieService cookieService;
    private final UserRepository userRepository;
    private final UserActivityService userActivityService;

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

        String token = readCookie(request, cookieService.cookieName())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        SessionToken sessionToken = signer.verify(token)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        User user = userRepository.findById(sessionToken.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        if (user.getStatus() == UserStatus.SUSPENDED) {
            cookieService.clear(response);
            throw new ApiException(ErrorCode.SUSPENDED);
        }

        userActivityService.touch(user.getId());

        request.setAttribute(
                CURRENT_USER_ATTRIBUTE,
                new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole())
        );
        return true;
    }

    private Optional<String> readCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return Optional.ofNullable(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}
