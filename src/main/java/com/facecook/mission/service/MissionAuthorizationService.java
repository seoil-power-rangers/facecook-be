package com.facecook.mission.service;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.mission.entity.MatchMission;
import com.facecook.mission.repository.MatchMissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MissionAuthorizationService {

    private final MatchMissionRepository matchMissionRepository;

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
