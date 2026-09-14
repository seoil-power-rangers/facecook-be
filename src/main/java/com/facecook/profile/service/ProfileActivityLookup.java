package com.facecook.profile.service;

import com.facecook.auth.entity.User;
import com.facecook.auth.repository.UserRepository;
import com.facecook.config.ProfileActivityProperties;
import com.facecook.profile.dto.ProfileResponse;
import com.facecook.profile.entity.Profile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Profile은 users.last_active_at을 모른다(별도 테이블) — ProfileResponse를
 * 만들 때마다 이 클래스가 그 값을 붙여 lastActiveAt·isActive를 채운다.
 * 발급(profileApi 응답)과 판정 기준(활성 시간 임계값)이 여기 한 곳에만
 * 있어야, 화면마다 기준이 어긋나는 일이 없다.
 */
@Component
@RequiredArgsConstructor
public class ProfileActivityLookup {

    private static final ZoneId EVENT_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final ProfileActivityProperties properties;
    private final Clock clock;

    public ProfileResponse toResponse(Profile profile) {
        LocalDateTime lastActiveAt = userRepository.findById(profile.getUserId())
                .map(User::getLastActiveAt)
                .orElse(null);
        return build(profile, lastActiveAt);
    }

    /** 입력 순서를 그대로 유지한다 — 호출부가 이미 정해둔 정렬(userId 오름차순 등)을 지킨다. */
    public List<ProfileResponse> toResponses(List<Profile> profiles) {
        Map<Long, LocalDateTime> lastActiveByUserId = lastActiveByUserId(
                profiles.stream().map(Profile::getUserId).toList()
        );
        return profiles.stream()
                .map(profile -> build(profile, lastActiveByUserId.get(profile.getUserId())))
                .toList();
    }

    private Map<Long, LocalDateTime> lastActiveByUserId(Collection<Long> userIds) {
        // lastActiveAt이 null일 수 있어(가입 직후 등) Collectors.toMap을 쓰지 않는다
        // — 값이 null이면 NullPointerException을 던지는 게 그 API의 알려진 함정이다.
        Map<Long, LocalDateTime> result = new HashMap<>();
        for (User user : userRepository.findAllById(userIds)) {
            result.put(user.getId(), user.getLastActiveAt());
        }
        return result;
    }

    private ProfileResponse build(Profile profile, LocalDateTime lastActiveAt) {
        boolean isActive = lastActiveAt != null && !lastActiveAt.isBefore(activeSince());
        return ProfileResponse.from(profile, lastActiveAt, isActive);
    }

    /**
     * "지금 활동 중"으로 칠 수 있는 가장 오래된 시각. 목록·필터·통계(참가자용
     * GET /api/stats)가 같은 기준을 쓰도록 공개한다.
     *
     * AdminStatsService.countActiveUsers()의 "활동 유저" 기준(자정부터
     * 하루 단위)과는 일부러 다르다 — 그쪽은 하루 단위 운영 리포트, 여긴
     * 실시간에 가까운 참가자 화면용이라 통일하지 않는다.
     */
    public LocalDateTime activeSince() {
        return now().minusMinutes(properties.activeWindowMinutes());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), EVENT_ZONE);
    }
}
