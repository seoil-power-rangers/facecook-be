package com.facecook.mission.websocket;

import com.facecook.chat.websocket.ChatPrincipal;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.mission.service.MissionAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class MissionInboundChannelInterceptor implements ChannelInterceptor {

    private static final String TOPIC_PREFIX = "/topic/mission/";
    private static final Pattern MISSION_TOPIC = Pattern.compile("^/topic/mission/(\\d+)$");

    private final MissionAuthorizationService authorizationService;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null
                || accessor.getCommand() != StompCommand.SUBSCRIBE
                || accessor.getDestination() == null
                || !accessor.getDestination().startsWith(TOPIC_PREFIX)) {
            return message;
        }

        Matcher matcher = MISSION_TOPIC.matcher(accessor.getDestination());
        if (!matcher.matches()) {
            throw new ApiException(ErrorCode.VALIDATION, "미션 구독 경로가 올바르지 않습니다.");
        }
        if (!(accessor.getUser() instanceof ChatPrincipal principal)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        Long matchId;
        try {
            matchId = Long.valueOf(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.VALIDATION, "미션 구독 경로가 올바르지 않습니다.");
        }
        authorizationService.requireParticipant(matchId, principal.user().userId());
        return message;
    }
}
