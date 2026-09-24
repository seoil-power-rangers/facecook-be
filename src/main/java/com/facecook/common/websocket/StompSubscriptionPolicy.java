package com.facecook.common.websocket;

import com.facecook.chat.service.ChatAuthorizationService;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.mission.service.MissionAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP 구독(SUBSCRIBE) 허용 목록. 여기 적힌 목적지만 구독할 수 있고 나머지는
 * 전부 거절한다 — 전송(SEND)을 {@code /app/chat/{matchId}/send} 하나로 제한하는
 * 것과 같은 방식이다. 새 구독 목적지를 추가하려면 여기에 권한 검사와 함께 넣는다.
 */
@Component
@RequiredArgsConstructor
public class StompSubscriptionPolicy {

    private static final String CHAT_ACKS = "/user/queue/chat-acks";
    private static final Pattern CHAT_TOPIC = Pattern.compile("^/topic/chat/(\\d+)$");
    private static final Pattern MISSION_TOPIC = Pattern.compile("^/topic/mission/(\\d+)$");

    private final ChatAuthorizationService chatAuthorizationService;
    private final MissionAuthorizationService missionAuthorizationService;

    /**
     * userId가 destination을 구독해도 되는지 확인한다.
     *
     * <p>예외: 허용 목록에 없는 목적지는 {@code FORBIDDEN}, 채팅방·미션의 참가자가
     * 아니면 각 권한 서비스의 예외, 매칭 ID가 너무 커서 숫자로 못 읽으면
     * {@code VALIDATION}.</p>
     */
    public void authorize(String destination, Long userId) {
        if (destination == null) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        if (CHAT_ACKS.equals(destination)) {
            // 사용자 목적지라 브로커가 이 세션의 큐로 바꿔 준다. 남의 ACK는 받을 수 없다.
            return;
        }
        Matcher chat = CHAT_TOPIC.matcher(destination);
        if (chat.matches()) {
            chatAuthorizationService.requireParticipant(matchId(chat), userId);
            return;
        }
        Matcher mission = MISSION_TOPIC.matcher(destination);
        if (mission.matches()) {
            missionAuthorizationService.requireParticipant(matchId(mission), userId);
            return;
        }
        throw new ApiException(ErrorCode.FORBIDDEN);
    }

    private static Long matchId(Matcher matcher) {
        try {
            return Long.valueOf(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.VALIDATION, "구독 경로가 올바르지 않습니다.");
        }
    }
}
