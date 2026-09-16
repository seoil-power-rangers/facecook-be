package com.facecook.mission.service;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.mission.entity.MatchMissionAssignment;
import com.facecook.mission.entity.MissionTemplate;
import com.facecook.mission.repository.MatchMissionAssignmentRepository;
import com.facecook.mission.repository.MatchMissionRepository;
import com.facecook.mission.repository.MissionTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class MissionAssignmentService {

    private static final int FIRST_STEP = 1;
    private static final int LAST_STEP = 3;

    private final MatchMissionRepository matchMissionRepository;
    private final MatchMissionAssignmentRepository assignmentRepository;
    private final MissionTemplateRepository templateRepository;
    private final Clock clock;

    /**
     * match_info 행 잠금과 (match_id, step) UNIQUE 제약을 함께 사용해 신규/기존
     * 매칭 모두 정확히 한 번만 세 STEP을 영구 배정한다.
     */
    @Transactional
    public List<MatchMissionAssignment> assignIfAbsent(Long matchId) {
        matchMissionRepository.findByIdForUpdate(matchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "매칭을 찾을 수 없습니다."));

        List<MatchMissionAssignment> assignments =
                new ArrayList<>(assignmentRepository.findAllByMatchIdOrderByStep(matchId));
        for (int step = FIRST_STEP; step <= LAST_STEP; step++) {
            int targetStep = step;
            if (assignments.stream().noneMatch(assignment -> assignment.getStep() == targetStep)) {
                MissionTemplate template = randomTemplate(step);
                assignments.add(assignmentRepository.save(
                        MatchMissionAssignment.assign(
                                matchId,
                                step,
                                template,
                                LocalDateTime.now(clock)
                        )
                ));
            }
        }
        assignments.sort(java.util.Comparator.comparingInt(MatchMissionAssignment::getStep));
        return List.copyOf(assignments);
    }

    private MissionTemplate randomTemplate(int step) {
        List<MissionTemplate> templates = templateRepository.findAllByStep(step);
        if (templates.isEmpty()) {
            throw new IllegalStateException("STEP " + step + " 미션 템플릿이 없습니다.");
        }
        return templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
    }
}
