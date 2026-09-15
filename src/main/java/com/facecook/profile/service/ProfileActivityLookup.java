package com.facecook.profile.service;

import com.facecook.config.ProfileActivityProperties;
import com.facecook.profile.dto.ProfileResponse;
import com.facecook.profile.entity.Profile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Profile.getUser().getLastActiveAt()에서 lastActiveAt·isActive를 만든다.
 * 발급(profileApi 응답)과 판정 기준(활성 시간 임계값)이 여기 한 곳에만
 * 있어야, 화면마다 기준이 어긋나는 일이 없다.
 *
 * profile.getUser()는 지연 로딩 프록시라, 여러 건을 한 번에 다룰 땐
 * ProfileRepository가 @EntityGraph로 미리 User를 조인해서 가져온
 * Profile만 넘겨야 한다 — 안 그러면 건별로 조회가 나가 N+1이 된다.
 */
@Component
@RequiredArgsConstructor
public class ProfileActivityLookup {

    private static final ZoneId EVENT_ZONE = ZoneId.of("Asia/Seoul");

    private final ProfileActivityProperties properties;
    private final Clock clock;

    public ProfileResponse toResponse(Profile profile) {
        return build(profile, profile.getUser().getLastActiveAt());
    }

    /** 입력 순서를 그대로 유지한다 — 호출부가 이미 정해둔 정렬(userId 오름차순 등)을 지킨다. */
    public List<ProfileResponse> toResponses(List<Profile> profiles) {
        return profiles.stream()
                .map(profile -> build(profile, profile.getUser().getLastActiveAt()))
                .toList();
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
