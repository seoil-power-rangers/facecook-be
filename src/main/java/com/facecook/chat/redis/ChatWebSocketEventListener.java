package com.facecook.chat.redis;

import com.facecook.chat.websocket.ChatPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
@RequiredArgsConstructor
public class ChatWebSocketEventListener {

    private final ChatPresenceService presenceService;

    @EventListener
    public void connected(SessionConnectedEvent event) {
        register(event.getUser(), SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders()));
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        Principal principal = event.getUser();
        if (principal instanceof ChatPrincipal chatPrincipal) {
            presenceService.disconnected(chatPrincipal.user().userId(), event.getSessionId());
        }
    }

    private void register(Principal principal, String sessionId) {
        if (principal instanceof ChatPrincipal chatPrincipal && sessionId != null) {
            presenceService.connected(chatPrincipal.user().userId(), sessionId);
        }
    }
}
