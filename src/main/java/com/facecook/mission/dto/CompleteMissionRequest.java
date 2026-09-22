package com.facecook.mission.dto;

import com.facecook.mission.entity.MatchMission;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 관리자가 확인 시트에서 본 STEP을 그대로 담아 보내는 완료 요청. 서버는 이 값과 매칭의 현재
 * STEP이 같을 때만 완료 처리한다({@link MatchMission#completeCurrentStep}).
 */
public record CompleteMissionRequest(
        @NotNull(message = "expectedStep은 필수입니다.")
        @Min(value = MatchMission.FIRST_STEP, message = "expectedStep은 1 이상이어야 합니다.")
        @Max(value = MatchMission.LAST_STEP, message = "expectedStep은 3 이하이어야 합니다.")
        Integer expectedStep
) {
}
