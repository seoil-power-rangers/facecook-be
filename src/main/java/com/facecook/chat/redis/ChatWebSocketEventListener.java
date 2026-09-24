package com.facecook.chat.redis;

import com.facecook.chat.websocket.ChatPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * WebSocket 연결·해제 이벤트를 받아 접속 기록({@link ChatPresenceService})을 갱신한다.
 *
 * <p>{@code @EventListener}: 스프링이 해당 타입의 이벤트를 발행하면 이 메서드를 불러 준다.
 * {@code SessionConnectedEvent}는 STOMP CONNECT가 성공한 뒤, {@code SessionDisconnectEvent}는 연결이 끝날 때
 * (브라우저가 닫거나, 하트비트가 끊겨 서버가 닫거나, 서버가 정상 종료될 때) 발행된다. 서버가 강제로 죽으면
 * 해제 이벤트가 없어서, 그 경우는 접속 기록의 만료가 처리한다.</p>
 */
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
