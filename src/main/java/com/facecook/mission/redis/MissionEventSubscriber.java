package com.facecook.mission.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.facecook.mission.dto.MissionProgressResponse;
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
public class MissionEventSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message redisMessage, byte[] pattern) {
        try {
            MissionProgressResponse progress = objectMapper.readValue(
                    new String(redisMessage.getBody(), StandardCharsets.UTF_8),
                    MissionProgressResponse.class
            );
            messagingTemplate.convertAndSend(
                    "/topic/mission/" + progress.matchId(),
                    progress
            );
        } catch (Exception exception) {
            log.error("Redis 미션 이벤트를 STOMP로 전달하지 못했습니다.", exception);
        }
    }
}
