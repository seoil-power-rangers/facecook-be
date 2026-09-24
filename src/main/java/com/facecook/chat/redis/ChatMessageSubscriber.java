package com.facecook.chat.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.facecook.chat.dto.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Redis 채널의 채팅 메시지를 받아 이 서버에 연결된 구독자에게 전달한다({@code /topic/chat/{matchId}}).
 * {@code RedisChatConfig}가 서버 기동 때 이 리스너를 채널에 연결한다.
 *
 * <p>{@code SimpMessagingTemplate.convertAndSend}는 서버 안의 STOMP 브로커로 메시지를 넣는다. 브로커는 이
 * 서버에서 그 주소를 구독한 연결에만 보내므로, 두 서버가 각자 자기 연결을 맡는다.</p>
 *
 * <p>변환·전달 실패는 로그만 남긴다. 리스너에서 예외를 던져도 보낸 사람에게 알릴 방법이 없고, 놓친 메시지는
 * FE의 이력 대조가 채운다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message redisMessage, byte[] pattern) {
        try {
            ChatMessageResponse message = objectMapper.readValue(
                    new String(redisMessage.getBody(), StandardCharsets.UTF_8),
                    ChatMessageResponse.class
            );
            messagingTemplate.convertAndSend("/topic/chat/" + message.matchId(), message);
        } catch (Exception exception) {
            log.error("Redis 채팅 메시지를 STOMP로 전달하지 못했습니다.", exception);
        }
    }
}
