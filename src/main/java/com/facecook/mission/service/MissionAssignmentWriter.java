package com.facecook.mission.service;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.mission.entity.MatchMission;
import com.facecook.mission.entity.MatchMissionAssignment;
import com.facecook.mission.entity.MissionTemplate;
import com.facecook.mission.repository.MatchMissionAssignmentRepository;
import com.facecook.mission.repository.MatchMissionRepository;
import com.facecook.mission.repository.MissionTemplateRepository;
import com.facecook.common.time.EventTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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

    /**
     * 매칭에 모자란 STEP의 미션을 채운다. STEP1~3은 같은 묶음(bundle)에서만 나온다 — 이미 배정된
     * STEP이 있으면 그 템플릿의 묶음을 그대로 쓰고, 하나도 없으면 새 묶음을 무작위로 고른다. 새 매칭은
     * STEP1~3이 이 호출 한 번에 같이 채워지므로(currentStep이 1에서 시작) 보통은 항상 새 묶음을 고르는
     * 경로를 탄다 — 이미 배정된 일부만 남은 상태에서 나머지를 채우는 경우에도 같은 묶음이 유지된다.
     */
    private List<MatchMissionAssignment> assignMissing(MatchMission mission) {
        List<MatchMissionAssignment> assignments = new ArrayList<>(
                assignmentRepository.findAllByMatchIdOrderByStep(mission.getMatchId())
        );
        int firstAssignableStep = Math.max(MatchMission.FIRST_STEP, mission.getCurrentStep());
        if (firstAssignableStep > MatchMission.LAST_STEP) {
            return List.copyOf(assignments);
        }

        Long bundleId = resolveBundleId(assignments);
        for (int step = firstAssignableStep; step <= MatchMission.LAST_STEP; step++) {
            int targetStep = step;
            if (assignments.stream().noneMatch(assignment -> assignment.getStep() == targetStep)) {
                MissionTemplate template = templateForBundleStep(bundleId, step);
                assignments.add(assignmentRepository.save(
                        MatchMissionAssignment.assign(
                                mission.getMatchId(),
                                step,
                                template,
                                EventTime.now(clock)
                        )
                ));
            }
        }
        assignments.sort(Comparator.comparingInt(MatchMissionAssignment::getStep));
        return List.copyOf(assignments);
    }

    /** 이미 배정된 STEP이 있으면 그 묶음을, 없으면 새 묶음을 무작위로 고른다. */
    private Long resolveBundleId(List<MatchMissionAssignment> existingAssignments) {
        return existingAssignments.stream()
                .findFirst()
                .map(assignment -> assignment.getTemplate().getBundleId())
                .orElseGet(this::randomBundleId);
    }

    private Long randomBundleId() {
        List<Long> bundleIds = templateRepository.findDistinctBundleIds();
        if (bundleIds.isEmpty()) {
            throw new MissionTemplateNotFoundException("미션 묶음이 없습니다.");
        }
        return bundleIds.get(ThreadLocalRandom.current().nextInt(bundleIds.size()));
    }

    private MissionTemplate templateForBundleStep(Long bundleId, int step) {
        return templateRepository.findByBundleIdAndStep(bundleId, step)
                .orElseThrow(() -> new MissionTemplateNotFoundException(
                        "묶음 " + bundleId + "의 STEP " + step + " 미션 템플릿이 없습니다."));
    }
}
