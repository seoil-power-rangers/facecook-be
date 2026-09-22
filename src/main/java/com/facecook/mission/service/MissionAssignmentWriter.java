package com.facecook.mission.service;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.mission.entity.MatchMission;
import com.facecook.mission.entity.MatchMissionAssignment;
import com.facecook.mission.entity.MissionTemplate;
import com.facecook.mission.repository.MatchMissionAssignmentRepository;
import com.facecook.mission.repository.MatchMissionRepository;
import com.facecook.mission.repository.MissionTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class MissionAssignmentWriter {

    private final MatchMissionRepository matchMissionRepository;
    private final MatchMissionAssignmentRepository assignmentRepository;
    private final MissionTemplateRepository templateRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<MatchMissionAssignment> assignWithLock(Long matchId) {
        MatchMission mission = matchMissionRepository.findByIdForUpdate(matchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "매칭을 찾을 수 없습니다."));
        return assignMissing(mission);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<MatchMissionAssignment> assignWithLockedMission(MatchMission mission) {
        return assignMissing(mission);
    }

    private List<MatchMissionAssignment> assignMissing(MatchMission mission) {
        List<MatchMissionAssignment> assignments = new ArrayList<>(
                assignmentRepository.findAllByMatchIdOrderByStep(mission.getMatchId())
        );
        int firstAssignableStep = Math.max(MatchMission.FIRST_STEP, mission.getCurrentStep());
        for (int step = firstAssignableStep; step <= MatchMission.LAST_STEP; step++) {
            int targetStep = step;
            if (assignments.stream().noneMatch(assignment -> assignment.getStep() == targetStep)) {
                MissionTemplate template = randomTemplate(step);
                assignments.add(assignmentRepository.save(
                        MatchMissionAssignment.assign(
                                mission.getMatchId(),
                                step,
                                template,
                                LocalDateTime.now(clock)
                        )
                ));
            }
        }
        assignments.sort(Comparator.comparingInt(MatchMissionAssignment::getStep));
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
