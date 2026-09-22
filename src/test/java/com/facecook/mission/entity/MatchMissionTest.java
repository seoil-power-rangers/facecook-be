package com.facecook.mission.entity;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchMissionTest {

    private static final Long ADMIN_ID = 99L;
    private static final LocalDateTime COMPLETED_AT = LocalDateTime.of(2026, 9, 30, 10, 0);

    @Test
    void completesExpectedStepAndAdvancesCurrentStep() {
        MatchMission mission = missionAtStep(1);

        mission.completeCurrentStep(ADMIN_ID, COMPLETED_AT, 1);

        assertThat(mission.getCurrentStep()).isEqualTo(2);
        assertThat(mission.getStep1CompletedAt()).isEqualTo(COMPLETED_AT);
        assertThat(mission.getStep1CompletedBy()).isEqualTo(ADMIN_ID);
    }

    @Test
    void rejectsWhenExpectedStepIsBehindTheCurrentStep() {
        MatchMission mission = missionAtStep(2);

        assertThatThrownBy(() -> mission.completeCurrentStep(ADMIN_ID, COMPLETED_AT, 1))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MISSION_STEP_MISMATCH));
        assertThat(mission.getCurrentStep()).isEqualTo(2);
        assertThat(mission.getStep1CompletedAt()).isNull();
    }

    @Test
    void rejectsWhenAllStepsAreAlreadyCompleted() {
        MatchMission mission = missionAtStep(MatchMission.COMPLETED_STEP);

        assertThatThrownBy(() -> mission.completeCurrentStep(ADMIN_ID, COMPLETED_AT, MatchMission.LAST_STEP))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MISSION_STEP_MISMATCH));
    }

    @Test
    void secondCompletionOfTheSameStepIsRejectedNotTreatedAsSuccess() {
        MatchMission mission = missionAtStep(1);
        mission.completeCurrentStep(ADMIN_ID, COMPLETED_AT, 1);

        assertThatThrownBy(() -> mission.completeCurrentStep(ADMIN_ID, COMPLETED_AT, 1))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MISSION_STEP_MISMATCH));
    }

    private static MatchMission missionAtStep(int currentStep) {
        MatchMission mission = new MatchMission();
        ReflectionTestUtils.setField(mission, "matchId", 1L);
        ReflectionTestUtils.setField(mission, "userAId", 10L);
        ReflectionTestUtils.setField(mission, "userBId", 20L);
        ReflectionTestUtils.setField(mission, "matchedAt", LocalDateTime.of(2026, 9, 1, 0, 0));
        ReflectionTestUtils.setField(mission, "currentStep", currentStep);
        return mission;
    }
}
