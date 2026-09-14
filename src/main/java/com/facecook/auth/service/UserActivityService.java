package com.facecook.auth.service;

import com.facecook.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * "현재 활동 중" 표시(기능명세 2절)의 근거가 되는 users.last_active_at을
 * 갱신한다. 인증된 요청마다 호출되므로 컬럼 하나만 바꾸는 벌크 업데이트를
 * 쓴다 — 엔티티를 통째로 읽고 쓰면 요청마다 낭비가 크다.
 */
@Service
@RequiredArgsConstructor
public class UserActivityService {

    private static final ZoneId EVENT_ZONE = ZoneId.of("Asia/Seoul");

    /**
     * 이 시간 안에 이미 갱신됐으면 다시 쓰지 않는다. 프론트가 5초 간격으로
     * 폴링하는 화면이 많아서 디바운스가 없으면 인증된 요청마다 쓰기가
     * 발생한다 — "활동 중" 판정 기준(15분)에 비하면 30초 오차는 무의미하다.
     */
    private static final Duration TOUCH_DEBOUNCE = Duration.ofSeconds(30);

    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public void touch(Long userId) {
        LocalDateTime now = now();
        userRepository.touchLastActiveAt(userId, now, now.minus(TOUCH_DEBOUNCE));
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), EVENT_ZONE);
    }
}
