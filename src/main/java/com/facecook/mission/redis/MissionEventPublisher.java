package com.facecook.mission.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.facecook.mission.dto.MissionProgressResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 미션 진행 상태를 Redis 채널({@link MissionRedisChannels#EVENTS})에 JSON으로 발행한다. 채팅의 {@code ChatMessagePublisher}와 같은 역할. */
@Component
@RequiredArgsConstructor
public class MissionEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(MissionProgressResponse progress) {
        try {
            redisTemplate.convertAndSend(
                    MissionRedisChannels.EVENTS,
                    objectMapper.writeValueAsString(progress)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("미션 진행상황 직렬화에 실패했습니다.", exception);
        }
    }
}
