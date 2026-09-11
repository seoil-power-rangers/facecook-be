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
