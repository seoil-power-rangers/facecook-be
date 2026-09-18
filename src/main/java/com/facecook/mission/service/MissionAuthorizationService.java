package com.facecook.mission.service;

import com.facecook.chat.service.ChatAuthorizationService;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.mission.entity.MatchMission;
import com.facecook.mission.repository.MatchMissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "이 유저가 이 매칭의 당사자인가"만 확인하는 전용 클래스.
 * {@link ChatAuthorizationService}와 똑같은 모양의 검사를 미션 도메인
 * 전용으로 한 번 더 두고 있다 — {@code MatchMission}과
 * {@code MatchInfo}는 같은 물리 테이블(match_info)이지만 서로 다른
 * JPA 엔티티라 하나로 합칠 수 없다.
 */
@Service
@RequiredArgsConstructor
public class MissionAuthorizationService {

    private final MatchMissionRepository matchMissionRepository;

    /**
     * matchId 매칭에 userId가 당사자로 포함돼 있는지 확인하고, 맞으면
     * 그 {@code MatchMission}을 반환한다.
     *
     * <p>전제조건: matchId 존재.</p>
     *
     * <p>부작용: 없음.</p>
     *
     * <p>예외: {@code NOT_FOUND}(매칭 없음), {@code FORBIDDEN}(당사자
     * 아님).</p>
     *
     * @see ChatAuthorizationService#requireParticipant(Long, Long)
     */
    @Transactional(readOnly = true)
    public MatchMission requireParticipant(Long matchId, Long userId) {
        MatchMission mission = matchMissionRepository.findById(matchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "매칭을 찾을 수 없습니다."));
        if (!mission.includes(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return mission;
    }
}
