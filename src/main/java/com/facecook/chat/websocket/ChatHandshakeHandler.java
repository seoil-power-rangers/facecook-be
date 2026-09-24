package com.facecook.chat.websocket;

import com.facecook.common.session.AuthenticatedUser;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket 연결이 누구의 것인지 정한다. {@link ChatHandshakeInterceptor}가 세션 쿠키로 인증해 속성에 넣어 둔
 * 사용자를 {@link ChatPrincipal}로 감싸 돌려주면, 이후 이 연결의 모든 STOMP 메시지에 그 사용자가 붙는다.
 * {@code @SendToUser}(ACK)도 이 값으로 "누구에게" 보낼지 정한다. {@code WebSocketConfig}가 등록한다.
 */
@Component
public class ChatHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        Object user = attributes.get(ChatSessionAttributes.AUTHENTICATED_USER);
        return user instanceof AuthenticatedUser authenticatedUser
                ? new ChatPrincipal(authenticatedUser)
                : null;
    }
}
