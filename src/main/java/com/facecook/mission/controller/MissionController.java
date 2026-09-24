package com.facecook.mission.controller;

import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.CurrentUser;
import com.facecook.mission.dto.MissionProgressResponse;
import com.facecook.mission.service.MissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 참가자용 미션 조회 API. {@code GET /api/matches/{matchId}/mission} →
 * {@link MissionService#getProgressAndAssignIfMissing}. 매칭 당사자만 볼 수 있고, 아직 미션이 배정되지
 * 않은 매칭이면 이 조회에서 배정까지 한다. 참가자가 "완료"를 누르는 API는 없다(관리자가 처리).
 *
 * <p>진행이 바뀌면(관리자 완료 처리) 참가자 화면은 WebSocket {@code /topic/mission/{matchId}}로 새 상태를 받는다.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/matches/{matchId}/mission")
public class MissionController {
    private final MissionService missionService;

    @GetMapping
    public ResponseEntity<MissionProgressResponse> getProgress(
            @PathVariable Long matchId,
            @CurrentUser AuthenticatedUser currentUser
    ) {
        return ResponseEntity.ok(missionService.getProgressAndAssignIfMissing(matchId, currentUser.userId()));
    }
}
