package com.facecook.mission.service;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.mission.dto.AdminMissionExclusionResponse;
import com.facecook.mission.dto.AdminMissionListResponse;
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
import java.util.ArrayList;
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
     * @see #completeCurrentStep(Long, Long, int)
     */
    @Transactional(readOnly = true)
    public MissionProgressResponse getProgressAndAssignIfMissing(Long matchId, Long userId) {
        MatchMission mission = requireParticipant(matchId, userId);
        List<MatchMissionAssignment> assignments = ensureAssigned(matchId);
        return buildParticipantResponse(mission, assignments);
    }

    /**
     * 전체 매칭의 미션 진행 현황을 관리자 화면용으로 반환한다(매칭 성사
     * 시각 최신순). 배정 실패로 빠진 매칭은 조용히 버려진다 — 구 FE와의
     * 호환을 위해 배열 그대로 유지한다({@link #getAllProgressWithExclusions}
     * 참고).
     *
     * @see #getAllProgressWithExclusions()
     * @see #completeCurrentStep(Long, Long, int)
     */
    @Transactional(readOnly = true)
    public List<AdminMissionProgressResponse> getAllProgress() {
        return collectAllProgress().items();
    }

    /**
     * {@link #getAllProgress()}와 같은 내용이지만, 배정 실패 등으로 목록에서
     * 빠진 매칭도 {@code excluded}로 함께 돌려준다. {@code ?includeExcluded=true}로
     * 요청했을 때만 이 메서드를 쓴다 — 구 FE는 여전히 배열만 기대한다.
     *
     * @see #getAllProgress()
     */
    @Transactional(readOnly = true)
    public AdminMissionListResponse getAllProgressWithExclusions() {
        return collectAllProgress();
    }

    /**
     * 전체 매칭을 훑어 정상 배정된 것은 {@code items}에, 배정 중 예외가 난 것은
     * {@code excluded}에 담는다.
     *
     * <p>전제조건: 호출자가 관리자임은 컨트롤러 계층에서 이미 검증됐다고
     * 전제한다 — 이 메서드 자체는 권한을 확인하지 않는다.</p>
     *
     * <p>부작용: {@link #getProgressAndAssignIfMissing}과 마찬가지로 매칭마다 미션 배정이
     * 없으면 이 호출 중에 배정한다. 한 가지 다른 점: 배정 중 어떤 매칭에서
     * 예외가 나도 전체 목록이 실패하지 않는다 — 그 매칭만 {@code excluded}로
     * 빠지고 에러 로그를 남긴다. 관리자 화면이 매칭 하나 때문에 통째로 안 뜨는
     * 것보다, 문제 있는 매칭 하나 빠지는 게 낫다고 판단한 것.</p>
     *
     * <p>예외 없음(내부에서 전부 흡수한다).</p>
     */
    private AdminMissionListResponse collectAllProgress() {
        List<AdminMissionProgressResponse> items = new ArrayList<>();
        List<AdminMissionExclusionResponse> excluded = new ArrayList<>();
        for (MatchMission mission : matchMissionRepository.findAll(Sort.by(Sort.Direction.DESC, "matchedAt"))) {
            try {
                items.add(AdminMissionProgressResponse.from(
                        mission,
                        assignmentService.assignIfAbsent(mission.getMatchId())
                ));
            } catch (RuntimeException exception) {
                log.error("관리자 미션 목록에서 매칭을 제외합니다. matchId={}", mission.getMatchId(), exception);
                excluded.add(new AdminMissionExclusionResponse(mission.getMatchId(), exclusionReason(exception)));
            }
        }
        return new AdminMissionListResponse(items, excluded);
    }

    /**
     * {@link MissionTemplateNotFoundException}만 {@code NO_TEMPLATE}으로 분류한다 —
     * 배정할 묶음·템플릿을 못 찾은 경우로 좁혀서 판단한다. 락·트랜잭션 상태 오류 같은
     * 다른 종류의 {@code IllegalStateException}까지 템플릿 누락으로 잘못 안내하지
     * 않기 위해 상위 타입이 아니라 이 전용 예외 타입만 본다. 그 밖의 예상 외 예외는
     * 전부 {@code UNKNOWN}이다.
     */
    private static String exclusionReason(RuntimeException exception) {
        return exception instanceof MissionTemplateNotFoundException ? "NO_TEMPLATE" : "UNKNOWN";
    }

    /**
     * adminId(관리자)가 matchId의 STEP {@code expectedStep}을 완료 처리하고 다음
     * STEP으로 넘긴다. {@code expectedStep}은 관리자가 화면에서 확인한 값을 그대로
     * 받는다 — 요청 시점의 서버 현재 STEP으로 대체하지 않는다.
     *
     * <p>전제조건: matchId 존재.</p>
     *
     * <p>부작용: {@code MatchMission} 행을 잠근 뒤(동시 완료 처리 방지)
     * {@code expectedStep}이 서버의 현재 STEP과 같은지 확인하고, 같으면
     * currentStep을 1 올리고 완료 시각·처리자를 기록한다. 트랜잭션
     * 커밋 후 {@code MissionProgressCommittedEvent}를 발행해서 Redis
     * pub/sub → WebSocket으로 참가자 화면에 실시간 반영된다(비동기 —
     * 이 실시간 알림이 실패해도 완료 처리 자체는 이미 성공한 상태다,
     * {@link com.facecook.mission.event.MissionProgressCommittedListener}
     * 참고). {@code expectedStep}이 다르면(이미 처리됨·전체 완료 포함)
     * 아무것도 기록하지 않고 예외를 던진다.</p>
     *
     * <p>예외: {@code NOT_FOUND}(매칭 없음), {@code MISSION_STEP_MISMATCH}
     * (확인한 STEP이 서버의 현재 STEP과 다름 — 이미 처리된 경우 포함).</p>
     *
     * @see #getProgressAndAssignIfMissing(Long, Long)
     * @see com.facecook.mission.event.MissionProgressCommittedListener
     */
    @Transactional
    public AdminMissionProgressResponse completeCurrentStep(Long matchId, Long adminId, int expectedStep) {
        MatchMission mission = matchMissionRepository.findByIdForUpdate(matchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "매칭을 찾을 수 없습니다."));
        List<MatchMissionAssignment> assignments = assignmentService.assignIfAbsent(mission);
        mission.completeCurrentStep(adminId, LocalDateTime.now(clock), expectedStep);
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

}
