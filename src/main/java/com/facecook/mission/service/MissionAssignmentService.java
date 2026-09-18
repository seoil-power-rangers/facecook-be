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

/**
 * 매칭별 STEP 1~3 랜덤 미션을 "필요할 때 한 번만" 배정한다.
 *
 * <p>오버로드 두 개는 호출자가 {@code MatchMission}을 이미 잠갔는지로
 * 갈린다 — {@link #assignIfAbsent(Long)}는 안 잠근 호출자용(필요하면
 * 스스로 새 트랜잭션에서 잠근다), {@link #assignIfAbsent(MatchMission)}은
 * 이미 잠근 호출자용(다시 조회·잠그지 않고 그대로 씀). 실제 배정
 * 로직은 이 클래스가 아니라 {@link MissionAssignmentWriter}에 있다 —
 * 이 클래스는 "정말 배정이 필요한지" 판단(락 없이 빠르게 읽기)만 하고,
 * 필요할 때만 쓰기 담당에게 넘긴다.</p>
 */
@Service
@RequiredArgsConstructor
public class MissionAssignmentService {

    private static final int FIRST_STEP = 1;
    private static final int LAST_STEP = 3;

    private final MatchMissionRepository matchMissionRepository;
    private final MatchMissionAssignmentRepository assignmentRepository;
    private final MissionAssignmentWriter assignmentWriter;

    /**
     * matchId의 현재 STEP부터 STEP 3까지 미션 배정을 반환한다. 필요한
     * 배정이 이미 있으면 읽기만 하고 반환한다.
     *
     * <p>전제조건: matchId 존재. 호출자가 {@code MatchMission}을 잠그고
     * 있지 않아도 된다(이 메서드가 필요하면 스스로 잠근다) — 잠그고
     * 있는 상태라면 대신 {@link #assignIfAbsent(MatchMission)}을 써야
     * 한다(같은 행을 이중으로 잠그려 들면 안 됨).</p>
     *
     * <p>부작용: 배정이 부족한 경우에만 별도 쓰기 트랜잭션
     * ({@code REQUIRES_NEW})에서 match_info 행을 잠그고 다시 확인한 뒤
     * 모자란 STEP만 랜덤으로 배정해서 저장한다({@link
     * MissionAssignmentWriter#assignWithLock}). 다 있으면 쓰기 자체가
     * 없다.</p>
     *
     * <p>예외: {@code NOT_FOUND}(매칭 없음).</p>
     *
     * @see #assignIfAbsent(MatchMission)
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
     * mission의 현재 STEP부터 STEP 3까지 미션 배정을 반환한다.
     * {@link #assignIfAbsent(Long)}과 결과는 같지만, 호출자가 이미
     * 잠근 {@code MatchMission}을 그대로 재사용한다.
     *
     * <p>전제조건: 호출자가 진행 중인 트랜잭션 안에서 이미 mission을
     * 잠갔어야 함(예: {@code findByIdForUpdate}로). 트랜잭션이 전혀 없는
     * 상태로 부르면 {@code MANDATORY} 전파 설정 때문에 예외가 난다
     * ({@link MissionAssignmentWriter#assignWithLockedMission} 참고).</p>
     *
     * <p>부작용: {@link #assignIfAbsent(Long)}과 같음(모자란 STEP만
     * 배정·저장) — 다만 같은 트랜잭션 안에서 처리해서 행을 다시
     * 잠그지 않는다.</p>
     *
     * <p>예외 없음(mission 자체는 호출자가 이미 조회해서 들고 있음).</p>
     *
     * @see #assignIfAbsent(Long)
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
