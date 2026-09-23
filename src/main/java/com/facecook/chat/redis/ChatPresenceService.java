package com.facecook.chat.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 유저별 WebSocket 접속 여부를 Redis에 추적한다(특정 채팅방이 아니라 "앱이 켜져 있는지" 기준).
 * 푸시 발송 전에 이 값을 보고 접속 중이면 푸시를 생략한다({@code ParticipantPushNotificationService}) —
 * 그래서 기록이 잘못 남아 있으면 그 사용자는 콕·매칭·채팅 푸시를 전부 못 받는다.
 *
 * <p><b>구조.</b> 유저마다 Sorted Set 하나({@code facecook:chat:presence:v2:{userId}}). 멤버는
 * {@code {서버 인스턴스 ID}:{세션 ID}}, 점수는 그 기록의 만료 시각(epoch ms)이다. 만료 시각이 지나지 않은
 * 멤버가 하나라도 있으면 접속 중이다. 탭·기기를 여러 개 열어도 전부 끊기거나 만료돼야 미접속이다.</p>
 *
 * <p><b>왜 만료가 필요한가.</b> 배포가 컨테이너를 강제 종료하거나 서버가 죽으면 접속 해제 이벤트가 오지
 * 않는다. 예전 구조(만료 없는 Set)에서는 그 기록이 영구히 남아, 배포 순간 접속해 있던 사용자가 이후
 * 푸시를 계속 못 받았다(#81). 이제 세션을 실제로 들고 있는 서버가 {@link #LEASE}보다 짧은 주기로 만료를
 * 연장하고({@link #refreshLeases}), 서버가 사라지면 연장이 멈춰서 최대 {@link #LEASE} 뒤 기록이
 * 판정에서 빠진다.</p>
 *
 * <p><b>인스턴스 ID.</b> 서버가 켜질 때마다 새로 만든다. 재시작한 서버는 예전 자기 기록을 연장하지 않으니
 * 그 기록은 스스로 만료되고, 다른 서버의 기록은 건드리지 않는다 — 기동 시 전체 기록을 지우지 않는 이유다
 * (서버가 2대라 다른 서버의 살아 있는 기록까지 지워진다).</p>
 *
 * <p><b>Redis 장애.</b> 등록·연장·해제 실패는 예외를 던지지 않는다(접속자 명단은 보조 정보라 실제 연결을
 * 끊으면 안 된다). 로컬 세션 목록은 그대로라서 다음 연장 주기에 다시 기록되며 스스로 회복한다. 조회
 * 실패는 "미접속"으로 본다 — 알림을 빠뜨리는 것보다 한 번 더 가는 쪽이 안전하다.</p>
 */
@Slf4j
@Service
public class ChatPresenceService {

    static final String KEY_PREFIX = "facecook:chat:presence:v2:";
    /** 만료 없이 영구히 남던 예전 형식의 키({@code facecook:chat:presence:{userId}}). 기동 시 정리한다. */
    private static final String LEGACY_KEY_PREFIX = "facecook:chat:presence:";
    private static final Pattern LEGACY_KEY = Pattern.compile("^facecook:chat:presence:\\d+$");

    /** 기록 하나가 연장 없이 살아 있는 시간. 서버가 사라진 뒤 최대 이만큼 "접속 중"으로 남을 수 있다. */
    static final Duration LEASE = Duration.ofSeconds(90);
    /** 연장 주기. {@link #LEASE}의 1/3이라 연장이 두 번 연속 실패해도 기록이 끊기지 않는다. */
    static final long REFRESH_INTERVAL_MS = 30_000;

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;
    private final String instanceId = UUID.randomUUID().toString();
    /** 이 서버가 지금 실제로 들고 있는 세션(세션 ID → userId). 연장 대상은 이 목록뿐이다. */
    private final Map<String, Long> localSessions = new ConcurrentHashMap<>();

    public ChatPresenceService(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    /**
     * userId의 세션을 접속 중으로 기록한다(STOMP CONNECT 시 호출).
     *
     * <p>부작용: 로컬 세션 목록에 추가하고 Redis에 {@link #LEASE}만큼 유효한 기록을 쓴다. Redis 쓰기가
     * 실패해도 로컬 목록에는 남아 다음 {@link #refreshLeases}에서 다시 쓴다.</p>
     *
     * <p>예외 없음.</p>
     */
    public void connected(Long userId, String sessionId) {
        localSessions.put(sessionId, userId);
        try {
            writeLease(userId, sessionId, clock.millis() + LEASE.toMillis());
        } catch (DataAccessException exception) {
            log.warn("WebSocket 접속자 등록에 실패했습니다(다음 연장 주기에 다시 기록). userId={}", userId, exception);
        }
    }

    /**
     * userId의 세션 기록을 지운다(STOMP 연결 종료 시 호출).
     *
     * <p>부작용: 로컬 세션 목록에서 빼고 Redis 멤버 하나를 {@code ZREM}으로 지운다. 명령 하나라 같은
     * 유저의 새 연결 등록과 겹쳐도 새 기록을 지우지 않는다(마지막 멤버가 빠지면 Redis가 키를 알아서 지운다).
     * Redis 삭제가 실패해도 로컬 목록에서는 빠졌으므로 더 연장되지 않고 {@link #LEASE} 뒤 만료된다.</p>
     *
     * <p>예외 없음.</p>
     */
    public void disconnected(Long userId, String sessionId) {
        localSessions.remove(sessionId);
        try {
            redisTemplate.opsForZSet().remove(key(userId), member(sessionId));
        } catch (DataAccessException exception) {
            log.warn("WebSocket 접속자 해제에 실패했습니다({}초 뒤 만료). userId={}",
                    LEASE.toSeconds(), userId, exception);
        }
    }

    /**
     * userId에게 만료되지 않은 접속 기록이 하나라도 있는지 확인한다(푸시 생략 여부 판단).
     *
     * <p>부작용: 없음.</p>
     *
     * <p>예외 없음 — Redis 조회가 실패하면 미접속(false)으로 본다.</p>
     */
    public boolean isConnected(Long userId) {
        try {
            Long live = redisTemplate.opsForZSet().count(key(userId), clock.millis() + 1, Double.POSITIVE_INFINITY);
            return live != null && live > 0L;
        } catch (DataAccessException exception) {
            log.warn("WebSocket 접속 여부 조회에 실패했습니다. userId={}", userId, exception);
            return false;
        }
    }

    /**
     * 이 서버가 들고 있는 세션의 만료를 연장한다. 이미 만료된 멤버(다른 서버가 죽으며 남긴 것 등)도
     * 같은 키를 쓰는 김에 지운다.
     *
     * <p>부작용: 로컬 세션마다 Redis 쓰기. 실패는 로그만 남기고 다음 주기에 다시 시도한다.</p>
     *
     * <p>예외 없음.</p>
     */
    @Scheduled(fixedDelay = REFRESH_INTERVAL_MS, initialDelay = REFRESH_INTERVAL_MS)
    public void refreshLeases() {
        long now = clock.millis();
        long expiresAt = now + LEASE.toMillis();
        for (Map.Entry<String, Long> session : List.copyOf(localSessions.entrySet())) {
            try {
                writeLease(session.getValue(), session.getKey(), expiresAt);
                redisTemplate.opsForZSet().removeRangeByScore(key(session.getValue()), Double.NEGATIVE_INFINITY, now);
            } catch (DataAccessException exception) {
                log.warn("WebSocket 접속자 만료 연장에 실패했습니다(다음 주기에 다시 시도). userId={}",
                        session.getValue(), exception);
            }
        }
    }

    /**
     * 예전 형식(만료 없는 Set) 키를 지운다. 이 키에는 영구히 남은 기록이 들어 있어 읽지 않고 버린다 —
     * 배포 중 잠깐 옛 서버에 연결된 사용자가 미접속으로 보여 푸시가 한 번 더 갈 수 있지만, 그쪽이 안전하다.
     * 새 형식({@code v2:}) 키는 건드리지 않는다.
     *
     * <p>부작용: 예전 형식 키 삭제. 실패하면 로그만 남긴다(새 구조는 예전 키를 읽지 않으므로 기능에 영향 없음).</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void removeLegacyKeys() {
        try {
            List<String> legacyKeys = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions().match(LEGACY_KEY_PREFIX + "*").count(500).build();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                cursor.forEachRemaining(key -> {
                    if (LEGACY_KEY.matcher(key).matches()) {
                        legacyKeys.add(key);
                    }
                });
            }
            if (!legacyKeys.isEmpty()) {
                redisTemplate.delete(legacyKeys);
                log.info("예전 형식 접속자 키 {}개를 정리했습니다.", legacyKeys.size());
            }
        } catch (DataAccessException exception) {
            log.warn("예전 형식 접속자 키 정리에 실패했습니다.", exception);
        }
    }

    private void writeLease(Long userId, String sessionId, long expiresAt) {
        String key = key(userId);
        redisTemplate.opsForZSet().add(key, member(sessionId), expiresAt);
        // 모든 멤버가 만료된 뒤 키 자체도 남지 않게 한다(판정은 점수로 하므로 이 TTL은 청소용이다).
        redisTemplate.expire(key, LEASE);
    }

    private String member(String sessionId) {
        return instanceId + ":" + sessionId;
    }

    private static String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
