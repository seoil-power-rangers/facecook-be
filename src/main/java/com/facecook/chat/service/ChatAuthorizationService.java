package com.facecook.chat.service;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.match.entity.MatchInfo;
import com.facecook.match.repository.MatchInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "이 유저가 이 매칭의 당사자인가"만 확인하는 전용 클래스. {@link
 * com.facecook.mission.service.MissionAuthorizationService}와 로직이
 * 완전히 동일하다 — {@code MatchInfo}(이 클래스)와
 * {@code MatchMission}(그쪽)이 같은 물리 테이블(match_info)을 가리키는
 * 서로 다른 JPA 엔티티라 이렇게 중복돼있다.
 */
@Service
@RequiredArgsConstructor
public class ChatAuthorizationService {

    private final MatchInfoRepository matchInfoRepository;

    /**
     * matchId 매칭에 userId가 당사자로 포함돼 있는지 확인하고, 맞으면
     * 그 {@code MatchInfo}를 반환한다.
     *
     * <p>전제조건: matchId 존재.</p>
     *
     * <p>부작용: 없음.</p>
     *
     * <p>예외: {@code NOT_FOUND}(매칭 없음), {@code FORBIDDEN}(당사자
     * 아님).</p>
     *
     * @see com.facecook.mission.service.MissionAuthorizationService#requireParticipant(Long, Long)
     */
    @Transactional(readOnly = true)
    public MatchInfo requireParticipant(Long matchId, Long userId) {
        MatchInfo matchInfo = matchInfoRepository.findById(matchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "매칭을 찾을 수 없습니다."));
        if (!matchInfo.includes(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return matchInfo;
    }
}
