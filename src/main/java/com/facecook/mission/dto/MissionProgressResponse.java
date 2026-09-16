package com.facecook.mission.dto;

import com.facecook.mission.entity.MatchMission;
import com.facecook.mission.entity.MatchMissionAssignment;

import java.time.LocalDateTime;
import java.util.List;

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
