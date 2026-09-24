package com.facecook.mission.dto;

import com.facecook.mission.entity.MatchMission;
import com.facecook.mission.entity.MatchMissionAssignment;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 참가자 화면의 미션 진행: 현재 STEP과 그 STEP의 미션 문구({@code currentMission}), STEP별 완료 시각.
 * 다음 STEP 문구는 미리 보여 주지 않는다. {@code currentStep}이 4면 모두 끝난 것이다({@code MatchMission.COMPLETED_STEP}).
 *
 * <p>REST 조회 응답이자, 관리자가 완료 처리했을 때 WebSocket({@code /topic/mission/{matchId}})으로 보내는 모양이다.</p>
 */
public record MissionProgressResponse(
        Long matchId,
        int currentStep,
        String currentMission,
        LocalDateTime step1CompletedAt,
        LocalDateTime step2CompletedAt,
        LocalDateTime step3CompletedAt
) {
    public static MissionProgressResponse from(
            MatchMission mission,
            List<MatchMissionAssignment> assignments
    ) {
        String currentMission = assignments.stream()
                .filter(assignment -> assignment.getStep() == mission.getCurrentStep())
                .map(assignment -> assignment.getTemplate().getContent())
                .findFirst()
                .orElse(null);
        return new MissionProgressResponse(mission.getMatchId(), mission.getCurrentStep(), currentMission,
                mission.getStep1CompletedAt(), mission.getStep2CompletedAt(), mission.getStep3CompletedAt());
    }
}
