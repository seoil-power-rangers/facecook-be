package com.facecook.chat.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 유저별 WebSocket 접속 여부를 Redis에 추적한다(특정 채팅방이 아니라
 * "앱이 켜져있는지" 기준). 유저당 세션 ID를 Set으로 모아서, 탭·기기를
 * 여러 개 열어도 전부 닫혀야 미접속으로 판정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatPresenceService {

    private static final String PRESENCE_KEY_PREFIX = "facecook:chat:presence:";

    private final StringRedisTemplate redisTemplate;

    /**
     * userId의 sessionId를 접속 중 목록에 추가한다(STOMP CONNECT 시
     * 호출).
     *
     * <p>전제조건: 없음.</p>
     *
     * <p>부작용: Redis Set에 sessionId 추가. Redis 장애 시에도 예외를
     * 던지지 않는다 — 접속자 명단은 보조 정보라 이것 때문에 실제
     * WebSocket 연결이 끊기면 안 된다.</p>
     *
     * <p>예외 없음.</p>
     *
     * @see #disconnected(Long, String)
     */
    public void connected(Long userId, String sessionId) {
        try {
            redisTemplate.opsForSet().add(key(userId), sessionId);
        } catch (DataAccessException exception) {
            // 접속자 명단은 보조적인 휘발성 상태다. Redis 장애가 WebSocket
            // 연결 자체를 끊지는 않도록 하고 운영 로그로 남긴다.
            log.warn("WebSocket 접속자 등록에 실패했습니다. userId={}", userId, exception);
        }
    }

    /**
     * userId의 sessionId를 접속 중 목록에서 뺀다(STOMP DISCONNECT 시
     * 호출). 이게 마지막 세션이었으면 키 자체를 지운다.
     *
     * <p>전제조건: 없음.</p>
     *
     * <p>부작용: Redis Set에서 sessionId 제거, 비었으면 키 삭제. 실패해도
     * 예외를 던지지 않는다(로그만).</p>
     *
     * <p>예외 없음.</p>
     *
     * @see #connected(Long, String)
     */
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

    /**
     * userId가 지금 WebSocket에 하나라도 연결돼 있는지 확인한다(푸시
     * 발송 여부 판단에 쓰임, {@link
     * com.facecook.push.service.ParticipantPushNotificationService}
     * 참고).
     *
     * <p>전제조건: 없음.</p>
     *
     * <p>부작용: 없음.</p>
     *
     * <p>예외 없음 — Redis 조회가 실패하면 "미접속"으로 간주하고
     * false를 반환한다(접속 중이라고 잘못 판단해서 알림을 누락하는
     * 것보다, 혹시 불필요한 푸시가 한 번 더 가는 쪽이 안전하다는
     * 판단).</p>
     */
    public boolean isConnected(Long userId) {
        try {
            Long connectionCount = redisTemplate.opsForSet().size(key(userId));
            return connectionCount != null && connectionCount > 0L;
        } catch (DataAccessException exception) {
            // Redis 장애 시 접속 중이라고 잘못 판단해 알림을 누락하기보다
            // 미접속으로 간주하고 best-effort 푸시 발송을 시도한다.
            log.warn("WebSocket 접속 여부 조회에 실패했습니다. userId={}", userId, exception);
            return false;
        }
    }

    private String key(Long userId) {
        return PRESENCE_KEY_PREFIX + userId;
    }
}
