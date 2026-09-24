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

/**
 * Redis 채널의 미션 진행 이벤트를 받아 이 서버에 연결된 구독자({@code /topic/mission/{matchId}})에게 전달한다.
 * {@code MissionRedisConfig}가 리스너로 등록한다. 채팅의 {@code ChatMessageSubscriber}와 같은 구조다.
 */
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
