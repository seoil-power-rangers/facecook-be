package com.facecook.common.session;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionCookieService {

    private final SessionProperties properties;
    private final SessionTokenSigner signer;

    public void issue(HttpServletResponse response, Long userId) {
        String token = signer.issue(userId, properties.maxAgeSeconds());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, properties.maxAgeSeconds()).toString());
    }

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

    public String cookieName() {
        return properties.cookieName();
    }
}
