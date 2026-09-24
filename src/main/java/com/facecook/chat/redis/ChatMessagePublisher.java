package com.facecook.chat.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.facecook.chat.dto.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 저장된 채팅 메시지를 Redis 채널({@link ChatRedisChannels#MESSAGES})에 JSON으로 발행한다. 호출:
 * {@code ChatMessageController}(새로 저장된 메시지만).
 *
 * <p>왜 WebSocket으로 바로 보내지 않고 Redis를 거치나: 서버가 2대라 받는 사람은 다른 서버에 연결돼 있을 수
 * 있다. 발행하면 두 서버의 {@link ChatMessageSubscriber}가 모두 받아서 각자 연결된 사람에게 보낸다.
 * Redis Pub/Sub은 저장하지 않는 방송이라, 그 순간 받는 서버가 없으면 사라진다 — 놓친 메시지는 FE가 이력
 * 조회로 대조해 채운다.</p>
 */
@Component
@RequiredArgsConstructor
public class ChatMessagePublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(ChatMessageResponse message) {
        try {
            redisTemplate.convertAndSend(ChatRedisChannels.MESSAGES, objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("채팅 메시지 직렬화에 실패했습니다.", exception);
        }
    }
}
