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
    private static final int COMPLETED_STEP = 4;

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

    public void completeCurrentStep(Long adminId, LocalDateTime completedAt) {
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
            case COMPLETED_STEP -> throw new ApiException(ErrorCode.VALIDATION, "이미 모든 STEP을 완료했습니다.");
            default -> throw new IllegalStateException("지원하지 않는 미션 STEP입니다: " + currentStep);
        }
        currentStep++;
    }
}
