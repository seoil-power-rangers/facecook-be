package com.facecook.mission.dto;

import com.facecook.mission.entity.MatchMission;

import java.time.LocalDateTime;

public record AdminMissionProgressResponse(
        Long matchId, Long userAId, Long userBId, LocalDateTime matchedAt, int currentStep,
        LocalDateTime step1CompletedAt, Long step1CompletedBy,
        LocalDateTime step2CompletedAt, Long step2CompletedBy,
        LocalDateTime step3CompletedAt, Long step3CompletedBy
) {
    public static AdminMissionProgressResponse from(MatchMission mission) {
        return new AdminMissionProgressResponse(mission.getMatchId(), mission.getUserAId(), mission.getUserBId(),
                mission.getMatchedAt(), mission.getCurrentStep(), mission.getStep1CompletedAt(),
                mission.getStep1CompletedBy(), mission.getStep2CompletedAt(), mission.getStep2CompletedBy(),
                mission.getStep3CompletedAt(), mission.getStep3CompletedBy());
    }
}
