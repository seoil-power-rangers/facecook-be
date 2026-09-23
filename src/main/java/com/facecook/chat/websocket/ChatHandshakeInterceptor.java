package com.facecook.chat.websocket;

import com.facecook.common.exception.ErrorCode;
import com.facecook.common.session.SessionAuthenticator;
import com.facecook.common.session.SessionCookieService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    private final SessionAuthenticator authenticator;
    private final SessionCookieService cookieService;

    /**
     * 세션 쿠키로 인증된 사용자만 WebSocket을 열게 한다. 인증 정책은 {@link
     * SessionAuthenticator}에 있고, 여기서는 실패를 응답 상태로만 알린다(401, 정지면
     * {@code SUSPENDED}의 상태). 통과하면 토큰 원문을 세션 속성에 남겨 STOMP
     * 프레임마다 다시 검증할 수 있게 한다.
     */
    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        String rawToken = SessionAuthenticator
                .readCookie(servletRequest.getServletRequest(), cookieService.cookieName())
                .orElse(null);
        SessionAuthenticator.Result result = authenticator.authenticate(rawToken);
        return switch (result.outcome()) {
            case UNAUTHORIZED -> {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                yield false;
            }
            case SUSPENDED -> {
                response.setStatusCode(ErrorCode.SUSPENDED.getStatus());
                yield false;
            }
            case AUTHENTICATED -> {
                attributes.put(ChatSessionAttributes.SESSION_TOKEN, rawToken);
                attributes.put(ChatSessionAttributes.AUTHENTICATED_USER, result.user());
                yield true;
            }
        };
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // no-op
    }
}
