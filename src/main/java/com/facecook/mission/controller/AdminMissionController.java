package com.facecook.mission.controller;

import com.facecook.common.session.AdminAuthorization;
import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.CurrentUser;
import com.facecook.mission.dto.AdminMissionProgressResponse;
import com.facecook.mission.dto.CompleteMissionRequest;
import com.facecook.mission.service.MissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자(부스 운영진)용 미션 API. 모든 메서드가 첫 줄에서 {@code AdminAuthorization.requireAdmin}으로 역할을 확인한다.
 *
 * <table>
 * <tr><th>API</th><th>서비스</th></tr>
 * <tr><td>GET /api/admin/missions[?includeExcluded=true]</td><td>{@link MissionService#getAllProgress} / {@link MissionService#getAllProgressWithExclusions}</td></tr>
 * <tr><td>POST /api/admin/missions/{matchId}/complete {expectedStep}</td><td>{@link MissionService#completeCurrentStep}</td></tr>
 * </table>
 *
 * <p>{@code ResponseEntity<?>}: 파라미터에 따라 응답 모양(배열 / 객체)이 달라서 제네릭 타입을 열어 뒀다.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/missions")
public class AdminMissionController {
    private final MissionService missionService;

    /**
     * {@code includeExcluded=true}로 요청하면 {@code {items, excluded}} 객체를,
     * 아니면(파라미터 없음 포함) 구 FE와 호환되는 배열을 그대로 돌려준다.
     */
    @GetMapping
    public ResponseEntity<?> getAllProgress(
            @RequestParam(name = "includeExcluded", required = false, defaultValue = "false")
            boolean includeExcluded,
            @CurrentUser AuthenticatedUser currentUser
    ) {
        AdminAuthorization.requireAdmin(currentUser);
        if (includeExcluded) {
            return ResponseEntity.ok(missionService.getAllProgressWithExclusions());
        }
        return ResponseEntity.ok(missionService.getAllProgress());
    }

    @PostMapping("/{matchId}/complete")
    public ResponseEntity<AdminMissionProgressResponse> completeCurrentStep(
            @PathVariable Long matchId,
            @Valid @RequestBody CompleteMissionRequest request,
            @CurrentUser AuthenticatedUser currentUser
    ) {
        AdminAuthorization.requireAdmin(currentUser);
        return ResponseEntity.ok(
                missionService.completeCurrentStep(matchId, currentUser.userId(), request.expectedStep())
        );
    }
}
