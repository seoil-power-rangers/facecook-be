package com.facecook.mission.service;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.mission.entity.MatchMission;
import com.facecook.mission.entity.MatchMissionAssignment;
import com.facecook.mission.repository.MatchMissionAssignmentRepository;
import com.facecook.mission.repository.MatchMissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class MissionAssignmentService {

    private static final int FIRST_STEP = 1;
    private static final int LAST_STEP = 3;

    private final MatchMissionRepository matchMissionRepository;
    private final MatchMissionAssignmentRepository assignmentRepository;
    private final MissionAssignmentWriter assignmentWriter;

    /**
     * 필요한 배정이 이미 있으면 읽기만 하고 반환한다. 배정이 부족한 경우에만
     * 별도 쓰기 트랜잭션에서 match_info 행을 잠그고 다시 확인한다.
     */
    public List<MatchMissionAssignment> assignIfAbsent(Long matchId) {
        List<MatchMissionAssignment> assignments =
                assignmentRepository.findAllByMatchIdOrderByStep(matchId);
        MatchMission mission = matchMissionRepository.findById(matchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "매칭을 찾을 수 없습니다."));

        if (hasAllRequiredAssignments(mission, assignments)) {
            return sortedCopy(assignments);
        }
        return assignmentWriter.assignWithLock(matchId);
    }

    /**
     * 호출자가 이미 잠근 MatchMission을 재사용해 같은 행을 다시 조회하거나
     * 잠그지 않는다. 쓰기 트랜잭션 안에서만 호출할 수 있다.
     */
    public List<MatchMissionAssignment> assignIfAbsent(MatchMission mission) {
        return assignmentWriter.assignWithLockedMission(mission);
    }

    static boolean hasAllRequiredAssignments(
            MatchMission mission,
            List<MatchMissionAssignment> assignments
    ) {
        int firstRequiredStep = Math.max(FIRST_STEP, mission.getCurrentStep());
        return IntStream.rangeClosed(firstRequiredStep, LAST_STEP)
                .allMatch(step -> assignments.stream()
                        .anyMatch(assignment -> assignment.getStep() == step));
    }

    private static List<MatchMissionAssignment> sortedCopy(
            List<MatchMissionAssignment> assignments
    ) {
        return assignments.stream()
                .sorted(Comparator.comparingInt(MatchMissionAssignment::getStep))
                .toList();
    }
}
