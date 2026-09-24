package com.facecook.profile.service;

import com.facecook.auth.entity.User;
import com.facecook.auth.repository.UserRepository;
import com.facecook.config.ProfileActivityProperties;
import com.facecook.profile.dto.ProfileResponse;
import com.facecook.profile.entity.Profile;
import com.facecook.common.time.EventTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Profile.getUser().getLastActiveAt()에서 lastActiveAt·isActive를 만든다.
 * 발급(profileApi 응답)과 판정 기준(활성 시간 임계값)이 여기 한 곳에만
 * 있어야, 화면마다 기준이 어긋나는 일이 없다.
 *
 * profile.getUser()는 지연 로딩 프록시라, 여러 건을 한 번에 다룰 땐
 * ProfileRepository가 @EntityGraph로 미리 User를 조인해서 가져온
 * Profile만 넘겨야 한다 — 안 그러면 건별로 조회가 나가 N+1이 된다.
 *
 * 다만 saveAndFlush 직후처럼 영속성 컨텍스트에 user 없이 올라온 Profile은
 * findById(EntityGraph)를 다시 호출해도 1차 캐시 때문에 user가 비어 있을 수
 * 있다 — 이때는 userId로 users를 직접 조회한다.
 */
@Component
@RequiredArgsConstructor
public class ProfileActivityLookup {

    private final UserRepository userRepository;
    private final ProfileActivityProperties properties;
    private final Clock clock;

    /**
     * 프로필 한 건을 응답으로 바꾼다. 마지막 활동 시각과 "활동 중" 여부를 같이 채운다.
     * 호출: {@code ProfileService}(작성·조회·수정).
     */
    public ProfileResponse toResponse(Profile profile) {
        return build(profile, resolveLastActiveAt(profile));
    }

    /**
     * 여러 프로필을 응답으로 바꾼다. 입력 순서를 그대로 유지한다 — 호출부가 이미 정해둔
     * 정렬(userId 오름차순 등)을 지킨다.
     *
     * <p>호출: {@code ProfileService}(탐색 목록), {@code CookService}(콕 목록의 상대 프로필),
     * {@code MatchService}(매칭 상대 프로필). 모두 {@code @EntityGraph}로 User를 미리 조인한
     * 목록을 넘긴다.</p>
     */
    public List<ProfileResponse> toResponses(List<Profile> profiles) {
        return profiles.stream()
                .map(profile -> build(profile, resolveLastActiveAt(profile)))
                .toList();
    }

    private LocalDateTime resolveLastActiveAt(Profile profile) {
        User user = profile.getUser();
        if (user != null) {
            return user.getLastActiveAt();
        }
        return userRepository.findById(profile.getUserId())
                .map(User::getLastActiveAt)
                .orElse(null);
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
        return EventTime.now(clock).minusMinutes(properties.activeWindowMinutes());
    }

}
