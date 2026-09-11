package com.facecook.chat.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatPresenceService {

    private static final String PRESENCE_KEY_PREFIX = "facecook:chat:presence:";

    private final StringRedisTemplate redisTemplate;

    public void connected(Long userId, String sessionId) {
        try {
            redisTemplate.opsForSet().add(key(userId), sessionId);
        } catch (DataAccessException exception) {
            // 접속자 명단은 보조적인 휘발성 상태다. Redis 장애가 WebSocket
            // 연결 자체를 끊지는 않도록 하고 운영 로그로 남긴다.
            log.warn("WebSocket 접속자 등록에 실패했습니다. userId={}", userId, exception);
        }
    }

    public void disconnected(Long userId, String sessionId) {
        try {
            String key = key(userId);
            redisTemplate.opsForSet().remove(key, sessionId);
            Long remaining = redisTemplate.opsForSet().size(key);
            if (remaining != null && remaining == 0L) {
                redisTemplate.delete(key);
            }
        } catch (DataAccessException exception) {
            log.warn("WebSocket 접속자 해제에 실패했습니다. userId={}", userId, exception);
        }
    }

    private String key(Long userId) {
        return PRESENCE_KEY_PREFIX + userId;
    }
}
