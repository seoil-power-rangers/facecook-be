package com.facecook.mission.service;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.mission.dto.AdminMissionProgressResponse;
import com.facecook.mission.dto.MissionProgressResponse;
import com.facecook.mission.entity.MatchMission;
import com.facecook.mission.entity.MatchMissionAssignment;
import com.facecook.mission.event.MissionProgressCommittedEvent;
import com.facecook.mission.repository.MatchMissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MissionService {
    private final MatchMissionRepository matchMissionRepository;
    private final MissionAssignmentService assignmentService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public MissionProgressResponse getProgress(Long matchId, Long userId) {
        MatchMission mission = findMission(matchId);
        if (!mission.includes(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return MissionProgressResponse.from(mission, assignmentService.assignIfAbsent(matchId));
    }

    @Transactional
    public List<AdminMissionProgressResponse> getAllProgress() {
        return matchMissionRepository.findAll(Sort.by(Sort.Direction.DESC, "matchedAt")).stream()
                .map(mission -> AdminMissionProgressResponse.from(
                        mission,
                        assignmentService.assignIfAbsent(mission.getMatchId())
                ))
                .toList();
    }

    @Transactional
    public AdminMissionProgressResponse completeCurrentStep(Long matchId, Long adminId) {
        MatchMission mission = matchMissionRepository.findByIdForUpdate(matchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "매칭을 찾을 수 없습니다."));
        List<MatchMissionAssignment> assignments = assignmentService.assignIfAbsent(matchId);
        mission.completeCurrentStep(adminId, LocalDateTime.now(clock));
        MissionProgressResponse participantProgress = MissionProgressResponse.from(mission, assignments);
        eventPublisher.publishEvent(new MissionProgressCommittedEvent(participantProgress));
        return AdminMissionProgressResponse.from(mission, assignments);
    }

    private MatchMission findMission(Long matchId) {
        return matchMissionRepository.findById(matchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "매칭을 찾을 수 없습니다."));
    }
}
