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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 매칭별 랜덤 미션(STEP 1~3)의 진행 상황 조회·완료 처리.
 *
 * <p>참가자는 자기 매칭 것만 {@link #getProgressAndAssignIfMissing} 조회할 수 있고, 완료
 * 처리({@link #completeCurrentStep})는 관리자만 한다 — 참가자가 스스로
 * "완료"를 누르는 API는 없다(운영진이 부스에서 실물로 확인한 뒤 처리).
 * 권한 검사는 참가자 쪽만 이 클래스가 직접 하고({@link
 * MissionAuthorizationService}), 관리자 권한 검사는 컨트롤러 계층
 * ({@code AdminAuthorization})에서 끝내고 들어온다 — 이 클래스는 호출자가
 * 이미 관리자라고 전제한다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MissionService {
    private final MatchMissionRepository matchMissionRepository;
    private final MissionAssignmentService assignmentService;
    private final MissionAuthorizationService authorizationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * userId가 자기 매칭(matchId)의 현재 미션 진행 상황(현재 STEP, 배정된
     * 미션 문구, STEP별 완료 시각)을 조회한다. 이름 그대로 조회이지만, 이 매칭에
     * 아직 배정된 미션이 없으면 이 호출 안에서 배정까지 한다({@link #ensureAssigned}).
     *
     * <p>전제조건: matchId 존재, userId가 그 매칭의 당사자.</p>
     *
     * <p>부작용: STEP별 미션이 아직 배정 안 돼 있으면 랜덤으로 배정해서 저장한다
     * (배정 자체는 매칭당 한 번만 — {@link MissionAssignmentService#assignIfAbsent(Long)}).</p>
     *
     * <p>예외: {@code NOT_FOUND}(매칭 없음), {@code FORBIDDEN}(당사자
     * 아님).</p>
     *
     * @see #completeCurrentStep(Long, Long)
     */
    @Transactional(readOnly = true)
    public MissionProgressResponse getProgressAndAssignIfMissing(Long matchId, Long userId) {
        MatchMission mission = requireParticipant(matchId, userId);
        List<MatchMissionAssignment> assignments = ensureAssigned(matchId);
        return buildParticipantResponse(mission, assignments);
    }

    /**
     * 전체 매칭의 미션 진행 현황을 관리자 화면용으로 반환한다(매칭 성사
     * 시각 최신순).
     *
     * <p>전제조건: 호출자가 관리자임은 컨트롤러 계층에서 이미 검증됐다고
     * 전제한다 — 이 메서드 자체는 권한을 확인하지 않는다.</p>
     *
     * <p>부작용: {@link #getProgressAndAssignIfMissing}과 마찬가지로 매칭마다 미션 배정이
     * 없으면 이 호출 중에 배정한다. 한 가지 다른 점: 배정 중 어떤 매칭에서
     * 예외가 나도 전체 목록이 실패하지 않는다 — 그 매칭만 목록에서 빠지고
     * 에러 로그만 남는다({@link #toAdminProgressOrNull}). 관리자 화면이
     * 매칭 하나 때문에 통째로 안 뜨는 것보다, 문제 있는 매칭 하나 빠지는
     * 게 낫다고 판단한 것.</p>
     *
     * <p>예외 없음(내부에서 전부 흡수).</p>
     *
     * @see #completeCurrentStep(Long, Long)
     */
    @Transactional(readOnly = true)
    public List<AdminMissionProgressResponse> getAllProgress() {
        return matchMissionRepository.findAll(Sort.by(Sort.Direction.DESC, "matchedAt")).stream()
                .map(this::toAdminProgressOrNull)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * adminId(관리자)가 matchId의 현재 STEP을 완료 처리하고 다음 STEP으로
     * 넘긴다.
     *
     * <p>전제조건: matchId 존재, 아직 전체 완료({@code currentStep <
     * COMPLETED_STEP})가 아님.</p>
     *
     * <p>부작용: {@code MatchMission} 행을 잠근 뒤(동시 완료 처리 방지)
     * currentStep을 1 올리고 완료 시각·처리자를 기록한다. 트랜잭션
     * 커밋 후 {@code MissionProgressCommittedEvent}를 발행해서 Redis
     * pub/sub → WebSocket으로 참가자 화면에 실시간 반영된다(비동기 —
     * 이 실시간 알림이 실패해도 완료 처리 자체는 이미 성공한 상태다,
     * {@link com.facecook.mission.event.MissionProgressCommittedListener}
     * 참고).</p>
     *
     * <p>예외: {@code NOT_FOUND}(매칭 없음), {@code VALIDATION}(이미
     * 전체 완료된 매칭).</p>
     *
     * @see #getProgressAndAssignIfMissing(Long, Long)
     * @see com.facecook.mission.event.MissionProgressCommittedListener
     */
    @Transactional
    public AdminMissionProgressResponse completeCurrentStep(Long matchId, Long adminId) {
        MatchMission mission = matchMissionRepository.findByIdForUpdate(matchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "매칭을 찾을 수 없습니다."));
        List<MatchMissionAssignment> assignments = assignmentService.assignIfAbsent(mission);
        mission.completeCurrentStep(adminId, LocalDateTime.now(clock));
        MissionProgressResponse participantProgress = MissionProgressResponse.from(mission, assignments);
        eventPublisher.publishEvent(new MissionProgressCommittedEvent(participantProgress));
        return AdminMissionProgressResponse.from(mission, assignments);
    }

    /** userId가 matchId의 당사자인지 확인하고 {@code MatchMission}을 돌려준다. */
    private MatchMission requireParticipant(Long matchId, Long userId) {
        return authorizationService.requireParticipant(matchId, userId);
    }

    /** matchId에 STEP별 미션이 이미 배정돼 있으면 그대로, 없으면 이 호출 안에서 랜덤 배정한다. */
    private List<MatchMissionAssignment> ensureAssigned(Long matchId) {
        return assignmentService.assignIfAbsent(matchId);
    }

    private MissionProgressResponse buildParticipantResponse(
            MatchMission mission,
            List<MatchMissionAssignment> assignments
    ) {
        return MissionProgressResponse.from(mission, assignments);
    }

    private AdminMissionProgressResponse toAdminProgressOrNull(MatchMission mission) {
        try {
            return AdminMissionProgressResponse.from(
                    mission,
                    assignmentService.assignIfAbsent(mission.getMatchId())
            );
        } catch (RuntimeException exception) {
            log.error("관리자 미션 목록에서 매칭을 제외합니다. matchId={}", mission.getMatchId(), exception);
            return null;
        }
    }
}
