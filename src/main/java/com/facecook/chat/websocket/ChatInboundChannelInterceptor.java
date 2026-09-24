package com.facecook.chat.websocket;

import com.facecook.chat.service.ChatAuthorizationService;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.common.session.SessionAuthenticator;
import com.facecook.common.websocket.StompSubscriptionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 클라이언트가 보내는 모든 STOMP 프레임이 브로커·컨트롤러에 닿기 전에 지나가는 검문소.
 * {@code WebSocketConfig#configureClientInboundChannel}이 등록한다.
 *
 * <ul>
 * <li>CONNECT·SUBSCRIBE·SEND마다 세션 토큰을 다시 검증한다 — 연결 뒤에 계정이 정지되거나 세션이 만료될 수 있다.</li>
 * <li>SUBSCRIBE: 허용 목록({@code StompSubscriptionPolicy})에 있는 주소만, 채팅방·미션은 당사자만.</li>
 * <li>SEND: {@code /app/chat/{matchId}/send}만, 당사자만. {@code /topic/...}으로 직접 보내 저장·검사를 건너뛰는 길을 막는다.</li>
 * </ul>
 *
 * <p>여기서 던진 예외는 {@link ChatStompErrorHandler}가 {@code {code, message}} ERROR 프레임으로 바꿔 보낸다.</p>
 */
@Component
@RequiredArgsConstructor
public class ChatInboundChannelInterceptor implements ChannelInterceptor {

    private static final Pattern CHAT_SEND = Pattern.compile("^/app/chat/(\\d+)/send$");

    private final SessionAuthenticator authenticator;
    private final ChatAuthorizationService authorizationService;
    private final StompSubscriptionPolicy subscriptionPolicy;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.SUBSCRIBE || command == StompCommand.SEND) {
            ChatPrincipal principal = refreshAuthentication(accessor);
            if (command == StompCommand.SUBSCRIBE) {
                subscriptionPolicy.authorize(accessor.getDestination(), principal.user().userId());
            } else if (command == StompCommand.SEND) {
                validateSendDestination(accessor.getDestination(), principal.user().userId());
            }
        }
        return message;
    }

    private ChatPrincipal refreshAuthentication(StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        Object rawToken = attributes.get(ChatSessionAttributes.SESSION_TOKEN);
        if (!(rawToken instanceof String token)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        // 핸드셰이크 이후 계정이 정지될 수 있어서 프레임마다 같은 정책으로 다시 확인한다.
        SessionAuthenticator.Result result = authenticator.authenticate(token);
        if (result.outcome() == SessionAuthenticator.Outcome.UNAUTHORIZED) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        if (result.outcome() == SessionAuthenticator.Outcome.SUSPENDED) {
            throw new ApiException(ErrorCode.SUSPENDED);
        }

        ChatPrincipal principal = new ChatPrincipal(result.user());
        accessor.setUser(principal);
        attributes.put(ChatSessionAttributes.AUTHENTICATED_USER, principal.user());
        return principal;
    }

    private void validateSendDestination(String destination, Long userId) {
        if (destination == null) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        Matcher matcher = CHAT_SEND.matcher(destination);
        if (!matcher.matches()) {
            // simple broker 목적지(/topic/**)로 직접 보내 DB 저장과 권한 검사를
            // 우회하는 경로를 차단한다.
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        Long matchId;
        try {
            matchId = Long.valueOf(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.VALIDATION, "채팅 전송 경로가 올바르지 않습니다.");
        }
        authorizationService.requireParticipant(matchId, userId);
    }
}
