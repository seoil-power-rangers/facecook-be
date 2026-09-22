package com.facecook.mission.entity;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "match_info")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchMission {
    /** 미션 배정·완료가 다루는 첫 STEP. */
    public static final int FIRST_STEP = 1;
    /** currentStep이 이 값이면 완료 처리 대상 STEP이 없다(모든 STEP을 마쳤다). */
    public static final int COMPLETED_STEP = 4;
    /** 배정·완료가 다루는 마지막 STEP. {@link #COMPLETED_STEP}의 정의를 그대로 따른다 — 3단계를
     * 늘리려면 이 관계 하나만 알면 된다. */
    public static final int LAST_STEP = COMPLETED_STEP - 1;

    @Id
    @Column(name = "match_id")
    private Long matchId;

    @Column(name = "user_a_id", nullable = false)
    private Long userAId;

    @Column(name = "user_b_id", nullable = false)
    private Long userBId;

    @Column(name = "matched_at", nullable = false)
    private LocalDateTime matchedAt;

    @Column(name = "current_step", nullable = false)
    private int currentStep;

    @Column(name = "step1_completed_at")
    private LocalDateTime step1CompletedAt;

    @Column(name = "step1_completed_by")
    private Long step1CompletedBy;

    @Column(name = "step2_completed_at")
    private LocalDateTime step2CompletedAt;

    @Column(name = "step2_completed_by")
    private Long step2CompletedBy;

    @Column(name = "step3_completed_at")
    private LocalDateTime step3CompletedAt;

    @Column(name = "step3_completed_by")
    private Long step3CompletedBy;

    public boolean includes(Long userId) {
        return userAId.equals(userId) || userBId.equals(userId);
    }

    /**
     * 관리자가 화면에서 확인한 STEP({@code expectedStep})을 완료 처리한다.
     *
     * <p>전제조건: 호출자가 이 행을 이미 잠갔다(동시 완료 처리 방지).</p>
     *
     * <p>예외: {@code expectedStep}이 서버의 현재 {@code currentStep}과 다르면
     * {@link ErrorCode#MISSION_STEP_MISMATCH}(409)를 던진다 — 이미 완료 처리된
     * STEP을 다시 요청한 경우(다른 관리자가 먼저 처리했거나 재시도로 중복
     * 요청된 경우)도, 아직 배정 전인 매칭을 완료 요청한 경우도 이 하나로
     * 응답한다. 누가 먼저 처리했는지는 구별하지 않는다.</p>
     */
    public void completeCurrentStep(Long adminId, LocalDateTime completedAt, int expectedStep) {
        if (currentStep != expectedStep) {
            throw new ApiException(ErrorCode.MISSION_STEP_MISMATCH);
        }
        switch (currentStep) {
            case 1 -> {
                step1CompletedAt = completedAt;
                step1CompletedBy = adminId;
            }
            case 2 -> {
                step2CompletedAt = completedAt;
                step2CompletedBy = adminId;
            }
            case 3 -> {
                step3CompletedAt = completedAt;
                step3CompletedBy = adminId;
            }
            default -> throw new IllegalStateException("지원하지 않는 미션 STEP입니다: " + currentStep);
        }
        currentStep++;
    }
}
