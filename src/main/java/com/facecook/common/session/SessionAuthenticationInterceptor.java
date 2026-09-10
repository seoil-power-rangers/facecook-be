package com.facecook.common.session;

import com.facecook.auth.entity.User;
import com.facecook.auth.entity.UserStatus;
import com.facecook.auth.repository.UserRepository;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) {
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
