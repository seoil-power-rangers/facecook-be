package com.facecook.common.session;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * HttpOnly 세션 쿠키의 발급·삭제. 쿠키 값 자체는 서명된 토큰({@link
 * SessionTokenSigner})이라 여기서 직접 만들지 않고 서명만 받아 쓴다.
 */
@Component
@RequiredArgsConstructor
public class SessionCookieService {

    private final SessionProperties properties;
    private final SessionTokenSigner signer;

    /**
     * userId의 세션 쿠키를 발급해서 response에 실어준다.
     *
     * <p>전제조건: 없음.</p>
     *
     * <p>부작용: {@code Set-Cookie} 헤더를 추가한다(HttpOnly, 설정된
     * secure/sameSite/domain 정책 적용, {@code properties.maxAgeSeconds()}
     * 뒤 만료).</p>
     *
     * <p>예외 없음.</p>
     *
     * @see #clear(HttpServletResponse)
     */
    public void issue(HttpServletResponse response, Long userId) {
        String token = signer.issue(userId, properties.maxAgeSeconds());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, properties.maxAgeSeconds()).toString());
    }

    /**
     * 세션 쿠키를 지운다(로그아웃).
     *
     * <p>전제조건: 없음.</p>
     *
     * <p>부작용: 값이 빈 문자열이고 maxAge가 0인 쿠키를 다시 내려서
     * 브라우저가 기존 쿠키를 즉시 삭제하게 한다(서버가 세션을 따로
     * 저장하지 않으므로 "무효화할 서버측 상태"는 없음).</p>
     *
     * <p>예외 없음.</p>
     *
     * @see #issue(HttpServletResponse, Long)
     */
    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", 0).toString());
    }

    private ResponseCookie cookie(String value, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(properties.cookieName(), value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path("/")
                .maxAge(maxAgeSeconds);
        if (properties.domain() != null) {
            builder.domain(properties.domain());
        }
        return builder.build();
    }

    /**
     * 설정된 세션 쿠키 이름을 반환한다(요청에서 쿠키를 읽어올 때 같은
     * 이름으로 찾기 위함).
     *
     * <p>전제조건: 없음.</p>
     *
     * <p>부작용: 없음.</p>
     *
     * <p>예외 없음.</p>
     */
    public String cookieName() {
        return properties.cookieName();
    }
}
