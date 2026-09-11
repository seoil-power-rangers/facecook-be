package com.facecook.mission.dto;

import com.facecook.mission.entity.MatchMission;

import java.time.LocalDateTime;

public record MissionProgressResponse(
        Long matchId,
        int currentStep,
        LocalDateTime step1CompletedAt,
        LocalDateTime step2CompletedAt,
        LocalDateTime step3CompletedAt
) {
    public static MissionProgressResponse from(MatchMission mission) {
        return new MissionProgressResponse(mission.getMatchId(), mission.getCurrentStep(),
                mission.getStep1CompletedAt(), mission.getStep2CompletedAt(), mission.getStep3CompletedAt());
    }
}
