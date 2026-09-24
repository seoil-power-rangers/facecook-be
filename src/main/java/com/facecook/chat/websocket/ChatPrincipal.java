package com.facecook.chat.websocket;

import com.facecook.common.session.AuthenticatedUser;

import java.security.Principal;

/**
 * WebSocket 연결의 사용자. Spring STOMP는 사용자를 {@link Principal}로 다루므로 {@code AuthenticatedUser}를 감싼다.
 * {@link #getName()}(userId 문자열)은 {@code @SendToUser}가 사용자별 큐를 찾을 때 쓴다.
 * 만드는 곳: {@link ChatHandshakeHandler}(연결 시), {@link ChatInboundChannelInterceptor}(프레임마다 재인증 뒤).
 */
public record ChatPrincipal(AuthenticatedUser user) implements Principal {

    @Override
    public String getName() {
        return user.userId().toString();
    }
}
