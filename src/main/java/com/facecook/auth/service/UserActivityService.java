package com.facecook.auth.service;

import com.facecook.auth.repository.UserRepository;
import com.facecook.common.time.EventTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * "현재 활동 중" 표시(기능명세 2절)의 근거가 되는 users.last_active_at을
 * 갱신한다. 인증된 요청마다 호출되므로 컬럼 하나만 바꾸는 벌크 업데이트를
 * 쓴다 — 엔티티를 통째로 읽고 쓰면 요청마다 낭비가 크다.
 */
@Service
@RequiredArgsConstructor
public class UserActivityService {

    /**
     * 이 시간 안에 이미 갱신됐으면 다시 쓰지 않는다. 프론트가 5초 간격으로
     * 폴링하는 화면이 많아서 디바운스가 없으면 인증된 요청마다 쓰기가
     * 발생한다 — "활동 중" 판정 기준(15분)에 비하면 30초 오차는 무의미하다.
     */
    private static final Duration TOUCH_DEBOUNCE = Duration.ofSeconds(30);

    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * 이 서버에서 사용자별로 마지막으로 DB에 갱신을 보낸 시각. {@link #TOUCH_DEBOUNCE} 안이면 DB에 가지 않는다.
     * 서버마다 따로 기억하므로, 사용자 한 명당 서버마다 30초에 최대 한 번 UPDATE가 나간다. 참가자 수(수백 명)만큼만
     * 쌓이고, 서버가 다시 뜨면 비워진다.
     */
    private final ConcurrentMap<Long, LocalDateTime> lastSentAt = new ConcurrentHashMap<>();

    /**
     * userId의 마지막 활동 시각을 지금으로 갱신한다.
     *
     * <p>전제조건: 없음(userId가 실제 존재하지 않아도 조용히 0행
     * 갱신으로 끝난다 — 벌크 업데이트라 별도 조회·예외 없음).</p>
     *
     * <p>부작용: 이 서버에서 {@link #TOUCH_DEBOUNCE}(30초) 안에 이미 갱신을 보냈으면 DB에 아무것도 보내지 않는다
     * (트랜잭션도 열지 않음). 그 밖에는 {@code users.last_active_at} 컬럼만 갱신한다. 다른 서버가 최근에
     * 갱신했으면 DB의 WHERE 조건(staleBefore)이 한 번 더 걸러 쓰기가 일어나지 않는다.</p>
     *
     * <p>예외 없음.</p>
     */
    public void touch(Long userId) {
        LocalDateTime now = EventTime.now(clock);
        LocalDateTime staleBefore = now.minus(TOUCH_DEBOUNCE);
        LocalDateTime previous = lastSentAt.get(userId);
        if (previous != null && previous.isAfter(staleBefore)) {
            return;
        }
        lastSentAt.put(userId, now);
        userRepository.touchLastActiveAt(userId, now, staleBefore);
    }

}
