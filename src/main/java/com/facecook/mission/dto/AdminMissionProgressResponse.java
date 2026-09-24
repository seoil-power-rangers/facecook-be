package com.facecook.mission.dto;

import com.facecook.mission.entity.MatchMission;
import com.facecook.mission.entity.MatchMissionAssignment;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 화면의 매칭 한 줄: 두 사람, 현재 STEP, STEP별 미션 문구, STEP별 완료 시각·처리한 관리자.
 * 미션 문구는 배정 목록({@code MatchMissionAssignment})에서 STEP으로 찾아 채운다.
 */
public record AdminMissionProgressResponse(
        Long matchId, Long userAId, Long userBId, LocalDateTime matchedAt, int currentStep,
        String step1Mission, String step2Mission, String step3Mission,
        LocalDateTime step1CompletedAt, Long step1CompletedBy,
        LocalDateTime step2CompletedAt, Long step2CompletedBy,
        LocalDateTime step3CompletedAt, Long step3CompletedBy
) {
    public static AdminMissionProgressResponse from(
            MatchMission mission,
            List<MatchMissionAssignment> assignments
    ) {
        return new AdminMissionProgressResponse(mission.getMatchId(), mission.getUserAId(), mission.getUserBId(),
                mission.getMatchedAt(), mission.getCurrentStep(), content(assignments, 1),
                content(assignments, 2), content(assignments, 3), mission.getStep1CompletedAt(),
                mission.getStep1CompletedBy(), mission.getStep2CompletedAt(), mission.getStep2CompletedBy(),
                mission.getStep3CompletedAt(), mission.getStep3CompletedBy());
    }

    private static String content(List<MatchMissionAssignment> assignments, int step) {
        return assignments.stream()
                .filter(assignment -> assignment.getStep() == step)
                .map(assignment -> assignment.getTemplate().getContent())
                .findFirst()
                .orElse(null);
    }
}
