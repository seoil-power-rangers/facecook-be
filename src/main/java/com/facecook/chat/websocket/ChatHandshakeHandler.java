package com.facecook.chat.websocket;

import com.facecook.common.session.AuthenticatedUser;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

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
